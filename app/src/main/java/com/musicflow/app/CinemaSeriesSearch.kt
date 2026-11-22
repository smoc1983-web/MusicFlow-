package com.musicflow.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal data class CinemaSeriesLookup(
    val show: CinemaTvShow?,
    val episodes: List<CinemaTitle>,
    val completeSeasons: Boolean,
    val message: String
)

/**
 * Finds a show from any episode through its public TV-show relationship.
 * When no relationship exists, searches available public catalogue shelves
 * and clearly describes that those results can be incomplete.
 */
internal object CinemaSeriesSearch {
    private val episodeHint = Regex(
        """(?iu)(?:\bсезон\s*\d{1,2}|\b\d{1,2}\s*сезон|\bсерия\s*\d{1,3}|\b\d{1,3}\s*серия|\bs\d{1,2}e\d{1,3}\b)"""
    )
    private val seasonRules = listOf(
        Regex("""(?iu)\b(\d{1,2})\s*сезон"""),
        Regex("""(?iu)\bсезон\s*(\d{1,2})"""),
        Regex("""(?iu)\bs(\d{1,2})e\d{1,3}\b""")
    )
    private val episodeRules = listOf(
        Regex("""(?iu)\b(\d{1,3})\s*(?:я|ая)?\s*серия"""),
        Regex("""(?iu)\bсерия\s*(\d{1,3})"""),
        Regex("""(?iu)\bs\d{1,2}e(\d{1,3})\b""")
    )

    fun family(raw: String): String =
        CinemaSeriesGrouping.titleFor(raw).trim().lowercase()
            .replace(Regex("""(?iu)^сериал\\s*[:—-]?\\s+"""), "")
            .trim()

    fun looksLikeEpisode(item: CinemaTitle): Boolean =
        item.kind == CinemaKind.SERIES ||
            item.seasonNumber != null ||
            item.episodeNumber != null ||
            episodeHint.containsMatchIn(item.title)

    fun enrich(item: CinemaTitle): CinemaTitle {
        if (!looksLikeEpisode(item)) return item
        fun number(rules: List<Regex>): Int? =
            rules.asSequence().mapNotNull {
                it.find(item.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }.firstOrNull { it > 0 }
        return item.copy(
            kind = CinemaKind.SERIES,
            seasonNumber = item.seasonNumber ?: number(seasonRules),
            episodeNumber = item.episodeNumber ?: number(episodeRules)
        )
    }

    fun matches(candidate: CinemaTitle, sample: CinemaTitle): Boolean {
        val candidateRich = enrich(candidate)
        return family(sample.title).length >= 3 &&
            looksLikeEpisode(candidateRich) &&
            candidate.sourceId == sample.sourceId &&
            family(candidate.title) == family(sample.title)
    }

    fun organize(items: List<CinemaTitle>): List<CinemaTitle> =
        items.asSequence().map(::enrich).distinctBy { it.key }
            .sortedWith(compareBy<CinemaTitle> { it.seasonNumber ?: 1 }
                .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }).toList()

    /**
     * Augment a canonical TV season with individually searchable public uploads.
     * Exact normalized title and optional TV ID guards prevent joining spin-offs.
     */
    fun mergeSeasonCandidates(
        showName: String,
        showId: Int,
        season: Int,
        sourceId: String,
        official: List<CinemaTitle>,
        discovered: List<CinemaTitle>
    ): List<CinemaTitle> {
        val identity = family(showName)
        val native = official.map(::enrich).filter {
            it.sourceId == sourceId && (it.seasonNumber ?: season) == season
        }
        val extras = discovered.map(::enrich).filter {
            it.sourceId == sourceId &&
                it.seasonNumber == season &&
                it.episodeNumber != null &&
                identity.length >= 3 &&
                family(it.title) == identity &&
                (showId <= 0 || it.tvShowId == null || it.tvShowId == showId)
        }
        return organize(native + extras)
    }

    /** Public search results supplement — not replace — the show's official season API. */
    suspend fun discoverSeason(
        show: CinemaTvShow,
        season: Int,
        sourceId: String,
        shelves: List<CinemaRutubeShelf>,
        cached: List<CinemaTitle>
    ): List<CinemaTitle> {
        if (family(show.name).length < 3) return emptyList()
        val queries = listOf(
            show.name,
            show.name + " " + season + " сезон"
        ).distinct().map { it.take(90) }
        val remote = withTimeoutOrNull(18_000) {
            coroutineScope {
                queries.map { request ->
                    async(Dispatchers.IO) {
                        try {
                            CinemaRutubeCatalog.search(request, sourceId, shelves)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }.orEmpty()
        return mergeSeasonCandidates(
            show.name, show.id, season, sourceId,
            emptyList(), cached + remote
        )
    }

    /** Tries all declared seasons by TV show, or a bounded cross-catalogue search. */
    suspend fun lookup(
        selected: CinemaTitle,
        shelves: List<CinemaRutubeShelf>,
        cached: List<CinemaTitle>
    ): CinemaSeriesLookup {
        val episode = enrich(selected)
        val videoId = CinemaRutubeSource.videoId(episode.pageUrl.orEmpty())
        val show = if (videoId == null) null
            else CinemaRutubeSeries.loadShowFor(videoId)
        if (show != null) {
            return CinemaSeriesLookup(
                show = show, episodes = emptyList(), completeSeasons = true,
                message = "Сериал найден. Серии доступны по сезонам " +
                    "из опубликованного каталога."
            )
        }

        val name = family(episode.title)
        if (name.length < 3 || !looksLikeEpisode(episode)) {
            return CinemaSeriesLookup(
                null, emptyList(), false,
                "Для этого видео источник не предоставил данные о сезонах."
            )
        }
        val local = cached.filter { matches(it, episode) }
        val remote = withTimeoutOrNull(24_000) {
            val searched = try {
                CinemaRutubeCatalog.search(
                    name.take(80), episode.sourceId, shelves
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }

            val relevant = shelves.filter {
                it.section == "Главная" ||
                    it.section == "По жанрам" ||
                    it.title.contains("сериал", ignoreCase = true)
            }.distinctBy { it.url }.take(16)
            val additional = coroutineScope {
                relevant.map { shelf ->
                    async(Dispatchers.IO) {
                        try {
                            CinemaRutubeCatalog.loadShelf(
                                shelf, episode.sourceId
                            ).films
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
            (searched + additional).filter { matches(it, episode) }
        }.orEmpty()

        val episodes = organize(local + remote + episode)
        return CinemaSeriesLookup(
            null, episodes, false,
            if (episodes.size > 1)
                "Найдено серий: " + episodes.size +
                    ". Это результаты открытых подборок; " +
                    "другие сезоны могут быть недоступны."
            else
                "Источник не указал полный список сезонов. " +
                    "Найдена только выбранная серия."
        )
    }

    fun fallbackShow(result: CinemaSeriesLookup, item: CinemaTitle): CinemaTvShow? {
        if (result.show != null) return result.show
        if (result.episodes.isEmpty()) return null
        return CinemaTvShow(
            id = 0,
            name = CinemaSeriesGrouping.titleFor(item.title),
            description = result.message,
            seasons = (result.episodes.maxOfOrNull { it.seasonNumber ?: 1 } ?: 1)
                .coerceIn(1, 30),
            cover = item.poster
        )
    }

    fun inSeason(result: CinemaSeriesLookup, season: Int): List<CinemaTitle> =
        organize(result.episodes.filter { (it.seasonNumber ?: 1) == season })
}
