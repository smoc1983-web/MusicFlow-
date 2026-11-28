package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class OfflineMp4ProgressPolicyTest {
    @Test fun reportsExactSavingProgressWithoutInventedTotals() {
        assertEquals(0, OfflineMp4ProgressPolicy.savingPercent(0, 1_000))
        assertEquals(25, OfflineMp4ProgressPolicy.savingPercent(250, 1_000))
        assertEquals(100, OfflineMp4ProgressPolicy.savingPercent(1_000, 1_000))
        assertEquals(100, OfflineMp4ProgressPolicy.savingPercent(2_000, 1_000))
        assertNull(OfflineMp4ProgressPolicy.savingPercent(50, 0))
        assertNull(OfflineMp4ProgressPolicy.savingPercent(-1, 1_000))
    }

    @Test fun exportPhaseAndBytesAreSeparateFromTheOriginalOfflineDownload() {
        val transforming = OfflineMp4ExportProgress(
            OfflineMp4ExportPhase.TRANSFORMING, null, 1_000_000)
        assertNull(transforming.percent)
        assertEquals(1_000_000L, transforming.processedBytes)
        val saving = OfflineMp4ExportProgress(
            OfflineMp4ExportPhase.SAVING, 50, 500_000L, 1_000_000L)
        assertEquals(50, saving.percent)
        assertEquals(1_000_000L, saving.totalBytes)
    }
}
