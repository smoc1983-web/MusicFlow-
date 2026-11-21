package com.musicflow.app

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Reusable rounded popup surface for every AudioBook-inspired MusicFlow theme. */
internal fun Modifier.musicFlowMenuSurface(
    palette: MusicFlowPalette
): Modifier {
    val shape = RoundedCornerShape(20.dp)
    return this
        .clip(shape)
        .border(1.dp, palette.accent.copy(alpha = 0.25f), shape)
}
