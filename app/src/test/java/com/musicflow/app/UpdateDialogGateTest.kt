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

    @Test fun laterExpiresAndDoesNotHideNewerRelease() {
        val now = 100_000_000L
        val until = now + UpdateDialogGate.SNOOZE_INTERVAL_MS
        assertTrue(UpdateDialogGate.snoozed(764,764,until,now))
        assertFalse(UpdateDialogGate.snoozed(764,764,until,until))
        assertFalse(UpdateDialogGate.snoozed(765,764,until,now))
    }

    @Test fun networkCheckThrottlesOnlyAfterSuccessAndHandlesClockReset() {
        val now = 100_000_000L
        assertTrue(UpdateDialogGate.dueForCheck(now,0))
        assertFalse(UpdateDialogGate.dueForCheck(now+1,now))
        assertTrue(UpdateDialogGate.dueForCheck(now+UpdateDialogGate.CHECK_INTERVAL_MS,now))
        assertTrue(UpdateDialogGate.dueForCheck(now-1,now))
    }

    @Test fun aNewerReleaseAfterDismissedOldOneAppears() {
        assertTrue(UpdateDialogGate.visible(UpdateDialogGate.State(744,746,745,false)))
    }
}
