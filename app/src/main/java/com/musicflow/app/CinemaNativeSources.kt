package com.musicflow.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Supported input adapters for native Media3 video playback (no embedded Lampa UI). */
internal enum class CinemaSourceFormat {
    CINEMA_JSON, M3U_PLAYLIST, DIRECT_MEDIA, LAMPA_REFERENCE
}
internal data class CinemaNativeSource(
    val id: String,
    val name: String,
    val url: String,
    val format: CinemaSourceFormat
) {
    val playable: Boolean get() = format != CinemaSourceFormat.LAMPA_REFERENCE
}

internal object CinemaNativeSources {
    private const val PREFS = "musicflow_cinema_native_sources_v3"
    private const val MAX_SOURCE_BYTES = 1_500_000
    private const val MAX_SOURCES = 24
    const val DEMO_M3U =
        "https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/feature/musicflow-cinema-native-sources-test-v3/cinema-test/native-demo.m3u"
    const val DEMO_JSON =
        "https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/feature/musicflow-cinema-native-sources-test-v3/cinema-test/catalog.json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isVideoUrl(url: String): Boolean {
        if (!CinemaLabSources.isSafeHttps(url)) return false
        val suffix = runCatching { URL(url).path.lowercase() }.getOrDefault("")
        return listOf(".mp4", ".m3u8", ".webm", ".mkv", ".mpd", ".ts")
            .any { suffix.endsWith(it) }
    }

    fun isLampaPluginUrl(url: String): Boolean =
        url.startsWith("http://", true) || url.endsWith(".js", true) ||
            url.contains("bwa.ad/rc", true) || url.contains("bwa.to/rc", true) ||
            url.contains("/plugins/", true) && !url.endsWith(".json", true)

    fun sources(context: Context): List<CinemaNativeSource> = runCatching {
        val arr = JSONArray(prefs(context).getString("registered", "[]"))
        buildList {
            for (i in 0 until minOf(MAX_SOURCES, arr.length())) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = runCatching {
                    CinemaSourceFormat.valueOf(o.optString("format"))
                }.getOrNull() ?: continue
                val url = o.optString("url")
                if (kind == CinemaSourceFormat.LAMPA_REFERENCE) {
                    if (!CinemaLampaBridge.allowedPluginUrl(url)) continue
                } else if (!CinemaLabSources.isSafeHttps(url)) continue
                add(CinemaNativeSource(
                    o.optString("id"),
                    o.optString("name"),
                    url,
                    kind
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, sources: List<CinemaNativeSource>) {
        val items = JSONArray()
        sources.take(MAX_SOURCES).forEach {
            items.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("url", it.url)
                put("format", it.format.name)
            })
        }
        prefs(context).edit().putString("registered", items.toString()).apply()
    }

    private fun fetch(address: String): String {
        require(CinemaLabSources.isSafeHttps(address)) { "Нужен публичный HTTPS-адрес." }
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 7_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("User-Agent", "MusicFlow-Cinema-Native-Test/3")
        connection.setRequestProperty("Accept", "application/json,audio/x-mpegurl,text/plain")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Источник вернул HTTP " + connection.responseCode)
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        if (bytes.size() + count > MAX_SOURCE_BYTES) {
                            throw IOException("Слишком большой ответ источника")
                        }
                        bytes.write(buffer, 0, count)
                    }
                }
            }
            return bytes.toString(StandardCharsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    suspend fun register(name: String, input: String): CinemaNativeSource =
        withContext(Dispatchers.IO) {
            val url = input.trim()
            val title = name.trim().take(64)
            require(url.isNotBlank()) { "Вставьте URL источника." }
            if (isLampaPluginUrl(url)) {
                require(CinemaLampaBridge.allowedPluginUrl(url)) {
                    "Недопустимый адрес JS-плагина."
                }
                return@withContext CinemaNativeSource(
                    UUID.randomUUID().toString(),
                    title.ifBlank { "Lampa JS · адаптер не подключён" },
                    url,
                    CinemaSourceFormat.LAMPA_REFERENCE
                )
            }
            require(CinemaLabSources.isSafeHttps(url)) {
                "Для нативного каталога требуется публичный HTTPS."
            }
            val kind = if (isVideoUrl(url)) {
                CinemaSourceFormat.DIRECT_MEDIA
            } else {
                val response = fetch(url).trimStart('\uFEFF', '\n', '\r', ' ', '\t')
                when {
                    response.startsWith("#EXTM3U") -> {
                        require(!response.contains("#EXT-X-STREAM-INF") &&
                            !response.contains("#EXT-X-TARGETDURATION")) {
                            "Это HLS-поток, вставьте URL с расширением .m3u8 как видео."
                        }
                        require(parseM3u(response, "validate").isNotEmpty()) {
                            "M3U не содержит доступных HTTPS-видео."
                        }
                        CinemaSourceFormat.M3U_PLAYLIST
                    }
                    response.startsWith("{") -> {
                        val json = JSONObject(response)
                        val manifest = json.optString("format") == "musicflow.cinema.v1"
                        if (manifest) {
                            val catalog = json.optString("catalog_url")
                            require(CinemaLabSources.isSafeHttps(catalog)) {
                                "Некорректный catalog_url в JSON."
                            }
                            require(CinemaLabSources.parseCatalog(fetch(catalog), "validate")
                                .isNotEmpty()) { "Каталог JSON не содержит элементов." }
                        } else {
                            require(CinemaLabSources.parseCatalog(response, "validate")
                                .isNotEmpty()) { "JSON-каталог пуст." }
                        }
                        CinemaSourceFormat.CINEMA_JSON
                    }
                    else -> throw IllegalArgumentException(
                        "Источник не распознан: поддерживаются JSON, M3U и видео HTTPS. " +
                        "Скрипты Lampa можно зарегистрировать как ссылку, " +
                        "но они не передают нативному Media3 прямые потоки."
                    )
                }
            }
            CinemaNativeSource(UUID.randomUUID().toString(),
                title.ifBlank { url.substringAfter("://").substringBefore('/').take(55) },
                url, kind)
        }

    suspend fun load(source: CinemaNativeSource): List<CinemaTitle> =
        withContext(Dispatchers.IO) {
            when (source.format) {
                CinemaSourceFormat.LAMPA_REFERENCE -> emptyList()
                CinemaSourceFormat.DIRECT_MEDIA ->
                    listOf(CinemaTitle(
                        source.id,
                        source.id,
                        source.name,
                        CinemaKind.MOVIE,
                        null,
                        "Прямая HTTPS-ссылка на видео, добавленная пользователем.",
                        null, "Мой источник", source.url,
                        emptyList(), "Личный источник"
                    ))
                CinemaSourceFormat.M3U_PLAYLIST -> parseM3u(fetch(source.url), source.id)
                CinemaSourceFormat.CINEMA_JSON -> {
                    val raw = fetch(source.url)
                    val obj = JSONObject(raw)
                    val catalog = if (obj.optString("format") == "musicflow.cinema.v1") {
                        val url = obj.optString("catalog_url")
                        require(CinemaLabSources.isSafeHttps(url)) {
                            "Каталог плагина должен быть по HTTPS."
                        }
                        fetch(url)
                    } else raw
                    CinemaLabSources.parseCatalog(catalog, source.id)
                }
            }
        }

    /** Extract only user-supplied HTTPS direct streams from structured M3U playlists. */
    fun parseM3u(raw: String, sourceId: String): List<CinemaTitle> {
        val results = mutableListOf<CinemaTitle>()
        var name = ""
        var genre = "Плейлист M3U"
        var poster: String? = null
        for (line in raw.lineSequence()) {
            val text = line.trim()
            when {
                text.startsWith("#EXTINF:", true) -> {
                    name = text.substringAfter(",", "").trim().take(140)
                    genre = Regex("group-title=\"([^\"]+)\"")
                        .find(text)?.groupValues?.getOrNull(1)?.take(80) ?: "Плейлист M3U"
                    poster = Regex("tvg-logo=\"([^\"]+)\"")
                        .find(text)?.groupValues?.getOrNull(1)?.takeIf {
                            CinemaLabSources.isSafeHttps(it)
                        }
                }
                text.isNotEmpty() && !text.startsWith("#") -> {
                    if (name.isNotEmpty() && CinemaLabSources.isSafeHttps(text) &&
                        results.size < 400) {
                        results.add(CinemaTitle(
                            id = UUID.nameUUIDFromBytes(text.toByteArray(StandardCharsets.UTF_8))
                                .toString(),
                            sourceId = sourceId,
                            title = name,
                            kind = CinemaKind.MOVIE,
                            year = null,
                            description = "Видео из пользовательского M3U-плейлиста.",
                            poster = poster,
                            genre = genre,
                            streamUrl = text,
                            episodes = emptyList(),
                            attribution = "Сторонний M3U-источник"
                        ))
                    }
                    name = ""
                    genre = "Плейлист M3U"
                    poster = null
                }
            }
        }
        return results.distinctBy { it.id }
    }
}
