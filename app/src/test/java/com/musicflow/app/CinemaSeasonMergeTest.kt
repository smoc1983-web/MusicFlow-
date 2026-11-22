package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: a searchable episode belongs to its own series, not its spin-off. */
class CinemaSeasonMergeTest {
    private fun episode(
        title: String, id: String, season: Int? = null,
        number: Int? = null, show: Int? = null
    ) = CinemaTitle(
        id = id, sourceId = "rutube", title = title,
        kind = CinemaKind.SERIES, year = null, description = "",
        poster = null, genre = "Сериал", streamUrl = null,
        episodes = emptyList(), attribution = "Каталог",
        seasonNumber = season, episodeNumber = number, tvShowId = show
    )

    @Test fun searchableMissingEpisodeJoinsCorrectSeasonOnly() {
        val official = listOf(1, 2, 3, 4, 6).map {
            episode("Американская история ужасов, 1 сезон, $it серия",
                "official-$it", 1, it, 111)
        }
        val discovered = listOf(
            episode("Сериал: Американская история ужасов – 1 сезон 5 серия",
                "missing-five"),
            episode("Сериал Американские истории ужасов – 1 сезон 5 серия / American Horror Stories",
                "different-series"),
            episode("Американская история ужасов, 2 сезон, 5 серия", "different-season"),
            episode("Американская история ужасов, 1 сезон, 5 серия", "different-show-id",
                1, 5, 222),
            official[0]
        )
        val merged = CinemaSeriesSearch.mergeSeasonCandidates(
            "Американская история ужасов", 111, 1, "rutube", official, discovered
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), merged.map { it.episodeNumber })
        assertTrue(merged.any { it.id == "missing-five" })
        assertFalse(merged.any { it.id == "different-series" })
        assertFalse(merged.any { it.id == "different-show-id" })
        assertEquals(6, merged.size)
    }

    @Test fun episodesWithAndWithoutTvIdFormOneSeriesGroup() {
        val known = episode("Сериал: Интерны, 1 сезон, 1 серия", "known", 1, 1, 44)
        val unknown = episode("Интерны, 1 сезон, 2 серия", "unknown", 1, 2)
        val unrelated = episode("Американские истории ужасов, 1 сезон, 1 серия",
            "other", 1, 1)
        val groups = CinemaSeriesGrouping.group(listOf(known, unknown, unrelated))
        assertEquals(2, groups.size)
        val interns = groups.first { it.title == "Интерны" }
        assertEquals(listOf(1, 2), interns.episodesIn(1).map { it.episodeNumber })
    }

    @Test fun seriesNameAndSpinOffStaySeparate() {
        assertFalse(CinemaSeriesSearch.family(
            "Американская история ужасов, 1 сезон, 5 серия"
        ) == CinemaSeriesSearch.family(
            "Американские истории ужасов – 1 сезон 5 серия / American Horror Stories"
        ))
    }

    @Test fun voiceLabelsNeverInventADubbedStudio() {
        assertEquals("Русский", CinemaAudioTrackLabels.display(null, "ru", 1))
        assertEquals("English", CinemaAudioTrackLabels.display(null, "en", 1))
        assertEquals("LostFilm", CinemaAudioTrackLabels.display("LostFilm", "ru", 1))
        assertEquals("Дорожка 2", CinemaAudioTrackLabels.display(null, null, 2))
    }
}
