package com.musicflow.app

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaScreenAwakePolicyTest {
    @Test fun playbackKeepsScreenAwakeInInlineAndFullscreenModes() {
        // Both UI modes use the same playback-driven keep-awake decision.
        assertTrue(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_READY, false))
        assertTrue(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_BUFFERING, false))
    }

    @Test fun pausedOrStoppedVideoAllowsScreenToSleep() {
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(false, Player.STATE_READY, false))
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(false, Player.STATE_BUFFERING, false))
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_IDLE, false))
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_ENDED, false))
    }

    @Test fun playbackErrorReleasesScreenAwakeRequest() {
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_READY, true))
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_BUFFERING, true))
    }

    @Test fun resumingFromPauseRestoresKeepAwakeRequest() {
        assertFalse(CinemaScreenAwakePolicy.shouldKeepScreenOn(false, Player.STATE_READY, false))
        assertTrue(CinemaScreenAwakePolicy.shouldKeepScreenOn(true, Player.STATE_READY, false))
    }
}
