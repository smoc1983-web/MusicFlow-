package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class ResumePositionPolicyTest {
    @Test fun unknownDurationMayResumeWhenSourceWasSeekable() {
        assertEquals(75_000L, ResumePositionPolicy.resumePosition("muzofond:9", 75_000L, 0L, true))
        assertEquals(75_000L, ResumePositionPolicy.resumePosition("zaycev:3", 75_000L, -1L, true))
    }

    @Test fun unknownDurationMustNeverSeekUnverifiedLiveSources() {
        assertEquals(0L, ResumePositionPolicy.resumePosition("muzofond:9", 75_000L, 0L, false))
        assertEquals(0L, ResumePositionPolicy.resumePosition("radio:station", 75_000L, 0L, true))
        assertEquals(0L, ResumePositionPolicy.resumePosition("ruvideo:channel", 75_000L, 0L, true))
        assertEquals(0L, ResumePositionPolicy.resumePosition("vk:legacy", 75_000L, 0L, true))
    }

    @Test fun knownDurationDiscardsNearEndOrInvalidResume() {
        assertEquals(0L, ResumePositionPolicy.resumePosition("zaycev:3", 4_000L, 300_000L, true))
        assertEquals(0L, ResumePositionPolicy.resumePosition("zaycev:3", 296_000L, 300_000L, true))
        assertEquals(90_000L, ResumePositionPolicy.resumePosition("zaycev:3", 90_000L, 300_000L, true))
        assertEquals(0L, ResumePositionPolicy.resumePosition("zaycev:3", 90_000_000L, 0L, true))
    }

    @Test fun pendingSeekRequiresSameTrackAndReadySeekableTimeline() {
        assertFalse(ResumePositionPolicy.canApplyAfterPrepare("song:1", "song:1", false, true))
        assertFalse(ResumePositionPolicy.canApplyAfterPrepare("song:1", "song:1", true, false))
        assertFalse(ResumePositionPolicy.canApplyAfterPrepare("song:1", "song:2", true, true))
        assertTrue(ResumePositionPolicy.canApplyAfterPrepare("song:1", "song:1", true, true))
    }
}
