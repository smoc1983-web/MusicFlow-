package com.musicflow.app

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URI

internal data class CinemaVideoScan(
    val mediaUrl: String?,
    val method: String,
    val iframeCount: Int = 0,
    val inspected: Int = 0
) {
    fun report(): String = if (mediaUrl != null) {
        "Открытая видеоссылка найдена: " + method +
            ". Возможность воспроизведения проверяется Media3."
    } else {
        "Прямая ссылка не найдена. Проверены HTML5, OpenGraph, JSON-LD, " +
            "открытые конфигурации и " + inspected + " из " + iframeCount +
            " iframe. Возможно, сайт использует JavaScript, авторизацию или DRM."
    }
}

/**
 * Static public sources only. Does not execute scripts, decrypt, bypass
 * access controls or synthesize signed URLs.
 */
internal object CinemaPublicStreamResolver {
    private val extensions = listOf(".m3u8", ".mp4", ".mpd", ".webm", ".mkv", ".ts")
    private val literal = Regex(
        """(?is)(?:["'](?:file|src|source|stream|videoUrl|contentUrl|hls)["']|(?:file|source|stream|videoUrl|contentUrl))\s*[:=]\s*["']([^"']{5,1900})["']"""
    )

    private fun normalized(value: String, base: String): String? {
        if (value.length > 2048 || value.isBlank()) return null
        val decoded = Parser.unescapeEntities(value.trim(), false)
            .replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u003A", ":", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
        val url = runCatching { URI(base).resolve(decoded).toString() }.getOrNull()
            ?: return null
        return url.takeIf(CinemaLabSources::isSafeHttps)
    }

    private fun media(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        return extensions.any(path::endsWith)
    }

    private fun walkJson(node: Any?, depth: Int, consumer: (String, String) -> Unit) {
        if (depth > 6) return
        when (node) {
            is JSONObject -> for (key in node.keys()) {
                when (val value = node.opt(key)) {
                    is String -> consumer(key, value)
                    is JSONObject, is JSONArray -> walkJson(value, depth + 1, consumer)
                }
            }
            is JSONArray -> for (i in 0 until minOf(node.length(), 50)) {
                walkJson(node.opt(i), depth + 1, consumer)
            }
        }
    }

    private fun embeds(raw: String, url: String): List<String> =
        Jsoup.parse(raw, url).select(
            "iframe[src],iframe[data-src],iframe[data-lazy-src],embed[src]"
        ).asSequence().flatMap {
            sequenceOf(it.attr("src"), it.attr("data-src"), it.attr("data-lazy-src"))
        }.mapNotNull { normalized(it, url) }.distinct().take(8).toList()

    fun scan(html: String, page: String): CinemaVideoScan {
        val doc = Jsoup.parse(html, page)
        val iframeCount = embeds(html, page).size
        var found: String? = null
        var method = ""
        fun offer(value: String?, reason: String) {
            if (found != null || value.isNullOrBlank()) return
            val candidate = normalized(value, page) ?: return
            if (media(candidate)) { found = candidate; method = reason }
        }
        for (key in listOf(
            "og:video", "og:video:url", "og:video:secure_url",
            "twitter:player:stream", "video:content_url"
        )) {
            doc.select("meta[property=$key],meta[name=$key]").forEach {
                offer(it.attr("content"), "OpenGraph")
            }
        }
        doc.select("video,video source,source[type^=video/],link[as=video]").forEach {
            for (attr in listOf("src", "data-src", "data-url", "href", "content")) {
                offer(it.attr(attr), "HTML5")
            }
        }
        doc.select("script[type=application/ld+json]").take(8).forEach {
            if (it.data().length <= 150_000) {
                runCatching {
                    walkJson(JSONTokener(it.data()).nextValue(), 0) { key, value ->
                        if (key in listOf("contentUrl", "videoUrl", "streamUrl", "file")) {
                            offer(value, "JSON-LD")
                        }
                    }
                }
            }
        }
        doc.select("script:not([src])").take(30).forEach {
            if (it.data().length <= 150_000) {
                for (match in literal.findAll(it.data()).take(40)) {
                    offer(match.groupValues[1], "открытый параметр плеера")
                }
            }
        }
        return CinemaVideoScan(found, method, iframeCount)
    }

    /** Up to three publicly linked iframe HTML pages, without code execution. */
    fun resolve(html: String, page: String, fetch: (String) -> String): CinemaVideoScan {
        val initial = scan(html, page)
        if (initial.mediaUrl != null) return initial
        val linked = embeds(html, page)
        var inspected = 0
        for (embed in linked.take(3)) {
            inspected++
            if (media(embed)) return CinemaVideoScan(
                embed, "прямая ссылка в iframe", linked.size, inspected)
            val nested = runCatching { fetch(embed) }.getOrNull() ?: continue
            val result = scan(nested, embed)
            if (result.mediaUrl != null) return CinemaVideoScan(
                result.mediaUrl, "iframe: " + result.method, linked.size, inspected)
        }
        return initial.copy(inspected = inspected)
    }
}
