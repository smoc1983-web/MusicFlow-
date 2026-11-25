package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class ListeningProgressTrackerTest {
    @Test fun browsingTrackWithoutPlayingDoesNotCreateHistory() {
        val tracker = ListeningProgressTracker()
        assertNull(tracker.sample("zaycev:42", 1_000L, 0L, false))
        assertNull(tracker.sample("zaycev:42", 6_000L, 0L, false))
    }

    @Test fun genuinePlaybackCreatesOneHistoryEntryAndAccruesTime() {
        val tracker = ListeningProgressTracker()
        assertNull(tracker.sample("zaycev:42", 1_000L, 1_000L, true))
        val first = tracker.sample("zaycev:42", 3_000L, 3_000L, true)!!
        assertEquals(2_000L, first.listenedMs)
        assertFalse(first.qualifiedStart)
        val second = tracker.sample("zaycev:42", 5_000L, 5_000L, true)!!
        assertEquals(2_000L, second.listenedMs)
        assertTrue(second.qualifiedStart)
        assertFalse(tracker.sample("zaycev:42", 7_000L, 7_000L, true)!!.qualifiedStart)
    }

    @Test fun seekingAndLargeTimeGapsDoNotInflateStats() {
        val tracker = ListeningProgressTracker()
        tracker.sample("zaycev:42", 1_000L, 1_000L, true)
        assertNull(tracker.sample("zaycev:42", 2_000L, 80_000L, true))
        assertEquals(1_000L, tracker.sample("zaycev:42", 3_000L, 81_000L, true)!!.listenedMs)
        assertNull(tracker.sample("zaycev:42", 50_000L, 120_000L, true))
    }

    @Test fun pauseDoesNotCountTimeAndResumeRequiresFreshBaseline() {
        val tracker = ListeningProgressTracker()
        tracker.sample("zaycev:42", 1_000L, 1_000L, true)
        assertNull(tracker.sample("zaycev:42", 6_000L, 1_000L, false))
        assertNull(tracker.sample("zaycev:42", 12_000L, 1_000L, true))
        assertEquals(1_000L, tracker.sample("zaycev:42", 13_000L, 2_000L, true)!!.listenedMs)
    }

    @Test fun switchingTracksNeverTransfersListeningTime() {
        val tracker = ListeningProgressTracker()
        tracker.sample("zaycev:42", 1_000L, 0L, true)
        assertNull(tracker.sample("muzofond:12", 2_000L, 0L, true))
        assertEquals(1_000L, tracker.sample("muzofond:12", 3_000L, 1_000L, true)!!.listenedMs)
    }

    @Test fun radioCanAccrueTimeWithoutSeekPosition() {
        val tracker = ListeningProgressTracker()
        tracker.sample("radio:station", 1_000L, 0L, true)
        assertEquals(4_000L, tracker.sample("radio:station", 5_000L, 0L, true)!!.listenedMs)
    }

    @Test fun televisionAndUnboundedPlaybackCannotBeCountedAsMusic() {
        val tracker = ListeningProgressTracker()
        assertNull(tracker.sample("ruvideo:channel", 100L, 0L, true))
        assertNull(tracker.sample("ruvideo:channel", 10_000L, 0L, true))
        assertNull(tracker.sample("vk:legacy", 10_100L, 0L, true))
    }
}
