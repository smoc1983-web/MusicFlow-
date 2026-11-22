package com.musicflow.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

internal enum class CinemaKind { MOVIE, SERIES }

internal data class CinemaEpisode(
    val id: String,
    val title: String,
    val streamUrl: String
)

internal data class CinemaTitle(
    val id: String,
    val sourceId: String,
    val title: String,
    val kind: CinemaKind,
    val year: Int?,
    val description: String,
    val poster: String?,
    val genre: String,
    val streamUrl: String?,
    val episodes: List<CinemaEpisode>,
    val attribution: String
) {
    val key: String get() = sourceId + ":" + id
    val hasPlayback: Boolean get() = streamUrl != null || episodes.isNotEmpty()
}

internal data class CinemaPlugin(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val catalogUrl: String
)

internal object CinemaLabSources {
    const val DEMO_SOURCE = "builtin"
    const val DEMO_MANIFEST_URL =
        "https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/feature/musicflow-cinema-lab-20261122/cinema-test/demo-plugin.json"
    private const val PREFS = "musicflow_cinema_lab_v1"
    private const val MAX_MANIFEST = 32_000
    private const val MAX_CATALOG = 1_500_000

    /**
     * Only public HTTPS endpoints are accepted for third-party declarations.
     * Untrusted JavaScript plug-ins are never evaluated or interpreted as Android code.
     */
    fun isSafeHttps(value: String): Boolean {
        if (value.isBlank() || value.length > 2048 || value.any(Char::isWhitespace)) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) || uri.userInfo != null ||
            uri.fragment != null || uri.port !in listOf(-1, 443)) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host.length < 4 || !host.contains('.') ||
            host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".invalid") || host.endsWith(".test") ||
            host.endsWith(".example") || host.contains(':') ||
            host.all { it.isDigit() || it == '.' }) return false
        if (host.startsWith("10.") || host.startsWith("127.") ||
            host.startsWith("169.254.") || host.startsWith("192.168.") ||
            host.startsWith("0.")) return false
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return false
        }
        return true
    }

    fun isLampaScriptUrl(value: String): Boolean {
        val trimmed = value.lowercase().trim().trimEnd('/')
        return trimmed.endsWith(".js") ||
            trimmed.contains("bwa.ad/rc") || trimmed.contains("bwa.to/rc") ||
            trimmed.endsWith("/plugin/iptv")
    }

    fun parseManifest(raw: String, requestedUrl: String): CinemaPlugin {
        require(isSafeHttps(requestedUrl)) { "Нужна публичная HTTPS-ссылка на JSON-плагин." }
        val obj = JSONObject(raw)
        require(obj.optString("format") == "musicflow.cinema.v1") {
            "Это не Cinema JSON v1. Плагины JavaScript для Lampa требуют её API и здесь не выполняются."
        }
        require(!obj.has("script") && !obj.has("code") && !obj.has("javascript")) {
            "Исполняемые скрипты не поддерживаются."
        }
        val name = obj.optString("name").trim().take(64)
        val catalog = obj.optString("catalog_url").trim()
        require(name.isNotBlank() && isSafeHttps(catalog)) {
            "В манифесте нужны название и публичный HTTPS catalog_url."
        }
        return CinemaPlugin(UUID.randomUUID().toString(), name, requestedUrl, catalog)
    }

    fun parseCatalog(raw: String, sourceId: String): List<CinemaTitle> {
        val array = JSONObject(raw).optJSONArray("items")
            ?: throw IllegalArgumentException("Каталог должен содержать массив items.")
        val result = mutableListOf<CinemaTitle>()
        for (i in 0 until minOf(array.length(), 150)) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id").trim().take(100)
            val title = o.optString("title").trim().take(160)
            val kind = when (o.optString("type").lowercase()) {
                "movie", "film" -> CinemaKind.MOVIE
                "series", "show" -> CinemaKind.SERIES
                else -> continue
            }
            if (id.isEmpty() || title.isEmpty()) continue
            val poster = o.optString("poster").takeIf(::isSafeHttps)
            val direct = o.optString("stream_url").takeIf(::isSafeHttps)
            val episodes = mutableListOf<CinemaEpisode>()
            val parts = o.optJSONArray("episodes") ?: JSONArray()
            for (j in 0 until minOf(parts.length(), 60)) {
                val p = parts.optJSONObject(j) ?: continue
                val url = p.optString("stream_url")
                if (!isSafeHttps(url)) continue
                episodes.add(CinemaEpisode(
                    p.optString("id").ifBlank { (j + 1).toString() }.take(64),
                    p.optString("title").ifBlank { "Серия " + (j + 1) }.take(130),
                    url
                ))
            }
            val year = o.optInt("year", 0).takeIf { it in 1880..2100 }
            val summary = Jsoup.parse(o.optString("description")).text().take(750)
            result.add(CinemaTitle(id, sourceId, title, kind, year, summary,
                poster, o.optString("genre").take(90), direct, episodes,
                o.optString("attribution").take(240)))
        }
        return result.distinctBy { it.key }
    }

    fun bundled(context: Context): List<CinemaTitle> = runCatching {
        val json = context.assets.open("cinema_lab_demo.json")
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        parseCatalog(json, DEMO_SOURCE)
    }.getOrElse {
        AppDiagnostics.failure("Cinema", "load embedded demo catalog", it)
        emptyList()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun plugins(context: Context): List<CinemaPlugin> = runCatching {
        val arr = JSONArray(preferences(context).getString("plugins", "[]"))
        buildList {
            for (i in 0 until minOf(20, arr.length())) {
                val o = arr.optJSONObject(i) ?: continue
                val manifest = o.optString("manifest")
                val catalog = o.optString("catalog")
                if (isSafeHttps(manifest) && isSafeHttps(catalog)) {
                    add(CinemaPlugin(o.optString("id"), o.optString("name"),
                        manifest, catalog))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun savePlugins(context: Context, plugins: List<CinemaPlugin>) {
        val arr = JSONArray()
        plugins.take(20).forEach { plugin ->
            arr.put(JSONObject().apply {
                put("id", plugin.id)
                put("name", plugin.name)
                put("manifest", plugin.manifestUrl)
                put("catalog", plugin.catalogUrl)
            })
        }
        preferences(context).edit().putString("plugins", arr.toString()).apply()
    }

    fun favoriteKeys(context: Context): Set<String> =
        preferences(context).getStringSet("favorites", emptySet())?.toSet().orEmpty()

    fun saveFavorites(context: Context, keys: Set<String>) {
        preferences(context).edit().putStringSet("favorites", keys.toSet()).apply()
    }

    fun laterKeys(context: Context): Set<String> =
        preferences(context).getStringSet("watch_later", emptySet())?.toSet().orEmpty()

    fun saveLater(context: Context, keys: Set<String>) {
        preferences(context).edit().putStringSet("watch_later", keys.toSet()).apply()
    }

    fun recentKeys(context: Context): List<String> =
        preferences(context).getString("recent", "").orEmpty()
            .split('|').filter { it.isNotBlank() }.take(25)

    fun markRecent(context: Context, key: String) {
        val value = (listOf(key) + recentKeys(context)).distinct().take(25).joinToString("|")
        preferences(context).edit().putString("recent", value).apply()
    }

    fun progress(context: Context, key: String): Long =
        preferences(context).getLong("position-" + key.hashCode().toString(), 0)

    fun saveProgress(context: Context, key: String, positionMs: Long) {
        preferences(context).edit().putLong(
            "position-" + key.hashCode().toString(), positionMs.coerceAtLeast(0)).apply()
    }

    suspend fun installPlugin(url: String): CinemaPlugin = withContext(Dispatchers.IO) {
        require(!isLampaScriptUrl(url)) {
            "Это JavaScript-плагин Lampa. В тесте нужны JSON-плагины Cinema v1, чужой JS не запускается."
        }
        parseManifest(String(fetch(url, MAX_MANIFEST), StandardCharsets.UTF_8), url)
    }

    suspend fun fetchPluginCatalog(plugin: CinemaPlugin): List<CinemaTitle> =
        withContext(Dispatchers.IO) {
            parseCatalog(String(fetch(plugin.catalogUrl, MAX_CATALOG), StandardCharsets.UTF_8),
                plugin.id)
        }

    suspend fun searchSeries(query: String): List<CinemaTitle> = withContext(Dispatchers.IO) {
        val q = query.trim().take(75)
        if (q.length < 2) return@withContext emptyList()
        val url = "https://api.tvmaze.com/search/shows?q=" +
            java.net.URLEncoder.encode(q, "UTF-8")
        val array = JSONArray(String(fetch(url, 350_000), StandardCharsets.UTF_8))
        (0 until minOf(25, array.length())).mapNotNull { index ->
            val show = array.optJSONObject(index)?.optJSONObject("show")
                ?: return@mapNotNull null
            val id = show.optInt("id", 0).takeIf { it > 0 }?.toString()
                ?: return@mapNotNull null
            val title = show.optString("name").trim().take(140)
            if (title.isEmpty()) return@mapNotNull null
            val poster = show.optJSONObject("image")?.optString("medium")
                ?.takeIf(::isSafeHttps)
            val year = show.optString("premiered").take(4).toIntOrNull()
            CinemaTitle(id, "tvmaze", title, CinemaKind.SERIES, year,
                Jsoup.parse(show.optString("summary")).text().take(750),
                poster,
                show.optJSONArray("genres")?.let { genres ->
                    (0 until minOf(4, genres.length())).joinToString(", ") {
                        genres.optString(it)
                    }
                }.orEmpty(),
                null, emptyList(), "TVMaze · сведения о сериале (не источник видео)")
        }
    }

    private fun fetch(url: String, limit: Int): ByteArray {
        require(isSafeHttps(url)) { "Источник должен находиться на публичном HTTPS." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json,text/plain")
            setRequestProperty("User-Agent", "MusicFlow-Cinema-TEST/0.1")
        }
        try {
            if (connection.responseCode != 200) {
                throw IllegalArgumentException("Источник вернул HTTP " + connection.responseCode)
            }
            connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        require(output.size() + count <= limit) {
                            "Источник вернул слишком большой каталог"
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
