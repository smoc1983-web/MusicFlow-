package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class DownloadSpeedTest {
    @Test fun computesNonNegativeBytesPerSecond() {
        val start = DownloadSpeed.next(null, 1024, 1_000)
        assertEquals(0L, start.bytesPerSecond)
        assertEquals(2048L, DownloadSpeed.next(start, 5120, 3_000).bytesPerSecond)
    }

    @Test fun resetsOnBackwardClockOrRestartedDownload() {
        val start = DownloadSpeedSample(10000L, 3000L, 50L)
        assertEquals(0L, DownloadSpeed.next(start, 10L, 4000L).bytesPerSecond)
        assertEquals(0L, DownloadSpeed.next(start, 20000L, 2000L).bytesPerSecond)
    }
}
