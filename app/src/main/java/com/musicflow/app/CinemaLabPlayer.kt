package com.musicflow.app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun CinemaLabPlayer(
    title: String,
    sourceKey: String,
    streamUrl: String,
    allowBuiltInFallback: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var playbackError by remember(streamUrl) { mutableStateOf<String?>(null) }
    var fallbackActive by remember(streamUrl) { mutableStateOf(false) }
    var fallbackNotice by remember(streamUrl) { mutableStateOf<String?>(null) }
    var position by remember(streamUrl) { mutableLongStateOf(0L) }
    val player = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            val last = CinemaLabSources.progress(context, sourceKey)
            if (last > 0L) seekTo(last)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player, sourceKey) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause?.message ?: error.message.orEmpty()
                val badHttp = error.errorCodeName.contains("BAD_HTTP_STATUS") ||
                    cause.contains("Response code: 403")
                if (allowBuiltInFallback && !fallbackActive &&
                    streamUrl != CinemaLabSources.LOCAL_TEST_VIDEO && badHttp) {
                    fallbackActive = true
                    fallbackNotice = "Внешний сервер отклонил запрос (HTTP 403). " +
                        "Вместо фильма запущен встроенный технический ролик для проверки Media3."
                    playbackError = null
                    player.setMediaItem(MediaItem.fromUri(CinemaLabSources.LOCAL_TEST_VIDEO))
                    player.prepare()
                    player.play()
                } else {
                    playbackError = error.errorCodeName + ": " + cause.take(180)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            CinemaLabSources.saveProgress(context, sourceKey, player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(2500)
            position = player.currentPosition.coerceAtLeast(0)
            CinemaLabSources.saveProgress(context, sourceKey, position)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color(0xFF0E101A), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, maxLines = 2)
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
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                )
                if (streamUrl == CinemaLabSources.LOCAL_TEST_VIDEO) {
                    Text("Встроенный диагностический ролик: работает без сети.",
                        color = Color(0xFFBDA9D9),
                        style = MaterialTheme.typography.bodySmall)
                }
                fallbackNotice?.let { info ->
                    Text(info, color = Color(0xFFFFCB8A),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (playbackError != null) {
                    Text("Ошибка видео: " + playbackError.orEmpty(),
                        color = Color(0xFFFFA9A9),
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        playbackError = null
                        player.prepare()
                        player.play()
                    }) { Text("Повторить воспроизведение") }
                }
                Text("Проверка Cinema TEST: Media3 / HTTPS. Сохранена позиция: " +
                    (position / 1000) + " сек.",
                    color = Color(0xFFBDA9D9),
                    style = MaterialTheme.typography.bodySmall)
                Text("При отсутствии доступа к серверу контента видео может не открыться. " +
                    "Приложение не обходит подписки, DRM и ограничения видеосервисов.",
                    color = Color(0xFFB4AFBD),
                    style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Вернуться в кино")
                }
            }
        }
    }
}
