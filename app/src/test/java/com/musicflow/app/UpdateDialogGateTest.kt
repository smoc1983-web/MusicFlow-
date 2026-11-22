package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class UpdateDialogGateTest {
    @Test fun tapClosesDialogBeforeDownloadManagerBegins() {
        val before = UpdateDialogGate.State(installedCode = 744L,
            candidateCode = 745L, ignoredCode = 0L, dismissed = false)
        assertTrue(UpdateDialogGate.visible(before))
        val after = UpdateDialogGate.onConfirm(before)
        assertFalse(UpdateDialogGate.visible(after))
        assertEquals(745L, after.ignoredCode)
        assertTrue(after.dismissed)
    }

    @Test fun alreadyInstalledAndFailedStaleDownloadCannotReopen() {
        assertFalse(UpdateDialogGate.visible(UpdateDialogGate.State(745,745,0,false)))
        assertFalse(UpdateDialogGate.visible(UpdateDialogGate.State(746,745,0,false)))
        assertFalse(UpdateDialogGate.visible(UpdateDialogGate.State(744,745,745,false)))
    }

    @Test fun aNewerReleaseAfterDismissedOldOneAppears() {
        assertTrue(UpdateDialogGate.visible(UpdateDialogGate.State(744,746,745,false)))
    }
}
