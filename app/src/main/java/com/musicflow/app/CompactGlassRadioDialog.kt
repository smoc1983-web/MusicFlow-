package com.musicflow.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/**
 * User-selected style #2: compact horizontal artwork/details and live-only
 * transport, inside a small Liquid Glass dialog instead of a full-screen empty
 * canvas. Reuses all five current palettes and existing radio metadata.
 */
@Composable
internal fun CompactGlassRadioDialog(
    player: MediaController,
    title: String,
    artwork: String?,
    playing: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalMusicFlowPalette.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 18.dp)
                .musicFlowGlass(palette, radius = 26.dp, active = playing)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Свернуть радио-плеер",
                        tint = palette.foreground)
                }
                Text("Радио", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.foreground)
                IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, "Закрыть радио-плеер",
                        tint = palette.foreground)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RadioStationArtwork(name = title, logo = artwork,
                    modifier = Modifier.size(94.dp), radius = 19.dp)
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.foreground,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        RadioCompactGlassPolicy.status(
                            playing, player.playerError?.errorCodeName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FilledIconButton(
                    onClick = {
                        if (player.isPlaying) player.pause()
                        else {
                            if (player.playerError != null ||
                                player.playbackState == Player.STATE_IDLE ||
                                player.playbackState == Player.STATE_ENDED)
                                player.prepare()
                            player.play()
                        }
                    },
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = palette.accent,
                        contentColor = if (palette.isLight) Color.White else Color(0xFF180C21)
                    )
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Пауза" else "Воспроизвести",
                        modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(23.dp))
                IconButton(onClick = onFavorite, modifier = Modifier.size(50.dp)) {
                    Icon(if (favorite) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                        "Радиостанция в избранном",
                        tint = if (favorite) palette.accent else palette.foreground,
                        modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                PlayerEqualizerButton(modifier = Modifier.size(48.dp),
                    tint = palette.foreground)
            }
        }
    }
}
