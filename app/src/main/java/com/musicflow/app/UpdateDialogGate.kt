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

    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    const val SNOOZE_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun dueForCheck(now: Long, lastSuccess: Long): Boolean =
        lastSuccess <= 0L || now < lastSuccess || now - lastSuccess >= CHECK_INTERVAL_MS

    fun snoozed(code: Long, snoozedCode: Long, until: Long, now: Long): Boolean =
        code == snoozedCode && now < until

    fun onConfirm(state: State): State =
        state.copy(ignoredCode = state.candidateCode ?: 0L, dismissed = true)
}
