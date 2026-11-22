package com.musicflow.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

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
    val attribution: String,
    val pageUrl: String? = null,
    val playbackDiagnostic: String? = null,
    val tvShowId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
) {
    val key: String get() = sourceId + ":" + id
    val hasPlayback: Boolean get() = streamUrl != null || episodes.isNotEmpty()
}

/** Shared models, URL validation and local data. No plugin or demo implementations. */
internal object CinemaLabSources {
    private const val PREFS = "musicflow_cinema_user_urls_v4"

    fun isSafeHttps(value: String): Boolean {
        if (value.isBlank() || value.length > 2048 ||
            value.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null ||
            uri.fragment != null || uri.port !in listOf(-1, 443)) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host.length < 4 || !host.contains('.') ||
            host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".test") || host.endsWith(".invalid") ||
            host.contains(':') || host.all { it.isDigit() || it == '.' } ||
            host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("169.254.") ||
            host.startsWith("0.")) return false
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return false
        }
        return true
    }

    fun parseCatalog(raw: String, sourceId: String): List<CinemaTitle> {
        val array = JSONObject(raw).optJSONArray("items")
            ?: throw IllegalArgumentException("JSON-каталог должен содержать items.")
        val result = mutableListOf<CinemaTitle>()
        for (i in 0 until minOf(array.length(), 150)) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id").trim().take(100)
            val name = o.optString("title").trim().take(160)
            val kind = when (o.optString("type").lowercase()) {
                "movie", "film" -> CinemaKind.MOVIE
                "series", "show" -> CinemaKind.SERIES
                else -> continue
            }
            if (id.isEmpty() || name.isEmpty()) continue
            val poster = o.optString("poster").takeIf(::isSafeHttps)
            val video = o.optString("stream_url").takeIf(::isSafeHttps)
            val episodes = mutableListOf<CinemaEpisode>()
            val parts = o.optJSONArray("episodes") ?: JSONArray()
            for (j in 0 until minOf(parts.length(), 60)) {
                val episode = parts.optJSONObject(j) ?: continue
                val url = episode.optString("stream_url")
                if (!isSafeHttps(url)) continue
                episodes.add(CinemaEpisode(
                    episode.optString("id").ifBlank { (j + 1).toString() }.take(64),
                    episode.optString("title").ifBlank { "Серия " + (j + 1) }.take(130),
                    url
                ))
            }
            val year = o.optInt("year", 0).takeIf { it in 1880..2100 }
            val description = Jsoup.parse(o.optString("description")).text().take(800)
            result.add(CinemaTitle(
                id, sourceId, name, kind, year, description, poster,
                o.optString("genre").take(90), video, episodes,
                o.optString("attribution").take(200)
            ))
        }
        return result.distinctBy { it.key }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favoriteKeys(context: Context): Set<String> =
        prefs(context).getStringSet("favorites", emptySet())?.toSet().orEmpty()

    fun saveFavorites(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet("favorites", keys.toSet()).apply()
    }

    fun recentKeys(context: Context): List<String> =
        prefs(context).getString("recent", "").orEmpty()
            .split('|').filter { it.isNotBlank() }.take(25)

    fun markRecent(context: Context, key: String) {
        val updated = (listOf(key) + recentKeys(context)).distinct().take(25)
        prefs(context).edit().putString("recent", updated.joinToString("|")).apply()
    }

    fun progress(context: Context, key: String): Long =
        prefs(context).getLong("position-" + key.hashCode(), 0)

    fun saveProgress(context: Context, key: String, position: Long) {
        prefs(context).edit().putLong(
            "position-" + key.hashCode(), position.coerceAtLeast(0)
        ).apply()
    }
}
