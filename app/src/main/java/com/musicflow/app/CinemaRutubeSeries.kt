package com.musicflow.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

internal data class CinemaTvShow(
    val id: Int,
    val name: String,
    val description: String,
    val seasons: Int,
    val cover: String?
)

/** Public series metadata and season/episode pagination; no private subscription APIs. */
internal object CinemaRutubeSeries {
    fun showIdFromRelation(raw: String): Int? =
        JSONObject(raw).optJSONObject("tv")?.optInt("id", 0)?.takeIf { it > 0 }

    fun parseShow(raw: String): CinemaTvShow {
        val obj = JSONObject(raw)
        return CinemaTvShow(
            id = obj.optInt("id"),
            name = obj.optString("name").take(140),
            description = Jsoup.parse(obj.optString("description")).text().take(1000),
            seasons = obj.optInt("seasons_count").coerceIn(1, 30),
            cover = obj.optString("vertical_poster_url")
                .takeIf(CinemaLabSources::isSafeHttps)
                ?: obj.optString("poster_url").takeIf(CinemaLabSources::isSafeHttps)
        )
    }

    fun parseEpisodes(raw: String, sourceId: String): List<CinemaTitle> {
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("results") ?: return emptyList()
        val result = mutableListOf<CinemaTitle>()
        for (i in 0 until minOf(100, arr.length())) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (!id.matches(Regex("[a-fA-F0-9]{32}"))) continue
            if (item.optBoolean("is_paid") || item.optBoolean("is_hidden")) continue
            val season = item.optInt("season", 0)
            val episode = item.optInt("episode", 0).takeIf { it > 0 }
                ?: item.optInt("series", 0)
            result.add(CinemaSeriesSearch.enrich(CinemaTitle(
                id = id, sourceId = sourceId,
                title = item.optString("title").ifBlank {
                    "Серия " + if (episode > 0) episode else i + 1
                }.take(150),
                kind = CinemaKind.SERIES,
                year = null,
                description = Jsoup.parse(item.optString("description")).text().take(400),
                poster = item.optString("thumbnail_url")
                    .takeIf(CinemaLabSources::isSafeHttps),
                genre = "Сериал",
                streamUrl = null,
                episodes = emptyList(),
                attribution = "Кино",
                pageUrl = "https://rutube.ru/video/$id/",
                seasonNumber = season.takeIf { it > 0 },
                episodeNumber = episode.takeIf { it > 0 },
                tvShowId = item.optInt("tv_show_id", 0).takeIf { it > 0 }
            )))
        }
        return result.distinctBy { it.id }.sortedWith(
            compareBy<CinemaTitle> { it.episodeNumber ?: Int.MAX_VALUE }
                .thenBy { it.title }
        )
    }

    suspend fun loadShowFor(videoId: String): CinemaTvShow? =
        withContext(Dispatchers.IO) {
            if (!videoId.matches(Regex("[a-fA-F0-9]{32}"))) return@withContext null
            val relation = runCatching {
                CinemaRutubeCatalog.get(
                    "https://rutube.ru/api/metainfo/contenttvs/$videoId"
                )
            }.getOrNull() ?: return@withContext null
            val showId = showIdFromRelation(relation) ?: return@withContext null
            runCatching {
                parseShow(CinemaRutubeCatalog.get(
                    "https://rutube.ru/api/metainfo/tv/$showId/"
                ))
            }.getOrNull()
        }

    suspend fun loadEpisodes(
        showId: Int, season: Int, sourceId: String
    ): List<CinemaTitle> = withContext(Dispatchers.IO) {
        require(showId > 0 && season in 1..30)
        val base = "https://rutube.ru/api/metainfo/tv/$showId/video?season=$season&sort=series_a"
        val found = mutableListOf<CinemaTitle>()
        var next: String? = base
        var calls = 0
        val visited = HashSet<String>()
        while (next != null && calls < 16 && found.size < 1200 &&
            visited.add(next)) {
            calls++
            val data = CinemaRutubeCatalog.get(next)
            found.addAll(parseEpisodes(data, sourceId)
                .filter { it.seasonNumber == null || it.seasonNumber == season }
                .map { it.copy(
                    seasonNumber = it.seasonNumber ?: season,
                    tvShowId = it.tvShowId ?: showId
                ) })
            next = JSONObject(data).optString("next")
                .takeIf(CinemaRutubeCatalog::permittedApi)
        }
        found.distinctBy { it.id }.sortedWith(
            compareBy<CinemaTitle> { it.episodeNumber ?: Int.MAX_VALUE }
                .thenBy { it.title }
        )
    }
}
