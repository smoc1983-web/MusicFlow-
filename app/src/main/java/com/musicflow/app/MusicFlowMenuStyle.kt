package com.musicflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Rounded popup glass with a soft top reflection for each Liquid Glass theme. */
internal fun Modifier.musicFlowMenuSurface(
    palette: MusicFlowPalette
): Modifier {
    val shape = RoundedCornerShape(22.dp)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    if (palette.isLight) Color.White.copy(alpha = 0.48f)
                    else palette.accent.copy(alpha = 0.14f),
                    palette.surface,
                    palette.gradientBottom.copy(alpha = 0.52f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    palette.accent.copy(alpha = 0.48f),
                    palette.foreground.copy(alpha = 0.11f),
                    palette.accent.copy(alpha = 0.19f)
                )
            ),
            shape = shape
        )
}
