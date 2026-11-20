package com.musicflow.app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Persistent inline TV preview. Opening fullscreen never resets playback. */
@Composable
@OptIn(UnstableApi::class)
internal fun TvMiniPlayer(
    player: MediaController,
    channelTitle: String,
    onExpand: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(
                listOf(Color(0xFF26243A), Color(0xFF171C2A))
            )).padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(124.dp)
            .clip(RoundedCornerShape(11.dp)).background(Color.Black)) {
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
            FilledTonalIconButton(onClick = onExpand,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(36.dp)) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Развернуть ТВ")
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                Text(channelTitle.ifBlank { "Прямой эфир" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ТВ · Нажмите для полного экрана",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB99CFF),
                    maxLines = 1)
            }
            IconButton(onClick = {
                if (player.isPlaying) player.pause()
                else {
                    if (player.playerError != null) player.prepare()
                    player.play()
                }
            }) {
                Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (player.isPlaying) "Пауза ТВ" else "Играть ТВ")
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Close, contentDescription = "Остановить ТВ")
            }
        }
    }
}
