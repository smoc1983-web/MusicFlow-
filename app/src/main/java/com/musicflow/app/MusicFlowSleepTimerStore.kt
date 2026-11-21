package com.musicflow.app

import android.content.Context

/**
 * Small Android persistence boundary for the sleep deadline.
 * The Media3 service remains the sole owner of stopping actual playback.
 */
internal object MusicFlowSleepTimerStore {
    private const val PREFS = "musicflow_sleep_timer_v1"
    private const val DEADLINE = "deadline_epoch_ms"

    fun deadlineMs(context: Context): Long? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(DEADLINE, 0L).takeIf { it > 0L }

    fun schedule(context: Context, minutes: Int, nowMs: Long = System.currentTimeMillis()): Long? {
        val value = SleepTimerPolicy.deadlineAfterMinutes(nowMs, minutes) ?: return null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(DEADLINE, value).apply()
        return value
    }

    fun cancel(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(DEADLINE).apply()
    }
}
