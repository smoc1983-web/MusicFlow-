package com.musicflow.app

/**
 * One update announcement per version. The confirmation is committed before
 * launching Android's asynchronous DownloadManager or package installer.
 */
internal object UpdateDialogGate {
    data class State(
        val installedCode: Long,
        val candidateCode: Long?,
        val ignoredCode: Long,
        val dismissed: Boolean
    )

    fun visible(state: State): Boolean =
        !state.dismissed &&
            state.candidateCode != null &&
            state.candidateCode > state.installedCode &&
            state.candidateCode != state.ignoredCode

    fun onConfirm(state: State): State =
        state.copy(ignoredCode = state.candidateCode ?: 0L, dismissed = true)
}
