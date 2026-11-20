package com.musicflow.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

private val TvBackground = Color(0xFF100F17)
private val TvPanel = Color(0xFF242032)
private val TvAccent = Color(0xFFAC80FF)

private data class VideoQualityOption(
    val group: Tracks.Group,
    val index: Int,
    val name: String
)

private tailrec fun Context.findTvActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findTvActivity()
    else -> null
}

@Composable
@OptIn(UnstableApi::class)
internal fun TvPlayerDialog(
    channel: TvChannel,
    channels: List<TvChannel>,
    player: MediaController,
    refreshing: Boolean,
    refreshError: String?,
    onSelectChannel: (TvChannel) -> Unit,
    onReload: () -> Unit,
    onOpenExternal: (TvChannel) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findTvActivity() }
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreen by remember { mutableStateOf(false) }
    var fillVideo by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var currentTracks by remember(player) { mutableStateOf(player.currentTracks) }
    var showControls by remember { mutableStateOf(true) }
    var showChannels by remember { mutableStateOf(false) }
    var channelSearch by remember { mutableStateOf("") }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var playbackError by remember(player, channel.id) { mutableStateOf<String?>(null) }
    val expanded = fullscreen || isLandscape
    val qualityChoices = currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).mapNotNull { index ->
                if (!group.isTrackSupported(index)) return@mapNotNull null
                val format = group.getTrackFormat(index)
                val description = when {
                    format.height > 0 -> format.height.toString() + "p"
                    format.bitrate > 0 -> (format.bitrate / 1000).toString() + " кбит/с"
                    else -> "Дорожка " + (index + 1)
                }
                VideoQualityOption(group, index, description)
            }
        }.distinctBy { it.name }
    val index = channels.indexOfFirst { it.id == channel.id }
    val displayedIndex = if (index >= 0) index else 0

    DisposableEffect(activity) {
        val hadKeepAwake = activity?.window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (!hadKeepAwake) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    LaunchedEffect(activity, fullscreen) {
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else originalOrientation
        AppDiagnostics.info("TV", if (fullscreen) "fullscreen requested" else "fullscreen closed")
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName + ": " +
                    (error.cause?.message ?: error.message.orEmpty()).take(180)
            }
        }
        player.addListener(listener)
        playbackState = player.playbackState
        isPlaying = player.isPlaying
        currentTracks = player.currentTracks
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(channel.id) {
        playbackError = null
        showControls = true
    }
    LaunchedEffect(showControls, showChannels, fullscreen, isLandscape) {
        if (showControls && !showChannels && fullscreen) {
            delay(6000)
            showControls = false
        }
    }

    fun changeChannel(direction: Int) {
        if (channels.isEmpty()) return
        val nextIndex = (displayedIndex + direction + channels.size) % channels.size
        playbackError = null
        showControls = true
        onSelectChannel(channels[nextIndex])
    }
    fun retry() {
        playbackError = null
        player.prepare()
        player.play()
    }
    val filtered = channels.filter {
        channelSearch.isBlank() ||
            it.title.contains(channelSearch.trim(), ignoreCase = true) ||
            it.group.contains(channelSearch.trim(), ignoreCase = true)
    }

    Dialog(
        onDismissRequest = {
            if (showChannels) showChannels = false else onClose()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = !fullscreen
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView, fullscreen) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
                val bars = WindowInsetsControllerCompat(window, window.decorView)
                if (fullscreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                    bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    bars.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                if (window != null) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    WindowInsetsControllerCompat(window, window.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TvBackground
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .then(
                        if (fullscreen) Modifier
                        else Modifier.statusBarsPadding().navigationBarsPadding()
                    )
                    .padding(if (fullscreen) 0.dp else if (isLandscape) 8.dp else 14.dp)
            ) {
                if (!fullscreen || showControls || showChannels) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (showChannels) showChannels = false else onClose()
                    }) {
                        Text("Назад")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (showChannels) "Выбор канала" else channel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Text(
                            if (showChannels) "Доступно: " + channels.size
                            else channel.group + " · ПРЯМОЙ ЭФИР",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvAccent
                        )
                    }
                    TextButton(onClick = { onOpenExternal(channel) }) { Text("Внешний") }
                    TextButton(onClick = { showChannels = !showChannels }) {
                        Text(if (showChannels) "Эфир" else "Каналы")
                    }
                }
                }

                if (showChannels) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = channelSearch,
                        onValueChange = { channelSearch = it },
                        label = { Text("Название канала или категория") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            val active = item.id == channel.id
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) Color(0xFF493465) else TvPanel)
                                    .clickable {
                                        showChannels = false
                                        playbackError = null
                                        onSelectChannel(item)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(item.logo),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, maxLines = 1,
                                         overflow = TextOverflow.Ellipsis,
                                         color = Color.White,
                                         fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                    Text(item.group, style = MaterialTheme.typography.bodySmall,
                                         color = Color.LightGray)
                                }
                                if (active) Text("●", color = TvAccent)
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = if (expanded) Modifier.fillMaxWidth().weight(1f)
                        else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    keepScreenOn = true
                                    this.player = player
                                }
                            },
                            update = { view ->
                                view.player = player
                                view.resizeMode = if (fillVideo)
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else AspectRatioFrameLayout.RESIZE_MODE_FIT
                            },
                            onRelease = { view -> view.player = null },
                            modifier = Modifier.fillMaxSize().background(Color.Black)
                        )
                        // Transparent tap target makes the controls reappear without video seeking.
                        Box(
                            Modifier.matchParentSize().clickable {
                                showControls = !showControls
                            }
                        )
                        if (playbackState == Player.STATE_BUFFERING && playbackError == null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.background(Color(0x99000000),
                                    RoundedCornerShape(12.dp)).padding(18.dp)
                            ) {
                                CircularProgressIndicator(color = TvAccent)
                                Spacer(Modifier.height(8.dp))
                                Text("Подключение к эфиру…", color = Color.White)
                            }
                        }
                        playbackError?.let { message ->
                            Column(
                                modifier = Modifier.fillMaxWidth(0.9f)
                                    .background(Color(0xEE221A29), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Не удалось воспроизвести эфир", color = Color.White,
                                    fontWeight = FontWeight.Bold)
                                Text(message, color = Color(0xFFFFC6BD),
                                    maxLines = 3, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { retry() }) { Text("Повторить") }
                                    TextButton(onClick = onReload, enabled = !refreshing) {
                                        Text("Обновить ссылку")
                                    }
                                }
                            }
                        }
                    }
                    if (showControls || !fullscreen) {
                        Spacer(Modifier.height(if (fullscreen) 2.dp else 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { changeChannel(-1) },
                                enabled = channels.size > 1) {
                                Icon(Icons.Filled.SkipPrevious, "Предыдущий канал",
                                    tint = Color.White)
                            }
                            IconButton(
                                onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                                enabled = player.isCurrentMediaItemSeekable
                            ) { Icon(Icons.Filled.Replay10, "Назад 10 секунд", tint = Color.White) }
                            FilledIconButton(
                                onClick = { if (isPlaying) player.pause() else player.play() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = TvAccent, contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Пауза" else "Воспроизвести"
                                )
                            }
                            IconButton(
                                onClick = {
                                    val length = player.duration
                                    val dest = player.currentPosition + 10_000L
                                    player.seekTo(if (length > 0) dest.coerceAtMost(length) else dest)
                                },
                                enabled = player.isCurrentMediaItemSeekable
                            ) { Icon(Icons.Filled.Forward10, "Вперёд 10 секунд", tint = Color.White) }
                            IconButton(onClick = { changeChannel(1) },
                                enabled = channels.size > 1) {
                                Icon(Icons.Filled.SkipNext, "Следующий канал", tint = Color.White)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { fillVideo = !fillVideo }) {
                                Text(if (fillVideo) "Вписать" else "Заполнить")
                            }
                            TextButton(onClick = { showQuality = true }) {
                                Text(if (qualityChoices.size > 1) "Качество" else "Исходное")
                            }
                            TextButton(onClick = {
                                fullscreen = !fullscreen
                                showControls = true
                            }) { Text(if (fullscreen) "Свернуть" else "⛶ Весь экран") }
                        }
                    }
                    if (!expanded) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("В ЭФИРЕ", color = TvAccent,
                                 style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onReload, enabled = !refreshing) {
                                Text(if (refreshing) "Обновление…" else "Обновить эфир")
                            }
                        }
                        if (channel.isExpired) {
                            Text(
                                "Ссылка на канал в исходном плейлисте устарела.",
                                color = Color(0xFFFFB3A8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        refreshError?.let {
                            Text(it, color = Color(0xFFFFB3A8),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Источник: RuVideoHub. Качество зависит от доступного потока.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

    if (showQuality) {
        AlertDialog(
            onDismissRequest = { showQuality = false },
            title = { Text("Качество телеканала") },
            text = {
                Column {
                    if (qualityChoices.size <= 1) {
                        Text("У этого потока нет нескольких доступных вариантов качества.")
                    } else {
                        Text("Доступные варианты из текущего видеопотока:")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            player.trackSelectionParameters =
                                player.trackSelectionParameters.buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                            showQuality = false
                        }) { Text("Автоматически") }
                        qualityChoices.forEach { option ->
                            TextButton(onClick = {
                                player.trackSelectionParameters =
                                    player.trackSelectionParameters.buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .setOverrideForType(
                                            TrackSelectionOverride(
                                                option.group.mediaTrackGroup, listOf(option.index)
                                            )
                                        ).build()
                                AppDiagnostics.info("TV", "quality switched to " + option.name)
                                showQuality = false
                            }) { Text(option.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuality = false }) { Text("Закрыть") }
            }
        )
    }
    }
}
