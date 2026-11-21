package com.musicflow.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Persisted choices. Avoid renaming enum entries across upgrades. */
internal enum class MusicFlowStyle(val title: String, val subtitle: String) {
    AB_PEARL("Перламутр", "AudioBook · мягкая светлая палитра"),
    AB_ICY_NIGHT("Ледяная ночь", "AudioBook · тёмный синий и ледяной"),
    AB_LILAC("Сиреневые сумерки", "AudioBook · аметист и сирень"),
    AB_AMBER("Янтарь", "AudioBook · бронза и золото"),
    AB_EMERALD("Изумруд", "AudioBook · глубокий зелёный"),
    AB_OBSIDIAN("Чёрный обсидиан", "AudioBook · серебристый акцент"),
    AB_ONYX("Чёрный оникс", "AudioBook · золото на чёрном"),
    AB_BLACK_ICE("Чёрный лёд", "AudioBook · голубой на чёрном"),
    AB_AMETHYST("Чёрный аметист", "AudioBook · лиловый на чёрном"),
    AB_FLAT_GRAPHITE("Графит · без свечения", "AudioBook · строгий минимализм");

    companion object {
        fun fromSaved(value: String?): MusicFlowStyle =
            entries.firstOrNull { it.name == value } ?: AB_ICY_NIGHT
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

// Exact base colors are adapted from AudioBook's AbGlassThemes.
// Keep the backgrounds as native Compose gradients so MusicFlow remains
// buildable offline without shipping AudioBook's large wallpaper PNGs.
private fun audiobookGlassPalette(
    background: Color,
    surface: Color,
    accent: Color,
    foreground: Color,
    muted: Color,
    reflection: Color,
    light: Boolean = false
): MusicFlowPalette = MusicFlowPalette(
    background = background,
    surface = surface,
    accent = accent,
    foreground = foreground,
    muted = muted,
    gradientTop = androidx.compose.ui.graphics.lerp(background, reflection, 0.17f),
    gradientBottom = background,
    isLight = light
)

internal fun musicFlowPalette(style: MusicFlowStyle): MusicFlowPalette = when (style) {
    MusicFlowStyle.AB_PEARL -> audiobookGlassPalette(
        Color(0xFFF4EEE6), Color(0xF0F5EFE8), Color(0xFF4D42B8),
        Color(0xFF202836), Color(0xFF474F5C), Color(0xFF8DDED7), light = true
    )
    MusicFlowStyle.AB_ICY_NIGHT -> audiobookGlassPalette(
        Color(0xFF061522), Color(0xE0103151), Color(0xFF67E3F4),
        Color(0xFFF5FAFF), Color(0xFFC7D8E5), Color(0xFF81F0DE)
    )
    MusicFlowStyle.AB_LILAC -> audiobookGlassPalette(
        Color(0xFF190F2D), Color(0xE0301854), Color(0xFFD8BBFF),
        Color(0xFFFCF7FF), Color(0xFFD3C6E3), Color(0xFFE3A2E9)
    )
    MusicFlowStyle.AB_AMBER -> audiobookGlassPalette(
        Color(0xFF21150F), Color(0xE0452917), Color(0xFFFFD08C),
        Color(0xFFFFF9F0), Color(0xFFE0CDBC), Color(0xFFFFD5A2)
    )
    MusicFlowStyle.AB_EMERALD -> audiobookGlassPalette(
        Color(0xFF061E1B), Color(0xE00B3E35), Color(0xFF7AE5C1),
        Color(0xFFF2FFF9), Color(0xFFC8E3D6), Color(0xFFAAEEDB)
    )
    MusicFlowStyle.AB_OBSIDIAN -> audiobookGlassPalette(
        Color(0xFF030405), Color(0xF00C1014), Color(0xFFD4E2ED),
        Color(0xFFF7F9FC), Color(0xFFBCC4CD), Color(0xFF9CADBC)
    )
    MusicFlowStyle.AB_ONYX -> audiobookGlassPalette(
        Color(0xFF050403), Color(0xF012100D), Color(0xFFFFD49B),
        Color(0xFFFFFAF2), Color(0xFFD0C3B1), Color(0xFFD5AA70)
    )
    MusicFlowStyle.AB_BLACK_ICE -> audiobookGlassPalette(
        Color(0xFF020406), Color(0xF0091016), Color(0xFF92E4FF),
        Color(0xFFF3FAFF), Color(0xFFB8CBD8), Color(0xFF77BCD7)
    )
    MusicFlowStyle.AB_AMETHYST -> audiobookGlassPalette(
        Color(0xFF050307), Color(0xF0100C17), Color(0xFFD9B5FF),
        Color(0xFFFDF7FF), Color(0xFFCABBD9), Color(0xFFB591D9)
    )
    MusicFlowStyle.AB_FLAT_GRAPHITE -> audiobookGlassPalette(
        Color(0xFF101214), Color(0xFF1D2226), Color(0xFF81E0C0),
        Color(0xFFF4F7F9), Color(0xFFCED6DC), Color(0xFF81E0C0)
    )
}

internal val LocalMusicFlowPalette =
    staticCompositionLocalOf { musicFlowPalette(MusicFlowStyle.AB_ICY_NIGHT) }
