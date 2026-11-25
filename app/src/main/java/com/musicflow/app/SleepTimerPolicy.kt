package com.musicflow.app

/** Pure timing rules, independent of Android clocks and playback state. */
internal object SleepTimerPolicy {
    fun deadlineAfterMinutes(nowMs: Long, minutes: Int): Long? {
        if (minutes !in 1..180 || nowMs < 0L) return null
        val duration = minutes * 60_000L
        if (nowMs > Long.MAX_VALUE - duration) return null
        return nowMs + duration
    }

    fun isExpired(deadlineMs: Long?, nowMs: Long): Boolean =
        deadlineMs != null && deadlineMs > 0L && nowMs >= deadlineMs

    fun remainingMs(deadlineMs: Long?, nowMs: Long): Long =
        if (deadlineMs == null || deadlineMs <= nowMs) 0L else deadlineMs - nowMs
}
