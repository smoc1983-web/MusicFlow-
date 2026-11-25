package com.musicflow.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal data class TvHandoff(val channels: List<TvChannel>, val selectedId: String, val sourceId: String)
internal object TvPlaybackHandoff {
    @Volatile private var latest: TvHandoff? = null
    fun set(channels: List<TvChannel>, selected: TvChannel, sourceId: String) {
        latest = TvHandoff(channels.toList(), selected.id, sourceId)
    }
    fun snapshot(): TvHandoff? = latest
    fun clear() { latest = null }
}

class TvFullscreenActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDiagnostics.initialize(this)
        val snapshot = TvPlaybackHandoff.snapshot() ?: run { finish(); return }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Use the SAME MediaController as the mini-player. Opening a second
        // controller could leave the ExoPlayer surface on the wrong Activity.
        val shared = TvVideoSurfaceBridge.sharedController()
        if (shared != null) {
            controller.value = shared
            AppDiagnostics.info("TV", "fullscreen shared MediaController")
        } else {
            // Safe fallback if Android recreates an Activity without its parent.
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            future = MediaController.Builder(this, token).buildAsync().also { f ->
                f.addListener({
                    runCatching { controller.value = f.get() }
                        .onFailure { AppDiagnostics.failure("TV", "fullscreen connection", it) }
                }, ContextCompat.getMainExecutor(this))
            }
        }
        setContent {
            val activeStyle = MusicFlowStyle.fromSaved(
                getSharedPreferences("musicflow", MODE_PRIVATE).getString("ui_theme", null)
            )
            val palette = musicFlowPalette(activeStyle)
            MaterialTheme(colorScheme = darkColorScheme(
                primary = palette.accent, background = Color.Black,
                surface = palette.surface, onSurface = Color.White
            )) {
                val player = controller.value
                if (player == null) Box(Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else FullscreenTvContent(player, snapshot, onExit = {
                    // Return to inline preview; stopping playback is a separate action.
                    finish()
                })
            }
        }
    }
    override fun onDestroy() {
        TvVideoSurfaceBridge.fullscreenDestroyed()
        future?.let { MediaController.releaseFuture(it) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

private enum class PictureMode(val title: String, val description: String) {
    ORIGINAL("Оригинал", "Пиксель в пиксель без увеличения"),
    FIT("Вписать", "Сохранить пропорции и всю картинку"),
    COVER("Заполнить", "На весь экран, края могут обрезаться"),
    STRETCH("Растянуть", "На весь экран без обрезки, пропорции меняются"),
    RATIO169("16:9", "Подогнать изображение под 16:9"),
    RATIO43("4:3", "Подогнать изображение под 4:3")
}

@Composable
@OptIn(UnstableApi::class)
private fun FullscreenVideo(player: MediaController, mode: PictureMode,
                            size: VideoSize, modifier: Modifier) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current.density
        val frame = when (mode) {
            PictureMode.ORIGINAL -> {
                if (size.width <= 0 || size.height <= 0) Modifier.fillMaxSize()
                else {
                    val w = (size.width * size.pixelWidthHeightRatio / density).dp
                    val h = (size.height / density).dp
                    val factor = minOf(1f, maxWidth.value / w.value, maxHeight.value / h.value)
                    Modifier.size(w * factor, h * factor)
                }
            }
            PictureMode.RATIO169, PictureMode.RATIO43 -> {
                val ratio = if (mode == PictureMode.RATIO169) 16f / 9f else 4f / 3f
                val w = minOf(maxWidth.value, maxHeight.value * ratio).dp
                Modifier.size(w, w / ratio)
            }
            else -> Modifier.fillMaxSize()
        }
        AndroidView(factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(
                R.layout.musicflow_tv_texture_player, null, false
            ) as PlayerView).apply {
                useController = false
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                // The bridge attaches only after the old surface is removed.
            }
        }, update = { view ->
            TvVideoSurfaceBridge.bindFullscreen(view, player)
            view.resizeMode = when (mode) {
                PictureMode.ORIGINAL, PictureMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PictureMode.COVER -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        }, onRelease = { TvVideoSurfaceBridge.releaseFullscreen(it) },
            modifier = frame.background(Color.Black))
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun FullscreenTvContent(player: MediaController, initial: TvHandoff,
                                onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf(initial.channels) }
    var selected by remember { mutableIntStateOf(
        channels.indexOfFirst { it.id == initial.selectedId }.coerceAtLeast(0)
    ) }
    var showUi by remember { mutableStateOf(true) }
    var showList by remember { mutableStateOf(false) }
    var showSize by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var playState by remember { mutableIntStateOf(player.playbackState) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var size by remember { mutableStateOf(player.videoSize) }
    var failure by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("musicflow_tv_display", 0) }
    var mode by remember { mutableStateOf(
        PictureMode.entries.firstOrNull { it.name == prefs.getString("sizeMode", "COVER") }
            ?: PictureMode.COVER
    ) }
    val active = channels.getOrNull(selected)
    val qualityOptions = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group -> (0 until group.length).mapNotNull { idx ->
            if (!group.isTrackSupported(idx)) return@mapNotNull null
            val format = group.getTrackFormat(idx)
            val label = when {
                format.height > 0 -> format.height.toString() + "p"
                format.bitrate > 0 -> (format.bitrate / 1000).toString() + " кбит/с"
                else -> "Дорожка " + (idx + 1)
            }
            Triple(group, idx, label)
        } }.distinctBy { it.third }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { playState = state }
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onTracksChanged(updated: Tracks) { tracks = updated }
            override fun onVideoSizeChanged(updated: VideoSize) { size = updated }
            override fun onPlayerError(error: PlaybackException) {
                failure = error.errorCodeName
                AppDiagnostics.failure("TV", "fullscreen playback", error)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(showUi, showList, showSize, showQuality) {
        if (showUi && !showList && !showSize && !showQuality) {
            delay(6500L)
            showUi = false
        }
    }
    fun choose(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        selected = index
        TvPlaybackHandoff.set(channels, channel, initial.sourceId)
        failure = null
        notice = null
        val path = android.net.Uri.parse(channel.streamUrl).path.orEmpty().lowercase(Locale.ROOT)
        val item = MediaItem.Builder().setMediaId("ruvideo:" + channel.id)
            .setUri(channel.streamUrl)
        when {
            path.endsWith(".m3u8") -> item.setMimeType(MimeTypes.APPLICATION_M3U8)
            path.endsWith(".mpd") -> item.setMimeType(MimeTypes.APPLICATION_MPD)
            path.endsWith(".ism") -> item.setMimeType(MimeTypes.APPLICATION_SS)
        }
        player.setMediaItem(item.setMediaMetadata(MediaMetadata.Builder()
            .setTitle(channel.title).setArtist("IPTV").build()).build())
        player.prepare()
        player.play()
        showUi = true
    }
    fun change(delta: Int) {
        if (channels.isNotEmpty()) choose((selected + delta + channels.size) % channels.size)
    }
    fun refresh() {
        scope.launch {
            refreshing = true
            notice = null
            try {
                val updated = if (initial.sourceId == "ruvideohub") retrieveChannels()
                    else {
                        IptvStorage.initialize(context)
                        val src = loadIptvSources(context).firstOrNull { it.id == initial.sourceId }
                            ?: error("Источник удалён")
                        fetchIptvSource(src)
                    }
                val id = active?.id
                channels = updated
                val next = updated.indexOfFirst { it.id == id }
                choose(if (next >= 0) next else 0)
            } catch (e: Exception) {
                notice = "Ошибка обновления списка каналов"
                AppDiagnostics.failure("TV", "reload playlist", e)
            } finally { refreshing = false }
        }
    }

    // A single system Back always returns to the mini-player; dialogs handle
    // their own Back events while they are visible.
    BackHandler { onExit() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FullscreenVideo(player, mode, size, Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().clickable { showUi = !showUi })
        if (playState == Player.STATE_BUFFERING && failure == null)
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFFAC80FF))
        if (showUi) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xB9000000)).padding(horizontal=10.dp, vertical=2.dp),
                verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onExit) { Text("Назад") }
                Text(active?.title ?: "ТВ", maxLines=1, overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.weight(1f), color=Color.White)
                TextButton(onClick={showSize=true}) { Text("Размер") }
                TextButton(onClick={showQuality=true}) { Text("Качество") }
                PlayerEqualizerButton(tint = Color.White)
                TextButton(onClick={showList=true}) { Text("Каналы") }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xB9000000)).padding(horizontal=6.dp, vertical=2.dp),
                horizontalAlignment=Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly,
                    verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={change(-1)},enabled=channels.size>1){Text("⏮")}
                    TextButton(onClick={
                        player.seekTo((player.currentPosition-10_000L).coerceAtLeast(0L))
                    },enabled=player.isCurrentMediaItemSeekable){Text("−10с")}
                    FilledTonalButton(onClick={if(isPlaying)player.pause() else player.play()}){
                        Text(if(isPlaying)"Пауза" else "Играть")
                    }
                    TextButton(onClick={
                        val target=player.currentPosition+10_000L
                        player.seekTo(if(player.duration>0)target.coerceAtMost(player.duration) else target)
                    },enabled=player.isCurrentMediaItemSeekable){Text("+10с")}
                    TextButton(onClick={change(1)},enabled=channels.size>1){Text("⏭")}
                }
                notice?.let { Text(it,color=Color(0xFFFFB3A8)) }
                failure?.let {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Ошибка эфира", color=Color(0xFFFFB3A8))
                        TextButton(onClick={player.prepare();player.play();failure=null}){Text("Повторить")}
                        TextButton(onClick={refresh()}){Text("Обновить")}
                    }
                }
            }
        }
        if (showList) Surface(
            modifier=Modifier.fillMaxHeight().widthIn(max=310.dp).align(Alignment.CenterEnd),
            color=Color(0xF0181522)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Каналы · "+channels.size, modifier=Modifier.weight(1f))
                    TextButton(onClick={showList=false}){Text("Закрыть")}
                }
                TextButton(onClick={refresh()},enabled=!refreshing) {
                    Text(if(refreshing)"Обновление…" else "Обновить список")
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(channels) { channel ->
                        TextButton(onClick={
                            choose(channels.indexOfFirst{it.id==channel.id})
                            showList=false
                        }) {Text(channel.title,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    }
                }
            }
        }
    }
    if(showSize) AlertDialog(
        onDismissRequest={showSize=false},
        title={Text("Размер изображения")},
        text={Column {
            PictureMode.entries.forEach { candidate ->
                TextButton(modifier=Modifier.fillMaxWidth(),onClick={
                    mode=candidate
                    prefs.edit().putString("sizeMode",candidate.name).apply()
                    showSize=false
                    showUi=true
                    AppDiagnostics.info("TV","size mode="+candidate.name)
                }) {
                    Column(Modifier.fillMaxWidth()) {
                        Text((if(mode==candidate)"✓ " else "")+candidate.title)
                        Text(candidate.description,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick={showSize=false}){Text("Закрыть")}}
    )
    if(showQuality) AlertDialog(
        onDismissRequest={showQuality=false},
        title={Text("Качество потока")},
        text={Column {
            TextButton(onClick={
                player.trackSelectionParameters=player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                showQuality=false
            }){Text("Авто")}
            if(qualityOptions.size<2) Text("Других вариантов поток не содержит.")
            else qualityOptions.forEach { option ->
                TextButton(onClick={
                    player.trackSelectionParameters=player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .setOverrideForType(
                            TrackSelectionOverride(option.first.mediaTrackGroup,listOf(option.second))
                        ).build()
                    showQuality=false
                }){Text(option.third)}
            }
        }},
        confirmButton={TextButton(onClick={showQuality=false}){Text("Закрыть")}}
    )
}
