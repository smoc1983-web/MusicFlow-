package com.musicflow.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Backstop independent of the Radio Browser API hostnames.
 *
 * The bundled country playlist comes from AlonDrilich/radio-playlists
 * (Radio Browser public-domain station metadata, refreshed upstream weekly).
 * Stream URLs still belong to their respective broadcasters.
 */
internal object RussianRadioFallback {
    private const val FILE_NAME = "radio_russia_fallback.m3u"
    private const val ONLINE_PLAYLIST =
        "https://raw.githubusercontent.com/AlonDrilich/radio-playlists/main/countries/ru.m3u"
    private val stationIdLine =
        Regex("""^# 72FM: https://72fm\.com/station/([0-9a-fA-F-]{36})(?:\?.*)?$""")
    private val logoAttribute = Regex("""tvg-logo="([^"]+)"""")

    /** Pure parser: the predicate is supplied at the network boundary and is testable on JVM. */
    internal fun parseM3u(
        content: String,
        permitted: (String) -> Boolean
    ): List<RussianRadioStation> {
        val result = mutableListOf<RussianRadioStation>()
        var uuid: String? = null
        var name: String? = null
        var logo: String? = null

        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("# 72FM:")) {
                uuid = stationIdLine.matchEntire(line)?.groupValues?.getOrNull(1)
            } else if (line.startsWith("#EXTINF:")) {
                name = line.substringAfter(',', "").trim().take(130).takeIf { it.isNotBlank() }
                logo = logoAttribute.find(line)?.groupValues?.getOrNull(1)
                    ?.takeIf { it.startsWith("https://") }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val stationName = name
                if (stationName != null && line.length <= 2048 && permitted(line)) {
                    val id = uuid ?: UUID.nameUUIDFromBytes(
                        ("radio-backup:$line").toByteArray(StandardCharsets.UTF_8)
                    ).toString()
                    result.add(RussianRadioStation(
                        uuid = id,
                        name = stationName,
                        stream = line,
                        favicon = logo,
                        tags = "Резервный каталог",
                        codec = "",
                        bitrate = 0
                    ))
                }
                uuid = null
                name = null
                logo = null
            }
            if (result.size >= 300) break
        }
        return result.distinctBy { it.uuid }
    }

    fun bundledStations(context: Context): List<RussianRadioStation> = runCatching {
        context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { input ->
            parseM3u(input.readText(), RussianRadioDirectory::isPublicStream)
        }
    }.getOrElse { error ->
        AppDiagnostics.failure("Radio", "read packaged offline catalog", error)
        emptyList()
    }

    /**
     * Retrieve the updated independent playlist when possible; if it cannot be
     * fetched, return the APK-bundled snapshot without requiring DNS or internet.
     */
    suspend fun stations(context: Context, query: String): List<RussianRadioStation> =
        withContext(Dispatchers.IO) {
            val refreshed = try {
                val conn = (URL(ONLINE_PLAYLIST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3_000
                    readTimeout = 5_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "MusicFlow/0.7.39 Android")
                    setRequestProperty("Accept", "audio/x-mpegurl,text/plain,*/*")
                }
                try {
                    if (conn.responseCode != 200) {
                        throw IOException("Backup playlist HTTP " + conn.responseCode)
                    }
                    val data = conn.inputStream.use { it.readNBytes(250_001) }
                    if (data.size > 250_000) throw IOException("Backup playlist exceeds 250 KB")
                    parseM3u(String(data, Charsets.UTF_8), RussianRadioDirectory::isPublicStream)
                        .takeIf { it.isNotEmpty() }
                        ?: throw IOException("Backup playlist has no valid stations")
                } finally {
                    conn.disconnect()
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppDiagnostics.failure("Radio", "independent playlist unavailable", error)
                emptyList()
            }
            val available = refreshed.ifEmpty { bundledStations(context) }
            val key = query.trim().lowercase(Locale.ROOT).replace('ё', 'е')
            if (key.isBlank()) available
            else available.filter {
                it.name.lowercase(Locale.ROOT).replace('ё', 'е').contains(key)
            }
        }
}
