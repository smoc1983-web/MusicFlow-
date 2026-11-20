package com.musicflow.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

internal data class MusicStream(val id: String, val uri: String) {
    val source: String get() = id.substringBefore(':')
}

internal const val MUSIC_STREAMS = "com.musicflow.music_streams"
internal const val MUSIC_SOURCE = "com.musicflow.music_source"
internal const val MUSIC_DURATION_SECONDS = "com.musicflow.music_duration_seconds"

internal fun encodeStreams(streams: List<MusicStream>): String = JSONArray().apply {
    streams.forEach { put(JSONObject().put("id", it.id).put("uri", it.uri)) }
}.toString()

internal fun decodeStreams(encoded: String?): List<MusicStream> = runCatching {
    val array = JSONArray(encoded ?: "[]")
    (0 until minOf(array.length(), 12)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val stream = MusicStream(item.optString("id"), item.optString("uri"))
        stream.takeIf {
            val uri = android.net.Uri.parse(it.uri)
            val host = uri.host.orEmpty().lowercase(Locale.ROOT)
            when (it.source) {
                "zaycev" -> Regex("zaycev://track/[0-9]{1,16}").matches(it.uri)
                "muzofond" -> uri.scheme == "https" &&
                    (host == "muzofond.fm" || host.endsWith(".muzofond.fm"))
                "mailru" -> uri.scheme == "https" &&
                    (host == "my.mail.ru" || host.endsWith(".my.mail.ru"))
                else -> false
            }
        }
    }
}.getOrDefault(emptyList())

internal fun Track.streams(): List<MusicStream> = listOf(MusicStream(id, stream)) + alternatives

private val wordBreak = Regex("[^\\p{L}\\p{N}]+")
internal fun musicKey(value: String): String =
    value.lowercase(Locale.ROOT).replace('ё', 'е').replace(wordBreak, " ").trim()

internal fun recordingMatches(track: Track, artist: String, title: String, durationSec: Long = 0): Boolean =
    musicKey(artist).let { key ->
        key.isNotEmpty() && key != "неизвестный исполнитель" && key == musicKey(track.artist)
    } && musicKey(title).let { key -> key.isNotEmpty() && key == musicKey(track.title) } &&
        durationMatches(track.durationSec, durationSec)

private fun durationMatches(left: Long, right: Long): Boolean =
    left <= 0 || right <= 0 || abs(left - right) <= 8

internal fun relevantMusic(track: Track, query: String): Boolean {
    val words = musicKey(query).split(' ').filter { it.isNotEmpty() }
    val fields = musicKey(track.artist + " " + track.title + " " + track.collectionTitle.orEmpty())
        .split(' ')
    return words.isNotEmpty() && words.all { word ->
        fields.any { it == word || (word.length >= 3 && it.startsWith(word)) }
    }
}

private val providers = mapOf<String, suspend (String) -> List<Track>>(
    "zaycev" to ::zaycevTracks,
    "muzofond" to ::muzofondTracks,
    "mailru" to ::mailRuTracks
)

internal suspend fun searchMusic(
    query: String,
    alternateQuery: String? = null,
    accept: (Track) -> Boolean = { true },
    onResult: (String, List<Track>?, Throwable?) -> Unit
) = coroutineScope {
    providers.forEach { (source, search) ->
        launch {
            val tracks = try {
                val initial = search(query).filter { relevantMusic(it, query) && accept(it) }
                if (initial.isEmpty() && !alternateQuery.isNullOrBlank() && alternateQuery != query) {
                    search(alternateQuery).filter { relevantMusic(it, alternateQuery) && accept(it) }
                } else initial
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                onResult(source, null, error)
                return@launch
            }
            onResult(source, tracks, null)
        }
    }
}

internal suspend fun findRecording(
    artist: String,
    title: String,
    durationSec: Long = 0
): List<Track> {
    var matches = emptyList<Track>()
    withTimeoutOrNull(24_000L) {
        searchMusic(
            "$artist $title",
            alternateQuery = title,
            accept = { recordingMatches(it, artist, title, durationSec) }
        ) { source, tracks, error ->
            if (error != null) AppDiagnostics.failure("Search", "recording lookup source=$source", error)
            matches += tracks.orEmpty()
        }
    }
    return matches
}
