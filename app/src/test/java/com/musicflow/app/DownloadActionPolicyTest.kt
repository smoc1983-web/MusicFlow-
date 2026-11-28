package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class DownloadActionPolicyTest {
    @Test fun activeMedia3AllowsPauseAndCancelNotResume() {
        assertEquals(
            setOf(DownloadUiAction.PAUSE, DownloadUiAction.CANCEL),
            DownloadActionPolicy.forMedia3(DownloadUiState.DOWNLOADING)
        )
        assertEquals(
            setOf(DownloadUiAction.PAUSE, DownloadUiAction.CANCEL),
            DownloadActionPolicy.forMedia3(DownloadUiState.QUEUED)
        )
    }

    @Test fun stoppedMedia3CanResumeFromCachedSegments() {
        assertEquals(
            setOf(DownloadUiAction.RESUME, DownloadUiAction.CANCEL),
            DownloadActionPolicy.forMedia3(DownloadUiState.STOPPED)
        )
    }

    @Test fun completedMedia3CanPlayAndDeleteButNotResume() {
        assertEquals(
            setOf(DownloadUiAction.PLAY, DownloadUiAction.DELETE),
            DownloadActionPolicy.forMedia3(DownloadUiState.COMPLETED)
        )
    }

    @Test fun failedMedia3CanRetryWithoutPretendingAlreadyFinished() {
        assertEquals(
            setOf(DownloadUiAction.RETRY, DownloadUiAction.CANCEL),
            DownloadActionPolicy.forMedia3(DownloadUiState.FAILED)
        )
    }

    @Test fun androidManagerCannotPretendToPauseOrResumeIndividualFiles() {
        assertEquals(
            setOf(DownloadUiAction.CANCEL),
            DownloadActionPolicy.forDirect(DownloadUiState.DOWNLOADING)
        )
        assertEquals(
            setOf(DownloadUiAction.OPEN_SYSTEM, DownloadUiAction.CANCEL),
            DownloadActionPolicy.forDirect(DownloadUiState.PAUSED_SYSTEM)
        )
        assertEquals(
            setOf(DownloadUiAction.RETRY, DownloadUiAction.CANCEL),
            DownloadActionPolicy.forDirect(DownloadUiState.FAILED)
        )
        assertEquals(
            setOf(DownloadUiAction.PLAY, DownloadUiAction.OPEN_EXTERNAL, DownloadUiAction.DELETE),
            DownloadActionPolicy.forDirect(DownloadUiState.COMPLETED)
        )
        assertFalse(DownloadActionPolicy.forDirect(DownloadUiState.QUEUED).contains(DownloadUiAction.PAUSE))
        assertFalse(DownloadActionPolicy.forDirect(DownloadUiState.PAUSED_SYSTEM).contains(DownloadUiAction.RESUME))
    }

    @Test fun media3ResumeReasonIsClearAndStopReasonNonZero() {
        assertTrue(DownloadActionPolicy.USER_PAUSED_STOP_REASON > 0)
        assertEquals(0, DownloadActionPolicy.RESUME_STOP_REASON)
    }
}
