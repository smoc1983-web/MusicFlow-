package com.musicflow.app

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvStreamRecoveryTest {
    @Test fun retriesOnlyLiveChannelAndOnlyTwice() {
        assertTrue(TvStreamRetryPolicy.canRetry("ruvideo:123", 0, true))
        assertTrue(TvStreamRetryPolicy.canRetry("ruvideo:123", 1, true))
        assertFalse(TvStreamRetryPolicy.canRetry("ruvideo:123", 2, true))
        assertFalse(TvStreamRetryPolicy.canRetry("music:123", 0, true))
        assertFalse(TvStreamRetryPolicy.canRetry("ruvideo:123", 0, false))
    }

    @Test fun permanentHttp4xxNeverLoops() {
        assertFalse(TvStreamRetryPolicy.retryHttpStatus(401))
        assertFalse(TvStreamRetryPolicy.retryHttpStatus(403))
        assertFalse(TvStreamRetryPolicy.retryHttpStatus(404))
        assertFalse(TvStreamRetryPolicy.retryHttpStatus(451))
        assertTrue(TvStreamRetryPolicy.retryHttpStatus(408))
        assertTrue(TvStreamRetryPolicy.retryHttpStatus(429))
        assertTrue(TvStreamRetryPolicy.retryHttpStatus(502))
        assertTrue(TvStreamRetryPolicy.retryHttpStatus(503))
    }

    @Test fun transientNetworkErrorsOnly() {
        assertTrue(TvStreamRetryPolicy.retryError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null))
        assertTrue(TvStreamRetryPolicy.retryError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
        assertFalse(TvStreamRetryPolicy.retryError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, 403))
        assertFalse(TvStreamRetryPolicy.retryError(
            PlaybackException.ERROR_CODE_DECODING_FAILED, null))
    }
}
