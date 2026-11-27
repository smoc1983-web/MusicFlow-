package com.musicflow.app

import androidx.media3.common.Player

/**
 * Keep the display awake only for an actively requested cinema playback.
 * BUFFERING must count: a slow video stream should not let Android dim
 * the screen before the next frame arrives. Paused, ended, idle and failed
 * playback must release the request.
 *
 * The visual PlayerView applies this policy in whichever window owns it:
 * the Activity's inline player or the dedicated fullscreen Dialog.
 */
internal object CinemaScreenAwakePolicy {
    fun shouldKeepScreenOn(
        playWhenReady: Boolean,
        playbackState: Int,
        hasError: Boolean
    ): Boolean = playWhenReady && !hasError &&
        (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
}
