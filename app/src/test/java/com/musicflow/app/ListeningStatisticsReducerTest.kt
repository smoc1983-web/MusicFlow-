package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class ListeningStatisticsReducerTest {
    @Test fun repeatedSamplesAddTimeButOnlyOnePlayPerQualifiedSession() {
        val original = emptyList<TrackListeningSummary>()
        val a = ListeningStatisticsReducer.record(original, "zaycev:42", "Песня", "Артист", null, 2_000L, false, 1_000L)
        assertEquals(2_000L, a.single().listenedMs)
        assertEquals(0, a.single().playCount)
        val b = ListeningStatisticsReducer.record(a, "zaycev:42", "Песня", "Артист", null, 3_000L, true, 2_000L)
        assertEquals(5_000L, b.single().listenedMs)
        assertEquals(1, b.single().playCount)
        val c = ListeningStatisticsReducer.record(b, "zaycev:42", "Песня", "Артист", null, 1_000L, false, 3_000L)
        assertEquals(6_000L, c.single().listenedMs)
        assertEquals(1, c.single().playCount)
    }

    @Test fun distinctTracksRemainSeparateAndSortedByTime() {
        val first = ListeningStatisticsReducer.record(emptyList(), "a", "A", "AA", null, 3_000L, true, 1L)
        val second = ListeningStatisticsReducer.record(first, "b", "B", "BB", null, 10_000L, true, 2L)
        assertEquals(listOf("b", "a"), second.map { it.id })
    }

    @Test fun invalidDataCannotInflateCounters() {
        val first = ListeningStatisticsReducer.record(emptyList(), "", "A", "AA", null, 500L, true, 1L)
        assertTrue(first.isEmpty())
        val second = ListeningStatisticsReducer.record(emptyList(), "a", "A", "AA", null, -500L, false, 1L)
        assertTrue(second.isEmpty())
    }
}
