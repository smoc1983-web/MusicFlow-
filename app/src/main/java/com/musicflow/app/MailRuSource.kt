package com.musicflow.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private const val MAILRU_API = "https://my.mail.ru/cgi-bin/my/ajax"
private const val MAILRU_WEB = "https://my.mail.ru/music/search/"
private const val MAILRU_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"

private fun encodedMailParam(value: String): String = URLEncoder.encode(value, "UTF-8")

/**
 * Searches the public My World music catalog without a login or WebView.
 *
 * Verified 2026-11-20 against Mail.ru's music.search AJAX response:
 * MusicData contains File, URL, Author, Name and DurationInSeconds.
 * The MP3 endpoint moosic.my.mail.ru supports HTTP 206 byte-range playback.
 */
internal suspend fun mailRuTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
    val requested = query.trim()
    if (requested.isBlank()) return@withContext emptyList()

    val searchParameters = JSONObject()
        .put("music", JSONObject().put("limit", 50).put("offset", 0))
        .toString()
    val fields = linkedMapOf(
        "xemail" to "",
        "ajax_call" to "1",
        "func_name" to "music.search",
        "mna" to "",
        "mnb" to "",
        "arg_query" to requested,
        "arg_extended" to "1",
        "arg_search_params" to searchParameters,
        "arg_limit" to "50",
        "arg_offset" to "0"
    )
    val queryString = fields.entries.joinToString("&") { (key, value) ->
        encodedMailParam(key) + "=" + encodedMailParam(value)
    }
    val connection = (URL("$MAILRU_API?$queryString").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 16_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", MAILRU_USER_AGENT)
        setRequestProperty("Referer", MAILRU_WEB + encodedMailParam(requested).replace("+", "%20"))
        setRequestProperty("X-Requested-With", "XMLHttpRequest")
        setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
        setRequestProperty("Accept", "application/json, text/plain, */*")
    }
    val text = try {
        when (val status = connection.responseCode) {
            200 -> Unit
            401, 403, 451 -> throw IOException("Музыкальный каталог недоступен (HTTP $status)")
            else -> throw IOException("Mail.ru: HTTP $status")
        }
        // The API returns a small list. Avoid unbounded reads.
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val output = StringBuilder()
            val buffer = CharArray(8192)
            while (output.length < 1_500_000) {
                val count = reader.read(buffer, 0, minOf(buffer.size, 1_500_000 - output.length))
                if (count < 0) break
                if (count > 0) output.append(buffer, 0, count)
            }
            output.toString()
        }
    } finally {
        connection.disconnect()
    }
    parseMailRuSearchJson(text)
}

internal fun parseMailRuSearchJson(content: String): List<Track> {
    val root = try {
        JSONTokener(content.trimStart('\uFEFF', ' ', '\r', '\n')).nextValue()
    } catch (e: Exception) {
        throw IOException("Mail.ru вернул не JSON. Возможно, требуется авторизация.", e)
    }
    val payload = when (root) {
        is JSONArray -> (0 until root.length()).asSequence()
            .mapNotNull { root.optJSONObject(it) }
            .firstOrNull { it.optJSONArray("MusicData") != null }
        is JSONObject -> root.takeIf { it.optJSONArray("MusicData") != null }
        else -> null
    } ?: throw IOException("Формат ответа музыкального поиска Mail.ru изменился.")

    val items = payload.optJSONArray("MusicData") ?: return emptyList()
    val tracks = ArrayList<Track>(minOf(items.length(), 50))
    for (i in 0 until minOf(items.length(), 50)) {
        val item = items.optJSONObject(i) ?: continue
        if (item.optBoolean("IsRestricted", false)) continue
        val id = item.optString("File").trim()
        val stream = item.optString("URL").trim()
        if (id.isBlank() || stream.isBlank()) continue
        val uri = Uri.parse(stream)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            (host != "my.mail.ru" && !host.endsWith(".my.mail.ru"))) {
            // Never pass arbitrary or insecure URLs from a remote JSON payload to Media3.
            continue
        }

        fun field(primary: String, secondary: String): String {
            val value = item.optString(primary).ifBlank { item.optString(secondary) }
            return Jsoup.parse(value).text().trim()
        }
        val title = field("Name", "Name_Text_HTML")
        if (title.isBlank()) continue
        val artist = field("Author", "Author_Text_HTML")
            .ifBlank { "Неизвестный исполнитель" }
        val album = field("Album", "Album_Text_HTML").takeIf { it.isNotBlank() }
        val cover = item.optString("AlbumCoverURL")
            .ifBlank { item.optString("FiledAlbumCover") }
            .takeIf {
                val parsed = Uri.parse(it)
                parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
            }
        val seconds = item.optLong("DurationInSeconds", 0L).takeIf { it > 0 }
            ?: item.optString("Duration").split(':')
                .mapNotNull { it.toLongOrNull() }
                .takeIf { it.size in 2..3 }
                ?.fold(0L) { total, part -> total * 60L + part }
            ?: 0L
        tracks.add(
            Track(
                id = "mailru:" + id.take(100),
                title = title.take(180),
                artist = artist.take(180),
                artwork = cover,
                stream = stream,
                durationSec = seconds,
                collectionTitle = album?.take(180)
            )
        )
    }
    return tracks.distinctBy { it.id }
}
