package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class CinemaDownloadQualityPolicyTest {
    @Test fun hlsVideoVariantsCanBeSelectedOnlyWithSafeAudio() {
        val choices = listOf(
            HlsQuality(0, "360p", false),
            HlsQuality(1, "720p", true),
            HlsQuality(2, "1080p", false)
        )
        assertEquals(0, CinemaDownloadQualityPolicy.hlsIndex(choices, 0))
        assertNull(CinemaDownloadQualityPolicy.hlsIndex(choices, 1))
        assertEquals(2, CinemaDownloadQualityPolicy.hlsIndex(choices, 2))
    }

    @Test fun missingOrInvalidSelectionFallsBackToAutomatic() {
        assertNull(CinemaDownloadQualityPolicy.hlsIndex(emptyList(), 0))
        assertNull(CinemaDownloadQualityPolicy.hlsIndex(listOf(HlsQuality(0, "720p", false)), 8))
        assertNull(CinemaDownloadQualityPolicy.hlsIndex(listOf(HlsQuality(0, "720p", false)), null))
    }

    @Test fun directMp4IsNotAdvertisedAsSelectableResolution() {
        assertEquals("Оригинальное качество файла", CinemaDownloadQualityPolicy.notice(
            OfflineTransfer.DIRECT_FILE
        ))
    }

    @Test fun adaptiveVideoShowsSourceSpecificQualityMessage() {
        assertTrue(CinemaDownloadQualityPolicy.notice(OfflineTransfer.ADAPTIVE_HLS).contains("HLS"))
        assertTrue(CinemaDownloadQualityPolicy.notice(OfflineTransfer.ADAPTIVE_DASH).contains("DASH"))
    }
}
