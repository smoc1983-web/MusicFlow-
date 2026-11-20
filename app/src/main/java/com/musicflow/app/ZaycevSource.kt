package com.musicflow.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val ZAYCEV_API = "https://zaycev.net/api/external/"
private const val ZAYCEV_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"

/**
 * Public first-party web endpoints verified in 2026-11-20:
 * pages/search/tracks -> trackIds + tracksInfo
 * POST track/filezmeta -> anonymous streaming hash for a track
 * GET track/play/{hash} -> short-lived native MP3 URL (dl.zaycev.net)
 * The latter supports HTTP 206 audio/mpeg. No browser or account required.
 */
internal suspend fun zaycevTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
    val term = query.trim()
    if (term.isBlank()) return@withContext emptyList()
    val address = ZAYCEV_API + "pages/search/tracks?q=" +
        URLEncoder.encode(term, "UTF-8") + "&page=1&limit=40"
    val root = zaycevRequest(address)
    // Zaycev shows Top-100 when a query has no matches. Never present those
    // unrelated popular songs as search results for the user's query.
    if (root.optBoolean("popularInSearch", false)) {
        AppDiagnostics.info("Search", "Zaycev search has no matching songs")
        return@withContext emptyList()
    }
    val trackIds = root.optJSONArray("trackIds") ?: return@withContext emptyList()
    val info = root.optJSONObject("tracksInfo") ?: return@withContext emptyList()
    val results = ArrayList<Track>(minOf(40, trackIds.length()))
    for (index in 0 until minOf(40, trackIds.length())) {
        val id = trackIds.optLong(index, -1L)
        if (id <= 0L) continue
        val metadata = info.optJSONObject(id.toString()) ?: continue
        if (!metadata.optBoolean("playbackEnabled", false) || metadata.optBoolean("isPreview", false)) continue
        val title = metadata.optString("track").trim()
        val artist = metadata.optString("artistName").trim()
        if (title.isBlank() || artist.isBlank()) continue
        val cover = metadata.optString("imageJpg")
            .ifBlank { metadata.optString("imageWebp") }
            .takeIf { it.startsWith("https://") }
        val duration = metadata.optString("duration")
            .split(':').mapNotNull { it.toLongOrNull() }
            .takeIf { it.size in 2..3 }
            ?.fold(0L) { accumulated, part -> accumulated * 60 + part } ?: 0L
        results += Track(
            id = "zaycev:$id",
            title = title.take(180),
            artist = artist.take(180),
            artwork = cover,
            stream = "zaycev://track/$id",
            durationSec = duration
        )
    }
    results
}

/** Called by Media3's ResolvingDataSource on its loader thread, never the main thread. */
internal object ZaycevStreamResolver {
    private data class Entry(val uri: Uri, val validUntil: Long)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val trackIdRegex = Regex("[0-9]{1,16}")
    private val hashRegex = Regex("[a-zA-Z0-9_-]{8,128}")

    fun invalidate(pseudoUri: Uri) {
        pseudoUri.lastPathSegment?.let { cache.remove(it) }
    }

    fun resolve(pseudoUri: Uri): Uri {
        require(pseudoUri.scheme == "zaycev" &&
            pseudoUri.host == "track" &&
            pseudoUri.pathSegments.size == 1
        ) { "Неверная ссылка Zaycev в очереди" }
        val id = pseudoUri.lastPathSegment.orEmpty()
        require(trackIdRegex.matches(id)) { "Неверный ID песни Zaycev" }
        val now = System.currentTimeMillis()
        cache[id]?.let {
            if (now < it.validUntil) return it.uri
        }
        val request = JSONObject().put("trackIds", org.json.JSONArray().put(id.toLong()))
            .put("subscription", false)
        val meta = zaycevRequest(ZAYCEV_API + "track/filezmeta", request)
        val tracks = meta.optJSONArray("tracks") ?: throw IOException("Сайт не вернул данные потока")
        val hash = (0 until tracks.length())
            .mapNotNull { tracks.optJSONObject(it) }
            .firstOrNull { it.optLong("id", -1) == id.toLong() }
            ?.optString("streaming").orEmpty()
        if (!hashRegex.matches(hash)) throw IOException("Zaycev не предоставил ссылку воспроизведения")
        val response = zaycevRequest(
            ZAYCEV_API + "track/play/" + URLEncoder.encode(hash, "UTF-8")
        )
        val rawUrl = response.optString("url")
        val media = Uri.parse(rawUrl)
        val host = media.host?.lowercase(Locale.ROOT).orEmpty()
        if (!media.scheme.equals("https", true) ||
            (host != "zaycev.net" && !host.endsWith(".zaycev.net"))) {
            throw IOException("Zaycev вернул неподдерживаемый аудиосервер")
        }
        // Signed links expire. Only cache briefly and resolve anew for later playback.
        if (cache.size >= 256) cache.clear()
        cache[id] = Entry(media, System.currentTimeMillis() + 20_000L)
        AppDiagnostics.info("Zaycev", "native playback URL resolved")
        return media
    }
}

private fun zaycevRequest(url: String, body: JSONObject? = null): JSONObject {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 9_000
        readTimeout = 11_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", ZAYCEV_AGENT)
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
        setRequestProperty("Referer", "https://zaycev.net/")
        if (body != null) {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Origin", "https://zaycev.net")
        }
    }
    return try {
        if (body != null) {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("Zaycev HTTP $status")
        val builder = StringBuilder()
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(4096)
            while (builder.length < 450_000) {
                val count = reader.read(chars, 0, minOf(chars.size, 450_000 - builder.length))
                if (count < 0) break
                if (count > 0) builder.append(chars, 0, count)
            }
        }
        val result = JSONObject(builder.toString())
        if (result.has("error"))
            throw IOException("Zaycev: ответ API содержит ошибку")
        result
    } finally {
        connection.disconnect()
    }
}
