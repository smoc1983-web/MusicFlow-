package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class LyricsTimingTest {
    @Test fun displaysNothingBeforeFirstTimestamp() {
        assertEquals(-1, LyricsTiming.activeIndex(listOf(1000L, 2000L), 500L, 0))
    }

    @Test fun positiveOffsetShowsLyricsEarlier() {
        assertEquals(1, LyricsTiming.activeIndex(listOf(1000L, 2000L, 3000L), 1900L, 200))
    }

    @Test fun negativeOffsetDelaysLyrics() {
        assertEquals(0, LyricsTiming.activeIndex(listOf(1000L, 2000L), 2150L, -200))
    }

    @Test fun offsetsAreClampedToFiveSeconds() {
        assertEquals(5000, LyricsTiming.clamp(8000))
        assertEquals(-5000, LyricsTiming.clamp(-8000))
    }

    @Test fun emptyAndRepetitiveTimestampsAreStable() {
        assertEquals(-1, LyricsTiming.activeIndex(emptyList(), 5000L, 0))
        assertEquals(2, LyricsTiming.activeIndex(listOf(100L, 100L, 500L), 500L, 0))
    }
}
