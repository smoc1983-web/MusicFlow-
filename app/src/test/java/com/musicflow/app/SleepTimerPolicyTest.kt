package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class SleepTimerPolicyTest {
    @Test fun createsDeadlineFromValidDuration() {
        assertEquals(1_230_000L, SleepTimerPolicy.deadlineAfterMinutes(30_000L, 20))
    }

    @Test fun invalidDurationCannotStartTimer() {
        assertNull(SleepTimerPolicy.deadlineAfterMinutes(10_000L, 0))
        assertNull(SleepTimerPolicy.deadlineAfterMinutes(10_000L, -10))
        assertNull(SleepTimerPolicy.deadlineAfterMinutes(10_000L, 240))
    }

    @Test fun deadlineIsNotTriggeredBeforeTime() {
        assertFalse(SleepTimerPolicy.isExpired(61_000L, 60_999L))
        assertTrue(SleepTimerPolicy.isExpired(61_000L, 61_000L))
        assertEquals(0L, SleepTimerPolicy.remainingMs(61_000L, 61_001L))
    }

    @Test fun unsetDeadlineNeverTriggers() {
        assertFalse(SleepTimerPolicy.isExpired(null, Long.MAX_VALUE))
        assertEquals(0L, SleepTimerPolicy.remainingMs(null, 20_000L))
    }
}
