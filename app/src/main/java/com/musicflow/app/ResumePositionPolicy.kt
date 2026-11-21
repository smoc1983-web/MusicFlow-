package com.musicflow.app

/**
 * Resume rules for non-live music. Unknown metadata length is valid only when
 * playback previously exposed seeking; a new unseekable stream always starts at zero.
 */
internal object ResumePositionPolicy {
    private const val MIN_RESUME_MS = 10_000L
    private const val MAX_RESUME_MS = 24L * 60L * 60L * 1000L

    fun resumePosition(id: String, positionMs: Long, durationMs: Long, wasSeekable: Boolean): Long {
        if (id.isBlank() || id.startsWith("radio:") || id.startsWith("ruvideo:") ||
            id.startsWith("vk:") || positionMs !in MIN_RESUME_MS..MAX_RESUME_MS) return 0L

        if (durationMs <= 0L && !wasSeekable) return 0L
        if (durationMs > 0L && positionMs >= durationMs - MIN_RESUME_MS) return 0L
        return positionMs
    }

    fun canApplyAfterPrepare(
        expectedId: String,
        actualId: String,
        ready: Boolean,
        seekable: Boolean
    ): Boolean = expectedId.isNotBlank() && expectedId == actualId && ready && seekable
}
