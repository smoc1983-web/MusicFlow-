package com.musicflow.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal data class AlbumSong(val number: Int, val title: String, val artist: String, val durationMs: Long)
internal data class AlbumListing(
    val title: String, val artist: String, val releaseId: String,
    val tracks: List<AlbumSong>
)

/** Public MusicBrainz recording/release metadata. No audio downloaded from MusicBrainz. */
internal object AlbumCatalog {
    private val mutex = Mutex()
    private var lastRequest = 0L
    private fun normalize(text: String) = text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private suspend fun get(path: String): JSONObject = mutex.withLock {
        val wait = 1200L - (System.currentTimeMillis() - lastRequest)
        if (wait > 0) delay(wait)
        lastRequest = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            val url = "https://musicbrainz.org/ws/2/" + path
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 9000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent",
                    "MusicFlow/0.7.6 (https://github.com/smoc1983-web/MusicFlow-)")
            }
            try {
                if (conn.responseCode != 200) error("MusicBrainz: HTTP " + conn.responseCode)
                val text = conn.inputStream.bufferedReader().use {
                    it.readText().take(650_000)
                }
                JSONObject(text)
            } finally { conn.disconnect() }
        }
    }

    suspend fun lookup(artist: String, trackTitle: String, albumHint: String?): AlbumListing? {
        val candidates = mutableListOf<String>()
        val knownAlbum = albumHint?.trim().orEmpty()
        if (knownAlbum.isNotBlank()) {
            val query = URLEncoder.encode(
                "release:\"" + knownAlbum.take(80) + "\" AND artist:\"" +
                    artist.take(80) + "\"", "UTF-8")
            val releases = get("release/?query=" + query + "&fmt=json&limit=8")
                .optJSONArray("releases") ?: JSONArray()
            for (i in 0 until minOf(releases.length(), 8)) {
                val r = releases.optJSONObject(i) ?: continue
                if (normalize(r.optString("title")) == normalize(knownAlbum)) {
                    r.optString("id").takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                }
            }
        }
        // For tracks with no album metadata, find verified releases for that exact recording.
        if (candidates.isEmpty()) {
            val query = URLEncoder.encode(
                "recording:\"" + trackTitle.take(80) + "\" AND artist:\"" +
                    artist.take(80) + "\"", "UTF-8")
            val recordings = get("recording/?query=" + query + "&fmt=json&limit=5")
                .optJSONArray("recordings") ?: JSONArray()
            for (i in 0 until minOf(recordings.length(), 5)) {
                val rec = recordings.optJSONObject(i) ?: continue
                if (normalize(rec.optString("title")) != normalize(trackTitle)) continue
                val releases = rec.optJSONArray("releases") ?: continue
                for (j in 0 until minOf(releases.length(), 12)) {
                    val rel = releases.optJSONObject(j) ?: continue
                    val group = rel.optJSONObject("release-group")
                    // Prefer albums over singles when the catalog supplies a primary type.
                    val type = group?.optString("primary-type").orEmpty()
                    if (type.isNotBlank() && type != "Album") continue
                    rel.optString("id").takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                }
            }
        }
        val target = normalize(trackTitle)
        for (id in candidates.distinct().take(6)) {
            if (!Regex("[0-9a-f-]{36}").matches(id)) continue
            val release = runCatching {
                get("release/" + id + "?inc=recordings+artist-credits&fmt=json")
            }.getOrNull() ?: continue
            val media = release.optJSONArray("media") ?: continue
            val songs = mutableListOf<AlbumSong>()
            for (disc in 0 until media.length()) {
                val tracks = media.optJSONObject(disc)?.optJSONArray("tracks") ?: continue
                for (j in 0 until tracks.length()) {
                    val item = tracks.optJSONObject(j) ?: continue
                    val name = item.optString("title")
                        .ifBlank { item.optJSONObject("recording")?.optString("title").orEmpty() }
                    if (name.isBlank()) continue
                    val credits = item.optJSONArray("artist-credit")
                    val credited = credits?.optJSONObject(0)?.optString("name")
                        ?.takeIf { it.isNotBlank() } ?: artist
                    songs += AlbumSong(
                        number = songs.size + 1,
                        title = name.take(160),
                        artist = credited.take(160),
                        durationMs = item.optLong("length", 0L).coerceAtLeast(0L)
                    )
                }
            }
            if (songs.size < 2 || songs.none { normalize(it.title) == target }) continue
            val name = release.optString("title")
            if (knownAlbum.isNotBlank() && normalize(name) != normalize(knownAlbum)) continue
            val credit = release.optJSONArray("artist-credit")
                ?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() } ?: artist
            return AlbumListing(name, credit, id, songs)
        }
        return null
    }
}
