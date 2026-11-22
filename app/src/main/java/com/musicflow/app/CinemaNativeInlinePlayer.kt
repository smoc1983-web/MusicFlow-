package com.musicflow.app

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private data class CinemaVideoQuality(
    val label: String,
    val mediaGroup: androidx.media3.common.TrackGroup,
    val trackIndex: Int
)

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
    val activity = context as? Activity
    var errorText by remember(sourceKey, streamUrl) { mutableStateOf<String?>(null) }
    var position by remember(sourceKey, streamUrl) { mutableLongStateOf(0L) }
    var fullScreen by remember { mutableStateOf(false) }
    var qualityOpen by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Авто") }
    var tracksVersion by remember { mutableIntStateOf(0) }

    val player = remember(sourceKey, streamUrl, pageUrl) {
        val page = pageUrl?.takeIf(CinemaLabSources::isSafeHttps)
        val http = DefaultHttpDataSource.Factory()
        if (page != null) http.setDefaultRequestProperties(mapOf("Referer" to page))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                CinemaLabSources.progress(context, sourceKey)
                    .takeIf { it > 0L }?.let { seekTo(it) }
                playWhenReady = true
            }
    }

    DisposableEffect(player, sourceKey) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
            override fun onPlayerError(error: PlaybackException) {
                val message = error.cause?.message ?: error.message.orEmpty()
                errorText = if (message.contains("Response code: 403") ||
                    message.contains("Response code: 401")) {
                    "Видеосервис отклонил запрос (HTTP 401/403). " +
                        "Для этого потока доступ не предоставлен."
                } else error.errorCodeName + ": " + message.take(180)
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

    DisposableEffect(fullScreen, activity) {
        val previous = activity?.requestedOrientation
        if (fullScreen && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fullScreen && previous != null) activity?.requestedOrientation = previous
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(2400)
            position = player.currentPosition.coerceAtLeast(0L)
            CinemaLabSources.saveProgress(context, sourceKey, position)
        }
    }

    val qualityOptions = remember(player, tracksVersion) {
        buildList {
            for (group in player.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    if (format.height <= 0) continue
                    add(CinemaVideoQuality(
                        format.height.toString() + "p", group.mediaTrackGroup, i
                    ))
                }
            }
        }.distinctBy { it.label }.sortedByDescending {
            it.label.removeSuffix("p").toIntOrNull() ?: 0
        }
    }

    fun selectQuality(option: CinemaVideoQuality?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .apply {
                if (option != null) {
                    setOverrideForType(TrackSelectionOverride(
                        option.mediaGroup, listOf(option.trackIndex)
                    ))
                }
            }.build()
        selectedQuality = option?.label ?: "Авто"
        qualityOpen = false
    }

    @Composable
    fun VideoControls(modifier: Modifier, isFull: Boolean) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { qualityOpen = true }) {
                        Text("Качество: " + selectedQuality, color = Color(0xFFBCA2FF))
                    }
                    DropdownMenu(expanded = qualityOpen,
                        onDismissRequest = { qualityOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Автоматически") },
                            onClick = { selectQuality(null) }
                        )
                        qualityOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { selectQuality(option) }
                            )
                        }
                    }
                }
                IconButton(onClick = { fullScreen = !fullScreen }) {
                    Icon(if (isFull) Icons.Filled.FullscreenExit
                        else Icons.Filled.Fullscreen,
                        if (isFull) "Свернуть плеер" else "На весь экран",
                        tint = Color.White)
                }
                IconButton(onClick = {
                    if (fullScreen) fullScreen = false else onClose()
                }) {
                    Icon(Icons.Filled.Close,
                        if (fullScreen) "Закрыть полный экран" else "Закрыть плеер",
                        tint = Color.White)
                }
            }
            CinemaPlayerView(player,
                if (isFull) Modifier.fillMaxWidth().weight(1f)
                else Modifier.fillMaxWidth().height(218.dp))
            errorText?.let { message ->
                Text(message, color = Color(0xFFFFB9B8),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    errorText = null
                    player.prepare()
                    player.play()
                }) { Text("Повторить") }
            }
            if (!isFull) Text(
                "Позиция: " + position / 1000L + " сек. • " +
                    (if (qualityOptions.isEmpty()) "Качество определяется потоком"
                     else "Доступно: " + qualityOptions.joinToString { it.label }),
                color = Color(0xFFB1A8BC),
                style = MaterialTheme.typography.labelSmall)
        }
    }

    if (!fullScreen) {
        Surface(color = Color(0xFF11131D),
            shape = RoundedCornerShape(17.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        ) { VideoControls(Modifier.padding(7.dp), false) }
    }

    if (fullScreen) {
        Dialog(onDismissRequest = { fullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )) {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                VideoControls(Modifier.fillMaxSize().padding(8.dp), true)
            }
        }
    }
}

@Composable
private fun CinemaPlayerView(player: ExoPlayer, modifier: Modifier) {
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
        modifier = modifier.background(Color.Black, RoundedCornerShape(12.dp))
    )
}
