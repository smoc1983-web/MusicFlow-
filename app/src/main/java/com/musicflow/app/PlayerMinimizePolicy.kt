package com.musicflow.app

/** Pure transition rules for the full player; hiding its UI must not stop Media3. */
internal data class MinimizedPlayerState(
    val expanded: Boolean,
    val keepMiniPlayer: Boolean,
    val playingUnchanged: Boolean
)

internal object PlayerMinimizePolicy {
    fun shouldMinimize(dragDistancePx: Float, thresholdPx: Float): Boolean =
        thresholdPx > 0f && dragDistancePx >= thresholdPx

    fun onMinimized(isPlaying: Boolean, hasTrack: Boolean): MinimizedPlayerState =
        MinimizedPlayerState(expanded = false, keepMiniPlayer = hasTrack, playingUnchanged = isPlaying)

    fun canShowNowPlaying(hasTrack: Boolean, isTelevision: Boolean): Boolean =
        hasTrack && !isTelevision
}
