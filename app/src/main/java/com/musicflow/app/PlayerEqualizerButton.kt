package com.musicflow.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Same persisted five-band sound settings for music, radio, TV and cinema.
 * The button opens a dialog, never calls pause/prepare/seek on the player.
 */
@Composable
internal fun PlayerEqualizerButton(
    modifier: Modifier = Modifier,
    tint: Color = LocalMusicFlowPalette.current.accent
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Filled.GraphicEq, contentDescription = "Эквалайзер плеера",
            tint = tint)
    }
    if (open) MusicFlowEqualizerDialog(onDismiss = { open = false })
}
