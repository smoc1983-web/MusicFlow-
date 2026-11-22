package com.musicflow.app

import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * RUTUBE's public feed and documented play-options API.
 * Does not use authentication cookies, unlock paid videos or bypass DRM.
 */
internal object CinemaRutubeSource {
    const val FEED_URL = "https://rutube.ru/feeds/movies-serials/"
    private val videoPath = Regex("^/(?:video|play/embed)/([a-fA-F0-9]{32})/?$")
    private const val MAX_CARDS = 90

    fun videoId(url: String): String? {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("https", true) ||
            parsed.host?.lowercase()?.trimEnd('.') !in listOf("rutube.ru", "www.rutube.ru")) {
            return null
        }
        return videoPath.matchEntire(parsed.path.orEmpty())?.groupValues?.get(1)?.lowercase()
    }

    fun isFeed(url: String): Boolean {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        return parsed.scheme.equals("https", true) &&
            parsed.host?.lowercase()?.trimEnd('.') in listOf("rutube.ru", "www.rutube.ru") &&
            parsed.path.orEmpty().trimEnd('/') == "/feeds/movies-serials"
    }

    private fun safeText(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().take(170)

    private fun imageFrom(node: org.jsoup.nodes.Element): String? {
        val photo = node.selectFirst("img")
            ?: node.parent()?.selectFirst("img")
        val candidates = listOfNotNull(
            photo?.attr("data-src"),
            photo?.attr("src"),
            photo?.attr("data-original"),
            photo?.attr("srcset")?.substringBefore(' ')?.trim(),
            photo?.attr("data-srcset")?.substringBefore(' ')?.trim()
        )
        return candidates.asSequence().filter { it.isNotBlank() }
            .mapNotNull { candidate ->
            val full = runCatching {
                URI(FEED_URL).resolve(candidate).toString()
            }.getOrNull()
            full?.takeIf(CinemaLabSources::isSafeHttps)
        }.firstOrNull()
    }

    fun parseFeed(markup: String, sourceId: String): List<CinemaTitle> {
        val doc = Jsoup.parse(markup, FEED_URL)
        val results = mutableListOf<CinemaTitle>()
        val seen = HashSet<String>()
        for (link in doc.select("a[href]")) {
            if (results.size >= MAX_CARDS) break
            val absolute = link.absUrl("href")
            val id = videoId(absolute) ?: continue
            if (!seen.add(id)) continue

            val titleOptions = listOf(
                link.attr("title"),
                link.attr("aria-label"),
                link.selectFirst("img")?.attr("alt").orEmpty(),
                link.selectFirst("[title]")?.attr("title").orEmpty(),
                link.selectFirst("h3,h4")?.text().orEmpty(),
                link.text(),
                link.parent()?.selectFirst("h3,h4,[class*=title],[class*=name]")
                    ?.text().orEmpty(),
                link.parent()?.text().orEmpty()
            )
            val title = titleOptions.map(::safeText).firstOrNull {
                it.length in 4..165 &&
                    !it.equals("Смотреть", true) &&
                    !it.contains("подробнее", true) &&
                    !it.contains("подписаться", true) &&
                    !it.matches(Regex("\\d+"))
            } ?: "Видео RUTUBE · " + id.take(8)

            val category = when {
                title.contains("сезон", true) || title.contains("серия", true) ->
                    "Сериал / серия"
                else -> "Кино и сериалы"
            }
            val poster = imageFrom(link)
            results.add(CinemaTitle(
                id = id,
                sourceId = sourceId,
                title = title,
                kind = if (category.startsWith("Сериал")) CinemaKind.SERIES
                    else CinemaKind.MOVIE,
                year = Regex("(?:19|20)\\d{2}").find(title)?.value?.toIntOrNull(),
                description = "Видео из раздела «Кино и сериалы» RUTUBE. " +
                    "При выборе проверяется публичный HLS-поток.",
                poster = poster,
                genre = category,
                streamUrl = null,
                episodes = emptyList(),
                attribution = "RUTUBE",
                pageUrl = "https://rutube.ru/video/$id/"
            ))
        }
        return results
    }

    fun apiUrl(videoId: String): String {
        require(videoId.matches(Regex("[a-fA-F0-9]{32}"))) {
            "Неверный идентификатор видео RUTUBE"
        }
        return "https://rutube.ru/api/play/options/$videoId/?format=json"
    }

    /**
     * Public API only. A stream being listed doesn't certify that all
     * playback restrictions have been satisfied. Media3 verifies playback.
     */
    fun fromPlayOptions(original: CinemaTitle, raw: String): CinemaTitle {
        val options = JSONObject(raw)
        val restricted = options.optBoolean("is_paid", false) ||
            options.optBoolean("is_hidden", false) ||
            options.optJSONObject("acl_access")?.optBoolean("is_forbidden", false) == true

        val balancer = options.optJSONObject("video_balancer")
        val rawStream = balancer?.optString("m3u8").orEmpty()
        val playable = !restricted &&
            CinemaLabSources.isSafeHttps(rawStream) &&
            rawStream.substringBefore('?').lowercase().contains(".m3u8")

        val name = options.optString("title").trim().take(170)
            .ifBlank { original.title }
        val summary = options.optString("description").trim().take(800)
        return original.copy(
            title = name,
            description = if (summary.isBlank()) original.description else
                Jsoup.parse(summary).text(),
            poster = options.optString("thumbnail_url")
                .takeIf(CinemaLabSources::isSafeHttps) ?: original.poster,
            streamUrl = if (playable) rawStream else null,
            playbackDiagnostic = when {
                restricted -> "RUTUBE ограничивает доступ к этому видео. " +
                    "Платный, закрытый или запрещённый контент не открывается."
                playable -> "RUTUBE предоставил публичный HLS-поток через /api/play/options/. " +
                    "Проверка фактического воспроизведения выполняется Media3."
                else -> "В публичном API RUTUBE нет разрешённого HLS-потока для этого видео. " +
                    "Просмотр в собственном Media3-плеере недоступен."
            }
        )
    }

    fun fromMeta(original: CinemaTitle, raw: String): CinemaTitle {
        val data = JSONObject(raw)
        return original.copy(
            title = data.optString("title").take(170).ifBlank { original.title },
            description = data.optString("description").take(800)
                .ifBlank { original.description }.let { Jsoup.parse(it).text() },
            poster = data.optString("thumbnail_url").takeIf(CinemaLabSources::isSafeHttps)
                ?: original.poster,
            year = original.year
        )
    }
}
