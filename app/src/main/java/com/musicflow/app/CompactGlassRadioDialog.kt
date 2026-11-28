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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val activeItem = player.currentMediaItem
    val activeId = activeItem?.mediaId.orEmpty().removePrefix("radio:")
    val activeUrl = activeItem?.localConfiguration?.uri?.toString().orEmpty()
    var randomMode by remember { mutableStateOf(player.shuffleModeEnabled) }
    val resolvedLogo by produceState<String?>(artwork, title, activeId) {
        value = artwork
        if (activeId.isNotBlank() && RussianRadioDirectory.isPublicStream(activeUrl)) {
            val station = RussianRadioStation(
                activeId, title, activeUrl, artwork, "", "", 0
            )
            value = RadioArtworkCatalog.resolve(context, station) ?: artwork
        }
    }
    val stationCount = player.mediaItemCount
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
                RadioStationArtwork(name = title, logo = resolvedLogo,
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
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(
                    enabled = stationCount > 1,
                    onClick = {
                        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                        else player.seekTo(player.mediaItemCount - 1, 0L)
                        player.play()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.SkipPrevious, "Предыдущая радиостанция",
                        tint = palette.foreground)
                }
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
                IconButton(
                    enabled = stationCount > 1,
                    onClick = {
                        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                        else player.seekTo(0, 0L)
                        player.play()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.SkipNext, "Следующая радиостанция",
                        tint = palette.foreground)
                }
                IconButton(
                    onClick = {
                        randomMode = !player.shuffleModeEnabled
                        player.shuffleModeEnabled = randomMode
                    },
                    enabled = stationCount > 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (randomMode) Icons.Filled.Shuffle
                        else Icons.Filled.FormatListNumbered,
                        if (randomMode) "Включён случайный порядок — переключить по списку"
                        else "Включён порядок по списку — переключить случайно",
                        tint = if (randomMode) palette.accent else palette.foreground)
                }
            }
            Text(
                if (stationCount > 1) {
                    if (randomMode) "Станции: в случайном порядке · $stationCount"
                    else "Станции: по списку · $stationCount"
                } else "В этом списке одна радиостанция",
                modifier = Modifier.fillMaxWidth(),
                color = palette.muted,
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(50.dp)) {
                    Icon(if (favorite) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                        "Радиостанция в избранном",
                        tint = if (favorite) palette.accent else palette.foreground,
                        modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                PlayerEqualizerButton(modifier = Modifier.size(48.dp),
                    tint = palette.foreground)
            }
        }
    }
}
