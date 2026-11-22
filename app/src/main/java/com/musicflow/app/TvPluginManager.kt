package com.musicflow.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * MusicFlow TV plugin v1 is a declarative JSON manifest, NOT an executable plugin.
 * No JavaScript/Dex execution, filesystem privileges, arbitrary intents or WebViews.
 * Users explicitly opt in to third-party services, which supply their own media.
 */
internal data class TvPluginManifest(
    val name: String,
    val playlistUrl: String,
    val epgUrl: String?,
    val homepage: String?
)

internal object TvPluginManager {
    const val FORMAT = "musicflow.tv-plugin.v1"
    private const val MAX_MANIFEST_BYTES = 32_000
    private const val MAX_EPG_BYTES = 3_000_000

    /**
     * Network fetches should never target local/private addresses, embedded
     * credentials, or non-HTTPS endpoints. Redirects are rejected.
     */
    fun isSafePluginUrl(value: String): Boolean {
        if (value.isBlank() || value.length > 2048 || value.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) || uri.userInfo != null ||
            uri.fragment != null || uri.rawQuery?.length.orZero() > 1000) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".test") || host.endsWith(".example") ||
            host.endsWith(".invalid") || host.startsWith("127.") ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("169.254.") || host.startsWith("0.") ||
            host == "::1" || host.contains(":") || host.all { it.isDigit() || it == '.' }) return false
        if (host.startsWith("172.")) {
            val octet = host.split('.').getOrNull(1)?.toIntOrNull()
            if (octet in 16..31) return false
        }
        return uri.port == -1 || uri.port == 443
    }

    private fun Int?.orZero(): Int = this ?: 0

    fun parseManifest(raw: String): TvPluginManifest {
        val json = JSONObject(raw)
        require(json.optString("format") == FORMAT) {
            "Неподдерживаемый формат плагина. Требуется $FORMAT"
        }
        require(!json.has("script") && !json.has("code") && !json.has("executable")) {
            "Программный код в ТВ-плагинах запрещён"
        }
        val name = json.optString("name").trim().take(70)
        val playlist = json.optString("playlist_url").trim()
        val epg = json.optString("epg_url").takeIf { it.isNotBlank() && it != "null" }
        val homepage = json.optString("homepage").takeIf { it.isNotBlank() && it != "null" }
        require(name.isNotBlank()) { "Плагин должен содержать название" }
        require(isSafePluginUrl(playlist)) { "Для плейлиста требуется публичный HTTPS-адрес" }
        require(epg == null || isSafePluginUrl(epg)) { "Небезопасная ссылка телепрограммы" }
        require(homepage == null || isSafePluginUrl(homepage)) { "Небезопасная ссылка сайта" }
        return TvPluginManifest(name, playlist, epg, homepage)
    }

    suspend fun load(url: String): TvPluginManifest = withContext(Dispatchers.IO) {
        require(isSafePluginUrl(url)) { "Плагин должен загружаться с публичного HTTPS-адреса" }
        parseManifest(String(fetchBounded(url, MAX_MANIFEST_BYTES), StandardCharsets.UTF_8))
    }

    suspend fun loadEpg(url: String): List<TvProgramme> = withContext(Dispatchers.IO) {
        require(isSafePluginUrl(url)) { "Неверная ссылка EPG" }
        val payload = fetchBounded(url, MAX_EPG_BYTES)
        TvEpgParser.parse(payload)
    }

    private fun fetchBounded(url: String, limit: Int): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "MusicFlow/0.7.40 Android")
            setRequestProperty("Accept", "application/json,application/xml,text/xml,text/plain")
        }
        try {
            if (connection.responseCode != 200) {
                throw IOException("Источник вернул HTTP " + connection.responseCode)
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        if (output.size() + count > limit) {
                            throw IOException("Ответ сервера превышает допустимый размер")
                        }
                        output.write(buffer, 0, count)
                    }
                }
                return output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
