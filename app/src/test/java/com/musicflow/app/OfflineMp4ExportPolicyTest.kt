package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class OfflineMp4ExportPolicyTest {
    @Test fun onlyCompletedNonDrmHlsCanBeExportedOnModernAndroid() {
        assertTrue(OfflineMp4ExportPolicy.allowed(true, true, false, 35))
        assertFalse(OfflineMp4ExportPolicy.allowed(false, true, false, 35))
        assertFalse(OfflineMp4ExportPolicy.allowed(true, false, false, 35))
        assertFalse(OfflineMp4ExportPolicy.allowed(true, true, true, 35))
        assertFalse(OfflineMp4ExportPolicy.allowed(true, true, false, 28))
    }
}
