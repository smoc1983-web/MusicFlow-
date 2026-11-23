package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design contract for the user-selected Compact Glass radio layout. */
class RadioCompactGlassPolicyTest {
    @Test fun controlsContainOnlyUsefulRadioActions() {
        assertEquals(
            listOf("play_pause", "favorite", "equalizer", "close", "collapse"),
            RadioCompactGlassPolicy.actions
        )
        assertFalse(RadioCompactGlassPolicy.actions.contains("seek"))
        assertFalse(RadioCompactGlassPolicy.actions.contains("skip"))
        assertFalse(RadioCompactGlassPolicy.actions.contains("shuffle"))
        assertFalse(RadioCompactGlassPolicy.actions.contains("repeat"))
    }

    @Test fun meaningfulPlaybackStateReplacesMisleadingMusicLabels() {
        assertEquals("На паузе", RadioCompactGlassPolicy.status(false, null))
        assertEquals("Сейчас играет", RadioCompactGlassPolicy.status(true, null))
        assertEquals("Поток временно недоступен",
            RadioCompactGlassPolicy.status(false, "MEDIA_ERROR"))
        assertTrue(RadioCompactGlassPolicy.isCompact(isRadio = true))
        assertFalse(RadioCompactGlassPolicy.isCompact(isRadio = false))
    }
}
