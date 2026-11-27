package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class OfflineDownloadRouteTest {
    @Test fun hlsAndDashUseMedia3Cache() {
        assertEquals(OfflineTransfer.ADAPTIVE_HLS,
            OfflineDownloadRoute.classify("https://cdn.example.org/show/master.m3u8?token=x"))
        assertEquals(OfflineTransfer.ADAPTIVE_DASH,
            OfflineDownloadRoute.classify("https://cdn.example.org/show/manifest.mpd"))
    }

    @Test fun progressiveFilesRemainPublicDownloads() {
        for (ext in listOf("mp4", "mkv", "webm", "mov", "mp3", "flac", "m4a", "ts")) {
            assertEquals(ext, OfflineTransfer.DIRECT_FILE,
                OfflineDownloadRoute.classify("https://files.example.org/a.$ext"))
        }
    }

    @Test fun noPageOrNonHttpsLinkIsMistakenForDownloadedVideo() {
        for (uri in listOf("https://cdn.example.org/watch?id=1",
            "http://cdn.example.org/movie.mp4",
            "file:///sdcard/file.m3u8",
            "https://name:secret@cdn.example.org/master.mpd",
            "javascript:alert(1)")) {
            assertEquals(uri, OfflineTransfer.UNSUPPORTED, OfflineDownloadRoute.classify(uri))
        }
    }

    @Test fun cachedAdaptiveFilesAreNotShareableAsAStandaloneMp4() {
        assertFalse(OfflineDownloadRoute.canShareOfflineFile(OfflineTransfer.ADAPTIVE_HLS))
        assertFalse(OfflineDownloadRoute.canShareOfflineFile(OfflineTransfer.ADAPTIVE_DASH))
        assertTrue(OfflineDownloadRoute.canShareOfflineFile(OfflineTransfer.DIRECT_FILE))
    }

    @Test fun testChannelVersionPolicyUnchanged() {
        assertEquals(MusicFlowUpdates.UpdateChannel.TEST,
            MusicFlowUpdates.channelForPackage("com.musicflow.preview.dev"))
        assertEquals(MusicFlowUpdates.UpdateChannel.OFFICIAL,
            MusicFlowUpdates.channelForPackage("com.musicflow.preview"))
    }
}
