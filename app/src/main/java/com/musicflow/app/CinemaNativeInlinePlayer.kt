package com.musicflow.app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Only the user's direct media URL is played; no substitute demo clip. */
@OptIn(UnstableApi::class)
@Composable
internal fun CinemaNativeInlinePlayer(
    title: String,
    sourceKey: String,
    streamUrl: String,
    pageUrl: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var errorText by remember(sourceKey, streamUrl) { mutableStateOf<String?>(null) }
    var position by remember(sourceKey, streamUrl) { mutableLongStateOf(0L) }

    val player = remember(sourceKey, streamUrl, pageUrl) {
        val origin = pageUrl?.takeIf(CinemaLabSources::isSafeHttps)
        val http = DefaultHttpDataSource.Factory()
        // The same page referrer a normal browser supplies; no authentication bypass.
        if (origin != null) http.setDefaultRequestProperties(
            mapOf("Referer" to origin)
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
            ).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            CinemaLabSources.progress(context, sourceKey).takeIf { it > 0 }?.let {
                seekTo(it)
            }
            playWhenReady = true
        }
    }

    DisposableEffect(player, sourceKey) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val message = error.cause?.message ?: error.message.orEmpty()
                errorText = if (message.contains("Response code: 403") ||
                    message.contains("Response code: 401")) {
                    "Сервер отказал в доступе (HTTP 401/403). " +
                        "Ссылка найдена, но доступ к видеопотоку не разрешён."
                } else {
                    error.errorCodeName + ": " + message.take(180)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            CinemaLabSources.saveProgress(context, sourceKey,
                player.currentPosition.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(2500)
            position = player.currentPosition.coerceAtLeast(0L)
            CinemaLabSources.saveProgress(context, sourceKey, position)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF11131D),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1)
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
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxWidth().height(218.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
            )
            errorText?.let { message ->
                Text("Не удалось открыть прямое видео: " + message,
                    color = Color(0xFFFFB3B0),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    errorText = null
                    player.prepare()
                    player.play()
                }) { Text("Повторить") }
            }
            Text("Media3 · сохранена позиция " + position / 1000L + " сек.",
                color = Color(0xFFBDAFD1),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
