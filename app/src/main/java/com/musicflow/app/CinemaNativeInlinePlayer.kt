package com.musicflow.app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Own Media3 player embedded directly in the Cinema page. No Lampa or WebView. */
@OptIn(UnstableApi::class)
@Composable
internal fun CinemaNativeInlinePlayer(
    title: String,
    sourceKey: String,
    streamUrl: String,
    allowBuiltInFallback: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var playbackError by remember(sourceKey, streamUrl) { mutableStateOf<String?>(null) }
    var fallbackNotice by remember(sourceKey, streamUrl) { mutableStateOf<String?>(null) }
    var fallbackUsed by remember(sourceKey, streamUrl) { mutableStateOf(false) }
    var position by remember(sourceKey, streamUrl) { mutableLongStateOf(0L) }

    val player = remember(sourceKey, streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            val saved = CinemaLabSources.progress(context, sourceKey)
            prepare()
            if (saved > 0) seekTo(saved)
            playWhenReady = true
        }
    }

    DisposableEffect(player, streamUrl, sourceKey) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val details = error.cause?.message ?: error.message.orEmpty()
                val forbidden = error.errorCodeName.contains("BAD_HTTP_STATUS") &&
                    (details.contains("403") || details.contains("401"))
                if (allowBuiltInFallback && !fallbackUsed &&
                    streamUrl != CinemaLabSources.LOCAL_TEST_VIDEO && forbidden) {
                    fallbackUsed = true
                    fallbackNotice = "Исходный сервер отклонил запрос (403/401). " +
                        "Сейчас воспроизводится только встроенный тестовый ролик, не фильм."
                    playbackError = null
                    player.setMediaItem(MediaItem.fromUri(CinemaLabSources.LOCAL_TEST_VIDEO))
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    playbackError = "Ошибка Media3: " + error.errorCodeName +
                        ", " + details.take(160)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            CinemaLabSources.saveProgress(context, sourceKey,
                if (fallbackUsed) 0 else player.currentPosition.coerceAtLeast(0))
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(2500)
            position = player.currentPosition.coerceAtLeast(0L)
            if (!fallbackUsed) CinemaLabSources.saveProgress(context, sourceKey, position)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF11131D),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, "Закрыть плеер", tint = Color.White)
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view -> view.player = player },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxWidth().height(206.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
            )
            if (fallbackNotice != null) {
                Text(fallbackNotice.orEmpty(), color = Color(0xFFFFC983),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (playbackError != null) {
                Text(playbackError.orEmpty(), color = Color(0xFFFFAAA8),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    playbackError = null
                    player.prepare()
                    player.play()
                }) { Text("Повторить") }
            }
            Text("Media3 · " + position / 1000L + " сек. · внутри MusicFlow",
                color = Color(0xFFBDAFD1), style = MaterialTheme.typography.labelSmall)
        }
    }
}
