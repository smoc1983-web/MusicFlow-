package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

/** Accelerated 6h model, NOT a substitute for emulator/device playback. */
class BackgroundPlaybackSoakModelTest {
    @Test fun syntheticSixHourBackgroundSessionKeepsExactListeningTime() {
        val tracker = ListeningProgressTracker()
        val first = 1_000L
        var elapsed = first
        var position = 0L
        var count = 0
        var total = 0L
        assertNull(tracker.sample("radio:long", first, 0L, true))
        repeat(6 * 60 * 60 / 5) {
            elapsed += 5_000L
            position += 5_000L
            val sample = tracker.sample("radio:long", elapsed, position, true)
            total += sample?.listenedMs ?: 0L
            if (sample?.qualifiedStart == true) count++
        }
        assertEquals(21_600_000L, total)
        assertEquals(1, count)
    }

    @Test fun syntheticRecreationCanReuseUnknownLengthProgress() {
        val saved = ResumePositionPolicy.resumePosition(
            "zaycev:long", 7_200_000L, 0L, true)
        assertEquals(7_200_000L, saved)
        assertTrue(ResumePositionPolicy.canApplyAfterPrepare("zaycev:long", "zaycev:long", true, true))
    }
}
