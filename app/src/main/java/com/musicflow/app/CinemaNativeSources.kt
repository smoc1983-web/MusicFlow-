package com.musicflow.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/** URLs entered by the user; no installable plug-ins or untrusted JavaScript execution. */
internal enum class CinemaSourceFormat {
    WEB_PAGE, DIRECT_MEDIA, M3U_PLAYLIST, CINEMA_JSON
}

internal data class CinemaNativeSource(
    val id: String,
    val name: String,
    val url: String,
    val format: CinemaSourceFormat
)

internal object CinemaNativeSources {
    const val FIRST_PAGE = "https://ff.sanfilm2.link/"
    private const val FIRST_NAME = "Sanfilm · каталог"
    private const val PREFS = "musicflow_cinema_urls_v5"
    private const val MAX_SOURCES = 30
    private const val MAX_RESPONSE_BYTES = 1_500_000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Seed the supplied page once, not every time the user removes it. */
    fun sources(context: Context): List<CinemaNativeSource> {
        val preferences = prefs(context)
        if (!preferences.getBoolean("initialized", false)) {
            val first = CinemaNativeSource(
                "starter-site", FIRST_NAME, FIRST_PAGE, CinemaSourceFormat.WEB_PAGE
            )
            save(context, listOf(first))
            preferences.edit().putBoolean("initialized", true).apply()
            return listOf(first)
        }
        return runCatching {
            val array = JSONArray(preferences.getString("sources", "[]"))
            buildList {
                for (index in 0 until minOf(array.length(), MAX_SOURCES)) {
                    val item = array.optJSONObject(index) ?: continue
                    val format = runCatching {
                        CinemaSourceFormat.valueOf(item.optString("format"))
                    }.getOrNull() ?: continue
                    val url = item.optString("url")
                    if (!CinemaLabSources.isSafeHttps(url)) continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(CinemaNativeSource(
                        id, item.optString("name").take(90), url, format
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, sources: List<CinemaNativeSource>) {
        val array = JSONArray()
        sources.take(MAX_SOURCES).forEach { source ->
            array.put(JSONObject().apply {
                put("id", source.id)
                put("name", source.name)
                put("url", source.url)
                put("format", source.format.name)
            })
        }
        prefs(context).edit().putString("sources", array.toString()).apply()
    }

    fun isVideoUrl(url: String): Boolean {
        if (!CinemaLabSources.isSafeHttps(url)) return false
        val path = runCatching { URI(url).path.lowercase() }.getOrDefault("")
        return listOf(".mp4", ".m3u8", ".mpd", ".webm", ".mkv", ".ts")
            .any { path.endsWith(it) }
    }

    fun chooseFormat(url: String): CinemaSourceFormat {
        val path = runCatching { URI(url).path.lowercase() }.getOrDefault("")
        return when {
            isVideoUrl(url) -> CinemaSourceFormat.DIRECT_MEDIA
            path.endsWith(".m3u") -> CinemaSourceFormat.M3U_PLAYLIST
            path.endsWith(".json") -> CinemaSourceFormat.CINEMA_JSON
            else -> CinemaSourceFormat.WEB_PAGE
        }
    }

    /** Adds the URL immediately, even when its server is blocked on this device. */
    fun register(name: String, value: String): CinemaNativeSource {
        val url = value.trim()
        require(CinemaLabSources.isSafeHttps(url)) {
            "Нужна публичная HTTPS-ссылка без логина и пароля."
        }
        require(!url.lowercase().substringBefore('?').endsWith(".js")) {
            "JS-плагины больше не поддерживаются. Добавьте ссылку на страницу фильма или видео."
        }
        val fallback = URL(url).host.removePrefix("www.")
        return CinemaNativeSource(
            UUID.randomUUID().toString(),
            name.trim().take(90).ifBlank { fallback },
            url,
            chooseFormat(url)
        )
    }

    suspend fun load(source: CinemaNativeSource): List<CinemaTitle> =
        withContext(Dispatchers.IO) {
            when (source.format) {
                CinemaSourceFormat.DIRECT_MEDIA -> listOf(
                    CinemaTitle(
                        source.id, source.id, source.name, CinemaKind.MOVIE,
                        null, "Прямая видеоссылка пользователя.",
                        null, "Видео", source.url, emptyList(), "Добавлено по URL"
                    )
                )
                CinemaSourceFormat.M3U_PLAYLIST -> parseM3u(fetch(source.url), source.id)
                CinemaSourceFormat.CINEMA_JSON -> {
                    val raw = fetch(source.url)
                    val obj = JSONObject(raw)
                    val catalog = if (obj.optString("format") == "musicflow.cinema.v1") {
                        val remote = obj.optString("catalog_url")
                        require(CinemaLabSources.isSafeHttps(remote)) {
                            "JSON ссылается на недопустимый адрес каталога."
                        }
                        fetch(remote)
                    } else raw
                    CinemaLabSources.parseCatalog(catalog, source.id)
                }
                CinemaSourceFormat.WEB_PAGE -> {
                    val markup = fetch(source.url)
                    val path = URI(source.url).path.orEmpty()
                    if (path.isEmpty() || path == "/" || path == "/index.html") {
                        parseSiteIndex(markup, source.url, source.id).ifEmpty {
                            listOf(parseHtml(markup, source.url, source.id, source.name))
                        }
                    } else listOf(parseHtml(markup, source.url, source.id, source.name))
                }
            }
        }


    /**
     * For a website homepage, offer a list of ordinary public movie-page links.
     * No page scripts or embedded player code is evaluated. Entries become
     * metadata-only cards until the user opens a specific page.
     */
    fun parseSiteIndex(raw: String, base: String, sourceId: String): List<CinemaTitle> {
        val document = Jsoup.parse(raw, base)
        val origin = URI(base).host.lowercase().removePrefix("www.")
        val entries = mutableListOf<CinemaTitle>()
        val seen = HashSet<String>()
        for (anchor in document.select("a[href]")) {
            if (entries.size >= 80) break
            val absolute = anchor.attr("abs:href").trim()
            val address = runCatching { URI(absolute) }.getOrNull() ?: continue
            if (!CinemaLabSources.isSafeHttps(absolute) ||
                address.host?.lowercase()?.removePrefix("www.") != origin ||
                !address.path.orEmpty().lowercase().endsWith(".html") ||
                address.path.orEmpty().count { it == '/' } > 5 ||
                !seen.add(address.path.orEmpty())) continue
            val lastPart = address.path.orEmpty().substringAfterLast('/').lowercase()
            if (listOf("privacy", "contact", "feedback", "help", "terms",
                    "about", "login", "search", "rules", "sitemap").any {
                    lastPart.contains(it)
                }) continue
            val image = anchor.selectFirst("img")
                ?: anchor.parent()?.selectFirst("img")
            val possibleNames = listOf(
                anchor.attr("title"), anchor.attr("aria-label"),
                image?.attr("alt").orEmpty(), anchor.text(),
                anchor.parent()?.selectFirst(".title,.name,h2,h3")?.text().orEmpty()
            )
            val title = possibleNames.firstOrNull {
                it.trim().length in 5..150 && !it.contains("Подробнее", true)
            }?.trim() ?: continue
            val posterCandidate = listOfNotNull(
                image?.attr("abs:data-src"),
                image?.attr("abs:src"),
                image?.absUrl("data-src"),
                image?.absUrl("src")
            ).firstOrNull(CinemaLabSources::isSafeHttps)
            val year = Regex("(?:19|20)\\d{2}").find(title)?.value?.toIntOrNull()
            entries.add(CinemaTitle(
                id = UUID.nameUUIDFromBytes(
                    address.path.orEmpty().toByteArray(StandardCharsets.UTF_8)
                ).toString(),
                sourceId = sourceId,
                title = title,
                kind = CinemaKind.MOVIE,
                year = year,
                description = "Карточка каталога сайта. Откройте страницу, чтобы проверить описание и наличие прямого видеопотока.",
                poster = posterCandidate,
                genre = "Каталог сайта",
                streamUrl = null,
                episodes = emptyList(),
                attribution = "Страница: " + address.host,
                pageUrl = absolute
            ))
        }
        return entries
    }

    suspend fun resolvePage(item: CinemaTitle): CinemaTitle = withContext(Dispatchers.IO) {
        val address = item.pageUrl ?: return@withContext item
        require(CinemaLabSources.isSafeHttps(address)) {
            "Небезопасная ссылка страницы фильма."
        }
        parseHtml(fetch(address), address, item.id, item.title)
            .copy(sourceId = item.sourceId)
    }

    fun placeholder(source: CinemaNativeSource, error: Throwable): CinemaTitle =
        CinemaTitle(
            source.id, source.id, source.name, CinemaKind.MOVIE, null,
            "Страница пока не отвечает или недоступна из этой сети: " +
                (error.message ?: "ошибка подключения").take(160) +
                ". Ссылка сохранена; попробуйте обновить её позже.",
            null, "Страница по ссылке", null, emptyList(), "Источник пользователя"
        )

    /**
     * Reads public OpenGraph/HTML5 video markup only. No iframe execution,
     * deobfuscation, DRM bypass, private tokens or hidden player APIs.
     */
    fun parseHtml(raw: String, url: String, id: String, userTitle: String): CinemaTitle {
        val document = Jsoup.parse(raw, url)
        fun meta(property: String): String = document
            .selectFirst("meta[property=\"$property\"],meta[name=\"$property\"]")
            ?.attr("content").orEmpty().trim()

        fun resolvePublic(value: String): String? {
            if (value.isBlank()) return null
            val resolved = runCatching { URI(url).resolve(value.trim()).toString() }
                .getOrNull() ?: return null
            return resolved.takeIf(CinemaLabSources::isSafeHttps)
        }
        val pageTitle = meta("og:title").ifBlank { document.title() }
            .replace(Regex("\\s+"), " ").trim().take(150)
        val name = pageTitle.takeIf {
            it.isNotBlank() &&
                !it.contains("Just a moment", true) &&
                !it.contains("Access denied", true)
        } ?: userTitle
        val description = meta("og:description").ifBlank {
            document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        }.take(850).ifBlank {
            "Киноисточник сохранён. Прямая видеоссылка на странице не подтверждена."
        }
        val poster = resolvePublic(meta("og:image"))
        val options = mutableListOf<String>()
        options.add(meta("og:video:url"))
        options.add(meta("og:video:secure_url"))
        options.add(meta("twitter:player:stream"))
        document.select("video[src],video source[src],source[type^=video/][src]").take(20)
            .forEach { options.add(it.attr("src")) }
        val stream = options.asSequence().mapNotNull(::resolvePublic)
            .firstOrNull(::isVideoUrl)
        val year = Regex("(?:19|20)\\d{2}").find(name)?.value?.toIntOrNull()
        return CinemaTitle(
            id, id, name, CinemaKind.MOVIE, year,
            description, poster, "Страница фильма",
            stream, emptyList(), "Добавлено по ссылке: " + URI(url).host
        )
    }

    fun parseM3u(raw: String, sourceId: String): List<CinemaTitle> {
        val result = mutableListOf<CinemaTitle>()
        var title = ""
        var group = "Каталог M3U"
        var poster: String? = null
        for (line in raw.lineSequence()) {
            val item = line.trim()
            when {
                item.startsWith("#EXTINF:", ignoreCase = true) -> {
                    title = item.substringAfter(',', "").trim().take(150)
                    group = Regex("group-title=\"([^\"]+)\"")
                        .find(item)?.groupValues?.getOrNull(1)?.take(100)
                        ?: "Каталог M3U"
                    poster = Regex("tvg-logo=\"([^\"]+)\"")
                        .find(item)?.groupValues?.getOrNull(1)
                        ?.takeIf(CinemaLabSources::isSafeHttps)
                }
                item.isNotBlank() && !item.startsWith("#") -> {
                    if (title.isNotBlank() && isVideoUrl(item) && result.size < 300) {
                        result.add(CinemaTitle(
                            UUID.nameUUIDFromBytes(item.toByteArray(StandardCharsets.UTF_8))
                                .toString(),
                            sourceId, title, CinemaKind.MOVIE, null,
                            "Видео из пользовательского M3U-каталога.",
                            poster, group, item, emptyList(), "Добавлено по M3U"
                        ))
                    }
                    title = ""
                    group = "Каталог M3U"
                    poster = null
                }
            }
        }
        return result.distinctBy { it.key }
    }

    /** Bound responses. Only HTTPS, redirects followed only to other public HTTPS URLs. */
    private fun fetch(address: String): String {
        var current = address
        repeat(4) { hop ->
            require(CinemaLabSources.isSafeHttps(current)) {
                "Источнику разрешён только публичный HTTPS-адрес."
            }
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MusicFlowCinema/4")
            conn.setRequestProperty("Accept", "text/html,application/json,audio/x-mpegurl,*/*")
            try {
                val status = conn.responseCode
                if (status in 300..399) {
                    if (hop == 3) throw IOException("Превышено число перенаправлений")
                    val location = conn.getHeaderField("Location").orEmpty()
                    val next = runCatching {
                        URI(current).resolve(location).toString()
                    }.getOrNull() ?: throw IOException("Некорректное перенаправление")
                    require(CinemaLabSources.isSafeHttps(next)) {
                        "Небезопасное перенаправление заблокировано"
                    }
                    current = next
                } else {
                    if (status !in 200..299) throw IOException("HTTP $status")
                    val out = ByteArrayOutputStream()
                    conn.inputStream.use { input ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n > 0) {
                                if (out.size() + n > MAX_RESPONSE_BYTES) {
                                    throw IOException("Слишком большая страница (макс. 1,5 МБ)")
                                }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    return out.toString(StandardCharsets.UTF_8.name())
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Источник не отвечает")
    }
}
