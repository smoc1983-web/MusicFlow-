package com.musicflow.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
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
import androidx.media3.ui.AspectRatioFrameLayout
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
    seriesShow: CinemaTvShow? = null,
    seriesSeason: Int = 1,
    seriesEpisodes: List<CinemaTitle> = emptyList(),
    seriesLoading: Boolean = false,
    onSeasonSelected: (Int) -> Unit = {},
    onEpisodeSelected: (CinemaTitle) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var errorText by remember(sourceKey, streamUrl) { mutableStateOf<String?>(null) }
    var position by remember(sourceKey, streamUrl) { mutableLongStateOf(0L) }
    var fullScreen by remember { mutableStateOf(false) }
    var enteredFullScreen by remember { mutableStateOf(false) }
    var seriesOpen by remember { mutableStateOf(false) }
    var controllerVisible by remember { mutableStateOf(true) }
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

    // Always return to portrait when fullscreen ends, including Back and closing.
    DisposableEffect(fullScreen, activity) {
        if (fullScreen && activity != null) {
            enteredFullScreen = true
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fullScreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            if (enteredFullScreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
    fun collapseVideo() {
        fullScreen = false
        qualityOpen = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    fun closeVideo() {
        collapseVideo()
        onClose()
    }
    BackHandler(enabled = fullScreen) { collapseVideo() }

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
                    addOverride(TrackSelectionOverride(
                        option.mediaGroup, listOf(option.trackIndex)
                    ))
                }
            }.build()
        selectedQuality = option?.label ?: "Авто"
        qualityOpen = false
    }

    @Composable
    fun VideoControls(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, modifier = Modifier.weight(1f))
                if (seriesShow != null) {
                    IconButton(onClick = { seriesOpen = true }) {
                        Icon(Icons.Filled.List, "Сезоны и серии", tint = Color.White)
                    }
                }
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
                IconButton(onClick = { fullScreen = true }) {
                    Icon(Icons.Filled.Fullscreen, "На весь экран", tint = Color.White)
                }
                IconButton(onClick = ::closeVideo) {
                    Icon(Icons.Filled.Close, "Закрыть плеер", tint = Color.White)
                }
            }
            CinemaPlayerView(player, Modifier.fillMaxWidth().height(218.dp)) {
                controllerVisible = it
            }
            errorText?.let { message ->
                Text(message, color = Color(0xFFFFB9B8),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    errorText = null
                    player.prepare()
                    player.play()
                }) { Text("Повторить") }
            }
            Text(
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
        ) { VideoControls(Modifier.padding(7.dp)) }
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = ::collapseVideo,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            // Only video and compact icon controls, without title overlay.
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CinemaPlayerView(player, Modifier.fillMaxSize()) {
                    controllerVisible = it
                }
                if (controllerVisible || qualityOpen || seriesOpen) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (seriesShow != null) {
                            IconButton(onClick = { seriesOpen = true }) {
                                Icon(Icons.Filled.List, "Выбор сезона и серии",
                                    tint = Color.White)
                            }
                        }
                        Box {
                            IconButton(onClick = { qualityOpen = true }) {
                                Icon(Icons.Filled.Settings, "Выбор качества",
                                    tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = qualityOpen,
                                onDismissRequest = { qualityOpen = false }
                            ) {
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
                        IconButton(onClick = ::collapseVideo) {
                            Icon(Icons.Filled.FullscreenExit, "Мини-плеер",
                                tint = Color.White)
                        }
                        IconButton(onClick = ::closeVideo) {
                            Icon(Icons.Filled.Close, "Закрыть видео",
                                tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (seriesOpen && seriesShow != null) {
        CinemaPlayerSeasonsDialog(
            show = seriesShow,
            season = seriesSeason,
            episodes = seriesEpisodes,
            loading = seriesLoading,
            onSeason = onSeasonSelected,
            onEpisode = { episode ->
                seriesOpen = false
                onEpisodeSelected(episode)
            },
            onDismiss = { seriesOpen = false }
        )
    }
}

@Composable
private fun CinemaPlayerSeasonsDialog(
    show: CinemaTvShow,
    season: Int,
    episodes: List<CinemaTitle>,
    loading: Boolean,
    onSeason: (Int) -> Unit,
    onEpisode: (CinemaTitle) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 565.dp),
            color = Color(0xFF1F2430), shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(show.name, style = MaterialTheme.typography.titleMedium,
                        color = Color.White, modifier = Modifier.weight(1f),
                        maxLines = 2)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Закрыть список",
                            tint = Color.White)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    (1..show.seasons).forEach { number ->
                        FilterChip(
                            selected = season == number,
                            onClick = { onSeason(number) },
                            label = { Text("Сезон " + number) }
                        )
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (episodes.isEmpty() && !loading) {
                    Text("Серии этого сезона не найдены.", color = Color.LightGray)
                }
                LazyColumn(Modifier.heightIn(max = 355.dp)) {
                    items(episodes, key = { it.key }) { item ->
                        TextButton(onClick = { onEpisode(item) },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Серия " + (item.episodeNumber?.toString() ?: "—") +
                                    " · " + item.title,
                                color = Color.White,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaPlayerView(
    player: ExoPlayer,
    modifier: Modifier,
    onControlsVisible: (Boolean) -> Unit
) {
    val callback by rememberUpdatedState(onControlsVisible)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        callback(visibility == View.VISIBLE)
                    }
                )
            }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier.background(Color.Black)
    )
}
