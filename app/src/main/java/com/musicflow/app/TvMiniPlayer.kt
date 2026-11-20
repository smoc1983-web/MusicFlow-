package com.musicflow.app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

/**
 * Movable TV player. The screen behind it remains fully scrollable.
 * Drag the header or the card, tap the compact pill to expand, or use fullscreen.
 * Position is stored as a percentage so rotations and resizing keep it on-screen.
 */
@Composable
@OptIn(UnstableApi::class)
internal fun TvMiniPlayer(
    player: MediaController,
    channelTitle: String,
    onExpand: () -> Unit,
    onStop: () -> Unit
) {
    val palette = LocalMusicFlowPalette.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var horizontalPosition by rememberSaveable { mutableFloatStateOf(1f) }
    var verticalPosition by rememberSaveable { mutableFloatStateOf(0.73f) }

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(20f)) {
        val density = LocalDensity.current
        val width = if (expanded) minOf(maxWidth - 20.dp, 270.dp)
            else minOf(maxWidth - 20.dp, 206.dp)
        val height = if (expanded) 206.dp else 58.dp
        val freeX = with(density) { (maxWidth - width).roundToPx().coerceAtLeast(0) }
        val freeY = with(density) { (maxHeight - height).roundToPx().coerceAtLeast(0) }

        val drag = Modifier.pointerInput(freeX, freeY) {
            detectDragGestures { change, delta: Offset ->
                change.consume()
                if (freeX > 0)
                    horizontalPosition =
                        (horizontalPosition + delta.x / freeX).coerceIn(0f, 1f)
                if (freeY > 0)
                    verticalPosition =
                        (verticalPosition + delta.y / freeY).coerceIn(0f, 1f)
            }
        }
        Column(
            Modifier
                .offset {
                    IntOffset((horizontalPosition * freeX).roundToInt(),
                        (verticalPosition * freeY).roundToInt())
                }
                .width(width)
                .then(drag)
                .shadow(14.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(
                    listOf(palette.gradientTop, palette.surface, palette.background)
                ))
                .padding(6.dp)
        ) {
            if (!expanded) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⋮⋮", modifier = Modifier.padding(horizontal = 5.dp),
                        color = palette.accent)
                    Column(
                        Modifier.weight(1f).clickable { expanded = true },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(channelTitle.ifBlank { "Прямой эфир" },
                            color = palette.foreground, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge)
                        Text("ТВ · нажмите для просмотра",
                            color = palette.muted,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Fullscreen,
                            contentDescription = "Развернуть мини-плеер",
                            tint = palette.accent)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(31.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⋮⋮", modifier = Modifier.padding(horizontal = 6.dp),
                        color = palette.accent)
                    Text(channelTitle.ifBlank { "ТВ" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.foreground)
                    IconButton(
                        modifier = Modifier.size(31.dp),
                        onClick = { expanded = false }
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Свернуть плеер",
                            tint = palette.foreground)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(115.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        } },
                        update = { view -> view.player = player },
                        onRelease = { view -> view.player = null },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.matchParentSize().clickable(onClick = onExpand))
                    FilledTonalIconButton(
                        onClick = onExpand,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(34.dp)
                    ) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "ТВ на весь экран")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("ЭФИР", color = palette.accent,
                        style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause()
                        else {
                            if (player.playerError != null) player.prepare()
                            player.play()
                        }
                    }, modifier = Modifier.size(42.dp)) {
                        Icon(if (player.isPlaying) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = "Пауза или воспроизведение",
                            tint = palette.foreground)
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.Fullscreen,
                            contentDescription = "Полный экран", tint = palette.foreground)
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.Close,
                            contentDescription = "Остановить ТВ", tint = palette.foreground)
                    }
                }
            }
        }
    }
}
