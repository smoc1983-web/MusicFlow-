package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFlowLiquidGlassThemesTest {
    @Test fun exactlyFiveNewLiquidGlassStylesReplaceTenLegacyThemes() {
        val keys = MusicFlowStyle.entries.map { it.name }
        assertEquals(
            listOf("OBSIDIAN_GLASS", "ARCTIC_CRYSTAL", "PEARL_VISION",
                "EMERALD_FLOW", "SOLAR_GLASS"), keys
        )
        assertEquals(5, keys.distinct().size)
        assertFalse(keys.any { it.startsWith("AB_") })
    }

    @Test fun obsidianGlassIsDefaultForFreshInstallsAndUnknownSavedValues() {
        assertEquals(MusicFlowStyle.OBSIDIAN_GLASS, MusicFlowStyle.fromSaved(null))
        assertEquals(MusicFlowStyle.OBSIDIAN_GLASS, MusicFlowStyle.fromSaved(""))
        assertEquals(MusicFlowStyle.OBSIDIAN_GLASS, MusicFlowStyle.fromSaved("invalid-value"))
    }

    @Test fun allTenLegacyChoicesMigrateToTheUserSelectedObsidianDefault() {
        val old = listOf(
            "AB_PEARL", "AB_ICY_NIGHT", "AB_LILAC", "AB_AMBER",
            "AB_EMERALD", "AB_OBSIDIAN", "AB_ONYX", "AB_BLACK_ICE",
            "AB_AMETHYST", "AB_FLAT_GRAPHITE"
        )
        assertEquals(10, old.size)
        old.forEach { saved ->
            assertEquals(saved, MusicFlowStyle.OBSIDIAN_GLASS,
                MusicFlowStyle.fromSaved(saved))
        }
    }

    @Test fun newThemeSelectionRoundTripsThroughPreferences() {
        MusicFlowStyle.entries.forEach { item ->
            assertEquals(item, MusicFlowStyle.fromSaved(item.name))
            val palette = musicFlowPalette(item)
            assertNotEquals(palette.background, palette.foreground)
            assertNotEquals(palette.surface, palette.accent)
            assertTrue(palette.surface.alpha > 0f)
        }
        assertTrue(musicFlowPalette(MusicFlowStyle.PEARL_VISION).isLight)
        assertFalse(musicFlowPalette(MusicFlowStyle.OBSIDIAN_GLASS).isLight)
    }
}
