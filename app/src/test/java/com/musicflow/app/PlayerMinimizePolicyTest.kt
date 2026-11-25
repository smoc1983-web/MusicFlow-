package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class PlayerMinimizePolicyTest {
    @Test fun downwardSwipeMinimizesOnlyAfterIntentionalDistance() {
        assertFalse(PlayerMinimizePolicy.shouldMinimize(85f, 96f))
        assertTrue(PlayerMinimizePolicy.shouldMinimize(111f, 96f))
        assertFalse(PlayerMinimizePolicy.shouldMinimize(-200f, 96f))
        assertFalse(PlayerMinimizePolicy.shouldMinimize(25f, 96f))
    }

    @Test fun minimizeNeverStopsPlayback() {
        val ui = PlayerMinimizePolicy.onMinimized(isPlaying = true, hasTrack = true)
        assertTrue(ui.keepMiniPlayer)
        assertFalse(ui.expanded)
        assertTrue(ui.playingUnchanged)
    }

    @Test fun currentTrackButtonCanReopenWhenPaused() {
        assertTrue(PlayerMinimizePolicy.canShowNowPlaying(hasTrack = true, isTelevision = false))
        assertFalse(PlayerMinimizePolicy.canShowNowPlaying(hasTrack = false, isTelevision = false))
        assertFalse(PlayerMinimizePolicy.canShowNowPlaying(hasTrack = true, isTelevision = true))
    }
}
