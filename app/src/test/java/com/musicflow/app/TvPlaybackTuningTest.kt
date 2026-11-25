package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Policy-level regression tests for the slow HLS TV streams in look.m3u8. */
class TvPlaybackTuningTest {
    @Test fun bufferingIsLargerThanTheOldDefaultAndConstrainedInMemory() {
        assertEquals(25_000, TvPlaybackTuning.MIN_BUFFER_MS)
        assertEquals(75_000, TvPlaybackTuning.MAX_BUFFER_MS)
        assertEquals(6_000, TvPlaybackTuning.START_BUFFER_MS)
        assertEquals(12_000, TvPlaybackTuning.REBUFFER_MS)
        assertEquals(64 * 1024 * 1024, TvPlaybackTuning.TARGET_BUFFER_BYTES)
        assertTrue(TvPlaybackTuning.MIN_BUFFER_MS <= TvPlaybackTuning.MAX_BUFFER_MS)
        assertTrue(TvPlaybackTuning.START_BUFFER_MS < TvPlaybackTuning.MIN_BUFFER_MS)
        assertTrue(TvPlaybackTuning.REBUFFER_MS < TvPlaybackTuning.MIN_BUFFER_MS)
    }

    @Test fun networkTimeoutPrecedesBufferingWatchdogAndRecoveryIsNotTooEager() {
        assertEquals(20_000, TvPlaybackTuning.CONNECT_TIMEOUT_MS)
        assertEquals(45_000, TvPlaybackTuning.READ_TIMEOUT_MS)
        assertEquals(65_000L, TvStreamRetryPolicy.BUFFERING_TIMEOUT_MS)
        assertEquals(30_000L, TvStreamRetryPolicy.STALL_TIMEOUT_MS)
        assertTrue(TvStreamRetryPolicy.BUFFERING_TIMEOUT_MS >
            TvPlaybackTuning.READ_TIMEOUT_MS)
        assertTrue(TvStreamRetryPolicy.BUFFERING_TIMEOUT_MS >
            TvPlaybackTuning.REBUFFER_MS * 4L)
    }

    @Test fun cannotInfiniteRetryPermanentHttpFailures() {
        assertFalse(TvStreamRetryPolicy.retryHttpStatus(403))
        assertFalse(TvStreamRetryPolicy.retryHttpStatus(404))
        assertTrue(TvStreamRetryPolicy.retryHttpStatus(408))
        assertTrue(TvStreamRetryPolicy.retryHttpStatus(503))
        assertTrue(TvStreamRetryPolicy.canRetry("ruvideo:test", 1, true))
        assertFalse(TvStreamRetryPolicy.canRetry("ruvideo:test", 2, true))
        assertFalse(TvStreamRetryPolicy.canRetry("radio:test", 0, true))
    }

    @Test fun startupIsFasterThanFullBufferWithoutAggressiveRestarts() {
        assertTrue(TvPlaybackTuning.START_BUFFER_MS < 10_000)
        assertTrue(TvPlaybackTuning.REBUFFER_MS >= 2 * TvPlaybackTuning.START_BUFFER_MS)
        assertTrue(TvStreamRetryPolicy.STALL_TIMEOUT_MS >
            TvPlaybackTuning.REBUFFER_MS)
    }
}
