package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaSeriesSearchTest {
    private fun episode(
        title: String,
        id: String,
        kind: CinemaKind = CinemaKind.MOVIE
    ): CinemaTitle = CinemaTitle(
        id = id, sourceId = "source", title = title,
        kind = kind, year = null, description = "Описание",
        poster = "https://example.org/poster.jpg",
        genre = "Сериал", streamUrl = null,
        episodes = emptyList(), attribution = "Каталог",
        pageUrl = "https://rutube.ru/video/11111111111111111111111111111111/"
    )

    @Test fun episodesClassifiedAsMoviesGetRecognizedAndNumbered() {
        val item = CinemaSeriesSearch.enrich(episode(
            "Интерны, 2 сезон, 13 серия", "s2e13"
        ))
        assertEquals(CinemaKind.SERIES, item.kind)
        assertEquals(2, item.seasonNumber)
        assertEquals(13, item.episodeNumber)
        val reversed = CinemaSeriesSearch.enrich(episode(
            "Интерны — серия 3, сезон 1", "s1e3"
        ))
        assertEquals(1, reversed.seasonNumber)
        assertEquals(3, reversed.episodeNumber)
        val compact = CinemaSeriesSearch.enrich(episode(
            "Интерны | S03E07", "s3e7"
        ))
        assertEquals(3, compact.seasonNumber)
        assertEquals(7, compact.episodeNumber)
    }

    @Test fun unrelatedMoviesNeverJoinSeries() {
        val film = episode("Сердце зверя (2026)", "film")
        val series = episode("Интерны, 1 сезон, 1 серия", "ep1")
        assertFalse(CinemaSeriesSearch.looksLikeEpisode(film))
        assertFalse(CinemaSeriesSearch.matches(film, series))
        assertTrue(CinemaSeriesSearch.looksLikeEpisode(series))
        assertFalse(CinemaSeriesSearch.matches(
            episode("Бывшие, 1 сезон, 1 серия", "other"), series
        ))
    }

    @Test fun episodeCardsAreDeduplicatedAndSortedSeasonThenNumber() {
        val cards = listOf(
            episode("Интерны, 2 сезон, 4 серия", "episode4"),
            episode("Интерны, 1 сезон, 9 серия", "episode9"),
            episode("Интерны, 1 сезон, 1 серия", "episode1"),
            episode("Интерны, 1 сезон, 1 серия", "episode1")
        )
        val organized = CinemaSeriesSearch.organize(cards)
        assertEquals(3, organized.size)
        assertEquals(listOf(1, 9, 4), organized.map { it.episodeNumber })
        assertEquals(listOf(1, 1, 2), organized.map { it.seasonNumber })
    }

    @Test fun partialSeriesResultNeverPromisesAllSeasons() {
        val first = CinemaSeriesSearch.enrich(
            episode("Интерны, 1 сезон, 1 серия", "a"))
        val next = CinemaSeriesSearch.enrich(
            episode("Интерны, 3 сезон, 2 серия", "b"))
        val lookup = CinemaSeriesLookup(null, listOf(next, first),
            false, "Найдены отдельные серии")
        val show = CinemaSeriesSearch.fallbackShow(lookup, first)
        assertEquals("Интерны", show?.name)
        assertEquals(3, show?.seasons)
        assertEquals(1, CinemaSeriesSearch.inSeason(lookup, 3).size)
        assertEquals("b", CinemaSeriesSearch.inSeason(lookup, 3)[0].id)
        assertNull(CinemaSeriesSearch.fallbackShow(
            CinemaSeriesLookup(null, emptyList(), false, ""), first))
    }

    @Test fun validShowRelationCanBeUsedForAnyEpisode() {
        val show = CinemaTvShow(123, "Название сериала", "",
            5, null)
        val lookup = CinemaSeriesLookup(show, emptyList(),
            true, "Найден сериал")
        assertEquals(123,
            CinemaSeriesSearch.fallbackShow(
                lookup, episode("Неопределённое видео", "some")
            )?.id)
    }
}
