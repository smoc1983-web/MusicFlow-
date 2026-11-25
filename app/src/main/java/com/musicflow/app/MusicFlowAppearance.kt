package com.musicflow.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Only the five Liquid Glass choices are user-visible. Previously persisted AB_*
 * values are accepted exclusively at this boundary and migrated on next launch.
 */
internal enum class MusicFlowStyle(val title: String, val subtitle: String) {
    OBSIDIAN_GLASS("Obsidian Glass", "Чёрное стекло · фиолетовое свечение"),
    ARCTIC_CRYSTAL("Arctic Crystal", "Ледяное стекло · серебро и голубой"),
    PEARL_VISION("Pearl Vision", "Светлый перламутр · пастельные блики"),
    EMERALD_FLOW("Emerald Flow", "Изумрудное стекло · бирюзовое свечение"),
    SOLAR_GLASS("Solar Glass", "Тёплое стекло · янтарь и коралл");

    companion object {
        val DEFAULT: MusicFlowStyle = OBSIDIAN_GLASS

        /** Old names never appear in the picker, but user preferences still load. */
        fun fromSaved(value: String?): MusicFlowStyle = when (value) {
            // The user chose Obsidian as the initial replacement for all ten
            // legacy choices. New Liquid Glass selections still round-trip.
            "AB_PEARL", "AB_ICY_NIGHT", "AB_LILAC", "AB_AMBER",
            "AB_EMERALD", "AB_OBSIDIAN", "AB_ONYX", "AB_BLACK_ICE",
            "AB_AMETHYST", "AB_FLAT_GRAPHITE" -> DEFAULT
            else -> entries.firstOrNull { it.name == value } ?: DEFAULT
        }
    }
}

internal data class MusicFlowPalette(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val foreground: Color,
    val muted: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val isLight: Boolean = false
)

/** Native translucent surfaces: no wallpaper images or proprietary blur shaders. */
internal fun musicFlowPalette(style: MusicFlowStyle): MusicFlowPalette = when (style) {
    MusicFlowStyle.OBSIDIAN_GLASS -> MusicFlowPalette(
        background = Color(0xFF06040C),
        surface = Color(0xB3231B35),
        accent = Color(0xFFC29AFF),
        foreground = Color(0xFFFAF6FF),
        muted = Color(0xFFC9BBD9),
        gradientTop = Color(0xFF251637),
        gradientBottom = Color(0xFF04030A)
    )
    MusicFlowStyle.ARCTIC_CRYSTAL -> MusicFlowPalette(
        background = Color(0xFF071420),
        surface = Color(0xB3264058),
        accent = Color(0xFF87DCFF),
        foreground = Color(0xFFF4FBFF),
        muted = Color(0xFFBFD5E4),
        gradientTop = Color(0xFF16435D),
        gradientBottom = Color(0xFF030C16)
    )
    MusicFlowStyle.PEARL_VISION -> MusicFlowPalette(
        background = Color(0xFFF2F2F9),
        surface = Color(0xC9FFFFFF),
        accent = Color(0xFF7356C9),
        foreground = Color(0xFF242236),
        muted = Color(0xFF625E75),
        gradientTop = Color(0xFFE5DAF6),
        gradientBottom = Color(0xFFFFFBFF),
        isLight = true
    )
    MusicFlowStyle.EMERALD_FLOW -> MusicFlowPalette(
        background = Color(0xFF061814),
        surface = Color(0xB31F3D37),
        accent = Color(0xFF7FF0D0),
        foreground = Color(0xFFF1FFF9),
        muted = Color(0xFFBFDED5),
        gradientTop = Color(0xFF155348),
        gradientBottom = Color(0xFF03110E)
    )
    MusicFlowStyle.SOLAR_GLASS -> MusicFlowPalette(
        background = Color(0xFF1D1016),
        surface = Color(0xB3473039),
        accent = Color(0xFFFFBA8D),
        foreground = Color(0xFFFFF7F2),
        muted = Color(0xFFE6C6C6),
        gradientTop = Color(0xFF60404C),
        gradientBottom = Color(0xFF12090F)
    )
}

internal val LocalMusicFlowPalette =
    staticCompositionLocalOf { musicFlowPalette(MusicFlowStyle.DEFAULT) }
