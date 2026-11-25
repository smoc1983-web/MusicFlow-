package com.musicflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight Liquid Glass: translucent layered reflections, a directional
 * rim highlight and soft elevation. No bitmap assets or content-blurring that
 * would make text and scrolling illegible on mid-range Android devices.
 */
internal fun Modifier.musicFlowGlass(
    palette: MusicFlowPalette,
    radius: Dp = 14.dp,
    active: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(radius)
    val reflection = if (palette.isLight) Color.White else Color(0xFFDCC8FF)
    val borderHighlight = if (active) palette.accent else reflection
    return this
        .shadow(
            elevation = if (active) 9.dp else 5.dp,
            shape = shape,
            ambientColor = palette.accent.copy(alpha = 0.18f),
            spotColor = palette.accent.copy(alpha = if (active) 0.25f else 0.12f)
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0.0f to reflection.copy(alpha = if (palette.isLight) 0.46f else 0.16f),
                0.14f to palette.surface.copy(alpha = 0.98f),
                0.67f to palette.surface.copy(alpha = 0.82f),
                1.0f to palette.gradientTop.copy(alpha = 0.32f)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    borderHighlight.copy(alpha = if (active) 0.76f else 0.48f),
                    palette.accent.copy(alpha = if (active) 0.60f else 0.19f),
                    reflection.copy(alpha = 0.09f),
                    borderHighlight.copy(alpha = 0.23f)
                )
            ),
            shape = shape
        )
}
