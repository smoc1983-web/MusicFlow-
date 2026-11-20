package com.musicflow.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Persisted choices. Avoid renaming enum entries across upgrades. */
internal enum class MusicFlowStyle(val title: String, val subtitle: String) {
    AURORA("Aurora Glass", "Северное сияние · бирюза и индиго"),
    AMOLED("AMOLED Neon", "Чёрный экран · неоновая бирюза"),
    MIDNIGHT("Midnight Violet", "Ночной фиолетовый"),
    GRAPHITE("Graphite Gold", "Графит и тёплое золото"),
    SOFT_LIGHT("Soft Light", "Светлая тема · лаванда");

    companion object {
        fun fromSaved(value: String?): MusicFlowStyle =
            entries.firstOrNull { it.name == value } ?: AURORA
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

internal fun musicFlowPalette(style: MusicFlowStyle): MusicFlowPalette = when (style) {
    MusicFlowStyle.AURORA -> MusicFlowPalette(
        Color(0xFF0D1727), Color(0xFF192940), Color(0xFF3BDAD7),
        Color(0xFFF2F8FF), Color(0xFFB6CBDD),
        Color(0xFF1D3B60), Color(0xFF0C1324)
    )
    MusicFlowStyle.AMOLED -> MusicFlowPalette(
        Color.Black, Color(0xFF14191B), Color(0xFF00E5D0),
        Color.White, Color(0xFFA3B6B9),
        Color(0xFF071B1B), Color.Black
    )
    MusicFlowStyle.MIDNIGHT -> MusicFlowPalette(
        Color(0xFF10121D), Color(0xFF262039), Color(0xFFA78BFA),
        Color(0xFFF5F3FF), Color(0xFFBBB4CD),
        Color(0xFF34264E), Color(0xFF10121D)
    )
    MusicFlowStyle.GRAPHITE -> MusicFlowPalette(
        Color(0xFF141414), Color(0xFF292929), Color(0xFFD8AC68),
        Color(0xFFEDE6D9), Color(0xFFBDB3A3),
        Color(0xFF37312A), Color(0xFF121212)
    )
    MusicFlowStyle.SOFT_LIGHT -> MusicFlowPalette(
        Color(0xFFF8F7F4), Color(0xFFE6E4EF), Color(0xFF7465BC),
        Color(0xFF202033), Color(0xFF625F70),
        Color(0xFFEDE9FA), Color(0xFFF8F7F4), isLight = true
    )
}

internal val LocalMusicFlowPalette =
    staticCompositionLocalOf { musicFlowPalette(MusicFlowStyle.AURORA) }
