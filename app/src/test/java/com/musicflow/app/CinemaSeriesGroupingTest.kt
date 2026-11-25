package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaSeriesGroupingTest {
    private fun series(title: String, episode: Int, season: Int) = CinemaTitle(
        id = "e" + season + "-" + episode,
        sourceId = "cinema",
        title = title,
        kind = CinemaKind.SERIES,
        year = null,
        description = "",
        poster = null,
        genre = "Сериалы",
        streamUrl = null,
        episodes = emptyList(),
        attribution = "Каталог",
        seasonNumber = season,
        episodeNumber = episode
    )

    @Test fun seriesEpisodesBecomeOneShowAndAreSortedBySeasonAndEpisode() {
        val items = listOf(
            series("Интерны, 2 сезон, 3 серия", 3, 2),
            series("Интерны, 1 сезон, 2 серия", 2, 1),
            series("Интерны, 1 сезон, 1 серия", 1, 1),
            series("Бывшие, 1 сезон, 1 серия", 1, 1)
        )
        val groups = CinemaSeriesGrouping.group(items)
        assertEquals(2, groups.size)
        val interns = groups.first { it.title == "Интерны" }
        assertEquals(listOf(1, 2), interns.seasons)
        assertEquals(3, interns.episodesCount)
        assertEquals(listOf(1, 2), interns.episodesIn(1).map { it.episodeNumber })
        assertEquals(listOf(3), interns.episodesIn(2).map { it.episodeNumber })
    }

    @Test fun groupByShowIdEvenIfEpisodesTitlesDiffer() {
        val one = series("Команда: серия 1", 1, 1).copy(tvShowId = 884)
        val two = series("Команда. Сезон 2, серия 1", 1, 2).copy(tvShowId = 884)
        val groups = CinemaSeriesGrouping.group(listOf(one, two))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].episodesCount)
    }

    @Test fun moviesNeverBecomeSeriesAndKnownSeriesRemain() {
        val movie = series("Фильм, 1 сезон, 1 серия", 1, 1)
            .copy(kind = CinemaKind.MOVIE)
        val group = CinemaSeriesGrouping.group(listOf(
            movie, series("Тестовая история", 1, 1)
        ))
        assertEquals(1, group.size)
        assertEquals("Тестовая история", group.first().title)
        assertTrue(group.first().seasons.contains(1))
    }
}
