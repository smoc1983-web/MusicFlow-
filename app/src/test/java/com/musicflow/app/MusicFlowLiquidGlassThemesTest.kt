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

    @Test fun previousThemesMigrateToClosestOneOfTheFiveChoices() {
        val old = mapOf(
            "AB_PEARL" to "PEARL_VISION",
            "AB_ICY_NIGHT" to "ARCTIC_CRYSTAL",
            "AB_LILAC" to "OBSIDIAN_GLASS",
            "AB_AMBER" to "SOLAR_GLASS",
            "AB_EMERALD" to "EMERALD_FLOW",
            "AB_OBSIDIAN" to "OBSIDIAN_GLASS",
            "AB_ONYX" to "SOLAR_GLASS",
            "AB_BLACK_ICE" to "ARCTIC_CRYSTAL",
            "AB_AMETHYST" to "OBSIDIAN_GLASS",
            "AB_FLAT_GRAPHITE" to "OBSIDIAN_GLASS"
        )
        old.forEach { (saved, expected) ->
            assertEquals(saved, expected, MusicFlowStyle.fromSaved(saved).name)
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
