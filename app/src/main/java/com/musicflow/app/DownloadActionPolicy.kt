package com.musicflow.app

/** Deterministic UI policy for two different Android download engines. */
internal enum class DownloadUiState {
    QUEUED, DOWNLOADING, STOPPED, PAUSED_SYSTEM, FAILED, COMPLETED, REMOVING
}

internal enum class DownloadUiAction {
    PAUSE, RESUME, CANCEL, RETRY, OPEN_SYSTEM, PLAY, OPEN_EXTERNAL, DELETE
}

internal object DownloadActionPolicy {
    // Positive app-defined Media3 reason. Clearing it (0) resumes cached HLS/DASH chunks.
    const val USER_PAUSED_STOP_REASON = 1
    const val RESUME_STOP_REASON = 0

    fun forMedia3(state: DownloadUiState): Set<DownloadUiAction> = when (state) {
        DownloadUiState.QUEUED, DownloadUiState.DOWNLOADING ->
            setOf(DownloadUiAction.PAUSE, DownloadUiAction.CANCEL)
        DownloadUiState.STOPPED ->
            setOf(DownloadUiAction.RESUME, DownloadUiAction.CANCEL)
        DownloadUiState.FAILED ->
            setOf(DownloadUiAction.RETRY, DownloadUiAction.CANCEL)
        DownloadUiState.COMPLETED ->
            setOf(DownloadUiAction.PLAY, DownloadUiAction.DELETE)
        else -> emptySet()
    }

    /** Public DownloadManager does not expose pause/resume of a single download. */
    fun forDirect(state: DownloadUiState): Set<DownloadUiAction> = when (state) {
        DownloadUiState.QUEUED, DownloadUiState.DOWNLOADING ->
            setOf(DownloadUiAction.CANCEL)
        DownloadUiState.PAUSED_SYSTEM ->
            setOf(DownloadUiAction.OPEN_SYSTEM, DownloadUiAction.CANCEL)
        DownloadUiState.FAILED ->
            setOf(DownloadUiAction.RETRY, DownloadUiAction.CANCEL)
        DownloadUiState.COMPLETED ->
            setOf(DownloadUiAction.PLAY, DownloadUiAction.OPEN_EXTERNAL, DownloadUiAction.DELETE)
        else -> emptySet()
    }
}
