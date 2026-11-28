package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class DownloadSizeEstimateTest {
    @Test fun estimatesVODBytesFromDurationAndBandwidth() {
        assertEquals(90_000_000L, DownloadSizeEstimate.bytes(600.0, 1_200_000L))
        assertEquals("≈ 90,0 МБ", DownloadSizeEstimate.format(90_000_000L))
    }

    @Test fun refusesUnknownLiveAndOverflowDuration() {
        assertNull(DownloadSizeEstimate.bytes(null, 1_000_000))
        assertNull(DownloadSizeEstimate.bytes(0.0, 1_000_000))
        assertNull(DownloadSizeEstimate.bytes(0.0 / 0.0, 1_000_000))
        assertNull(DownloadSizeEstimate.bytes(900_000.0, 1_000_000))
        assertNull(DownloadSizeEstimate.bytes(60.0, null))
        assertNull(DownloadSizeEstimate.bytes(60.0, -20))
        assertEquals("размер неизвестен", DownloadSizeEstimate.format(null))
    }

    @Test fun distinguishesExactContentLengthFromStreamEstimate() {
        assertEquals("720p · ≈ 1,5 ГБ",
            DownloadSizeEstimate.choice("720p", 1_500_000_000L))
        assertEquals("24,0 МБ", DownloadSizeEstimate.format(24_000_000L, false))
    }
}
