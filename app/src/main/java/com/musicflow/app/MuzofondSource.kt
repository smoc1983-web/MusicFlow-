package com.musicflow.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private const val MUZOFOND_HOST = "muzofond.fm"
private const val MUZOFOND_ROOT = "https://muzofond.fm"
private const val MUZOFOND_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

internal suspend fun muzofondTracks(query: String): List<Track> =
    withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val segment = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val destinations = listOf(
            "$MUZOFOND_ROOT/search/$segment",
            "$MUZOFOND_ROOT/music/$segment"
        )
        var lastError: Exception? = null
        for (destination in destinations) {
            try {
                val connection = URL(destination).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 14_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", MUZOFOND_UA)
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
                val html: String
                val finalUrl: String
                val responseCode: Int
                try {
                    responseCode = connection.responseCode
                    if (responseCode == 404) continue
                    if (responseCode == 403 || responseCode == 451) {
                        throw IOException("HTTP $responseCode: доступ ограничен для текущего соединения. Проверь VPN или регион.")
                    }
                    if (responseCode !in 200..299) {
                        throw IOException("Сервер ответил: HTTP $responseCode")
                    }
                    finalUrl = connection.url.toExternalForm()
                    // Parse only a bounded HTML response, never save songs to disk.
                    html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(8192)
                        val output = StringBuilder()
                        while (output.length < 2_000_000) {
                            val count = reader.read(buffer, 0, minOf(buffer.size, 2_000_000 - output.length))
                            if (count < 0) break
                            if (count == 0) continue
                            output.append(buffer, 0, count)
                        }
                        output.toString()
                    }
                } finally {
                    connection.disconnect()
                }
                val tracks = parseMuzofondHtml(html, finalUrl)
                if (tracks.isNotEmpty()) return@withContext tracks
                if (html.contains("li class=\"item\"") || html.contains("class=\"trackTitle\"")) {
                    throw IOException("Формат каталога изменился, не найдены ссылки на аудио.")
                }
                // Some installations route /search/ -> /music/ automatically;
                // retry the canonical /music/ path if no results were parsed.
            } catch (e: Exception) {
                lastError = e
                if (e.message?.contains("HTTP 403") == true ||
                    e.message?.contains("HTTP 451") == true) break
            }
        }
        if (lastError != null) throw lastError
        emptyList()
    }

internal fun parseMuzofondHtml(html: String, baseUrl: String): List<Track> {
    val document = Jsoup.parse(html, baseUrl)
    val result = mutableListOf<Track>()
    for (card in document.select("li.item")) {
        val button = card.selectFirst("li.play[data-url], .play[data-url]") ?: continue
        val artist = card.selectFirst(".trackTitle .artist")?.text()?.trim()
        val title = card.selectFirst(".trackTitle .track")?.text()?.trim()
        if (artist.isNullOrBlank() || title.isNullOrBlank()) continue
        val rawUrl = button.attr("data-url").trim()
        val link = runCatching { URL(URL(baseUrl), rawUrl) }.getOrNull() ?: continue
        if (link.protocol.lowercase(Locale.ROOT) != "https") continue
        val host = link.host.lowercase(Locale.ROOT)
        if (host != MUZOFOND_HOST && !host.endsWith(".$MUZOFOND_HOST")) continue
        val trackId = card.attr("data-id").ifBlank {
            button.attr("data-id").ifBlank { link.toExternalForm().hashCode().toString() }
        }
        result += Track(
            id = "muzofond:$trackId",
            title = title.take(160),
            artist = artist.take(160),
            artwork = null,
            stream = link.toExternalForm(),
            durationSec = 0L
        )
        if (result.size >= 100) break
    }
    return result.distinctBy { it.id }
}
