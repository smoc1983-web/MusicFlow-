package com.musicflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact glass surface adapted to the palette and gentle reflections used in
 * AudioBook. Native gradients keep the MusicFlow UI responsive and offline.
 */
internal fun Modifier.musicFlowGlass(
    palette: MusicFlowPalette,
    radius: Dp = 14.dp,
    active: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    palette.surface.copy(alpha = if (palette.isLight) 0.97f else 0.92f),
                    palette.surface.copy(alpha = if (palette.isLight) 0.95f else 0.83f),
                    palette.gradientTop.copy(alpha = 0.55f)
                )
            )
        )
        .border(
            1.dp,
            palette.accent.copy(alpha = if (active) 0.62f else 0.16f),
            shape
        )
}
