package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaEpisodeAuditTest {
    private fun video(number: Int) = CinemaTitle(
        id = "id-" + number, sourceId = "rutube",
        title = "American Horror Story, 1 сезон, " + number + " серия",
        kind = CinemaKind.SERIES, year = null, description = "",
        poster = null, genre = "Сериал", streamUrl = null,
        episodes = emptyList(), attribution = "RUTUBE",
        seasonNumber = 1, episodeNumber = number
    )

    @Test fun fullReferenceGuideCalculatesMissingFifthEpisode() {
        val guide = CinemaEpisodeAudit.parseEpisodes(10, "American Horror Story", """
            [
                {"season":1,"number":1,"name":"One"},
                {"season":1,"number":2,"name":"Two"},
                {"season":1,"number":3,"name":"Three"},
                {"season":1,"number":4,"name":"Four"},
                {"season":1,"number":5,"name":"Five"},
                {"season":1,"number":6,"name":"Six"},
                {"season":2,"number":1,"name":"New season"},
                {"season":0,"number":1,"name":"Special"},
                {"season":1,"number":null,"name":"Unknown"}
            ]
        """.trimIndent())
        assertEquals(listOf(1, 2), guide.seasons())
        val coverage = CinemaEpisodeAudit.coverage(guide, 1,
            listOf(video(1), video(2), video(3), video(4), video(6)))
        assertTrue(coverage.verifiedGuide)
        assertEquals(6, coverage.expectedCount)
        assertEquals(5, coverage.foundCount)
        assertEquals(listOf(5), coverage.missing)
    }

    @Test fun withoutReferenceGuideGapsAreInferredNotCertified() {
        val coverage = CinemaEpisodeAudit.coverage(null, 1,
            listOf(video(1), video(2), video(4)))
        assertFalse(coverage.verifiedGuide)
        assertEquals(4, coverage.expectedCount)
        assertEquals(listOf(3), coverage.missing)
    }

    @Test fun spinOffCannotMatchOriginalSeriesName() {
        val response = """
          [{"show":{"id":41,"name":"American Horror Stories"}},
           {"show":{"id":42,"name":"American Horror Story"}}]
        """.trimIndent()
        assertEquals(42, CinemaEpisodeAudit.matchShowId(
            response, "American Horror Story")?.first)
        assertEquals(41, CinemaEpisodeAudit.matchShowId(
            response, "American Horror Stories")?.first)
        assertNull(CinemaEpisodeAudit.matchShowId(
            response, "American Horror Story Deluxe"))
        assertEquals(listOf("American Horror Stories"),
            CinemaEpisodeAudit.englishHints(
                "Американские истории ужасов",
                "Сериал: Американские истории ужасов — 1 сезон 5 серия / American Horror Stories"
            ))
    }
}
