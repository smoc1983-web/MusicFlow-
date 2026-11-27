package com.musicflow.app

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.content.pm.ActivityInfo
import android.view.View
import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import coil.compose.rememberAsyncImagePainter

private data class CinemaVideoQuality(
    val label: String,
    val mediaGroup: androidx.media3.common.TrackGroup,
    val trackIndex: Int,
    val estimatedBytes: Long? = null
)

private data class CinemaAudioOption(
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
    seriesExpected: List<CinemaExpectedEpisode> = emptyList(),
    seriesGuideUrl: String? = null,
    seriesAuditMessage: String = "",
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
    var showDownloadsLibrary by remember { mutableStateOf(false) }
    var showVideoDownloadQuality by remember(streamUrl) { mutableStateOf(false) }
    var downloadInspecting by remember(streamUrl) { mutableStateOf(false) }
    var downloadMessage by remember(streamUrl) { mutableStateOf("") }
    var downloadHlsOptions by remember(streamUrl) { mutableStateOf<List<HlsQuality>>(emptyList()) }
    var downloadDashOptions by remember(streamUrl) { mutableStateOf<List<DashVideoQuality>>(emptyList()) }
    var downloadSelectedHls by remember(streamUrl) { mutableStateOf<Int?>(null) }
    var downloadSelectedDash by remember(streamUrl) { mutableStateOf<Int?>(null) }
    var downloadDirectSize by remember(streamUrl) { mutableStateOf<Long?>(null) }
    val downloadType = remember(streamUrl) { OfflineDownloadRoute.classify(streamUrl) }
    var controllerVisible by remember { mutableStateOf(true) }
    var qualityOpen by remember { mutableStateOf(false) }
    var selectedQuality by remember(sourceKey, streamUrl) { mutableStateOf("Авто") }
    var audioOpen by remember { mutableStateOf(false) }
    var selectedAudio by remember(sourceKey, streamUrl) { mutableStateOf("Авто") }
    var tracksVersion by remember { mutableIntStateOf(0) }

    // Independent cinema ExoPlayer must use the same user-selected PCM EQ as
    // PlaybackService (music, radio and TV), without pausing during edits.
    val cinemaEq = remember(sourceKey, streamUrl, pageUrl) {
        PcmEqAudioProcessor(AudioEqSettings.load(context))
    }
    val player = remember(sourceKey, streamUrl, pageUrl) {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(cinemaEq))
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        val page = pageUrl?.takeIf(CinemaLabSources::isSafeHttps)
        val http = DefaultHttpDataSource.Factory()
        if (page != null) http.setDefaultRequestProperties(mapOf("Referer" to page))
        ExoPlayer.Builder(context, renderers)
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

    // PlayerView lives inside the Activity in inline mode and inside a separate
    // Dialog Window in fullscreen. The view's keepScreenOn flag follows actual
    // playback (including buffering), so Android may sleep on pause or closure.
    var keepVideoScreenAwake by remember(player) {
        mutableStateOf(CinemaScreenAwakePolicy.shouldKeepScreenOn(
            player.playWhenReady, player.playbackState, player.playerError != null
        ))
    }
    DisposableEffect(player) {
        val wakeListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                keepVideoScreenAwake = CinemaScreenAwakePolicy.shouldKeepScreenOn(
                    player.playWhenReady, player.playbackState, player.playerError != null
                )
            }
        }
        player.addListener(wakeListener)
        onDispose {
            player.removeListener(wakeListener)
            keepVideoScreenAwake = false
        }
    }

    DisposableEffect(player, sourceKey) {
        val preferences = context.getSharedPreferences(AudioEqSettings.PREFS,
            Context.MODE_PRIVATE)
        val eqListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == "enabled" || changed == "preset" ||
                changed?.startsWith("gain_") == true ||
                changed == "boost_enabled" || changed == "boost_db") {
                cinemaEq.update(AudioEqSettings.load(context))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(eqListener)
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
            preferences.unregisterOnSharedPreferenceChangeListener(eqListener)
            player.removeListener(listener)
            player.release()
        }
    }

    // Immersive video: hide Android clock, signal, battery, and navigation bars.
    // Both Activity and fullscreen Dialog own windows, so control both.
    DisposableEffect(fullScreen, activity) {
        val window = activity?.window
        if (fullScreen && window != null) {
            enteredFullScreen = true
            hideCinemaSystemBars(window)
            activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fullScreen && window != null) {
                showCinemaSystemBars(window)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            if (enteredFullScreen) {
                activity?.window?.let(::showCinemaSystemBars)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
    fun collapseVideo() {
        fullScreen = false
        qualityOpen = false
        audioOpen = false
        activity?.window?.let(::showCinemaSystemBars)
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
        val audioRates = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .mapNotNull { group ->
                (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                    ?.let { group.getTrackFormat(it).bitrate.toLong().takeIf { rate -> rate > 0L } }
            }
        val durationSeconds = player.duration
            .takeIf { it > 0 && it != C.TIME_UNSET }?.toDouble()?.div(1000.0)
        buildList {
            for (group in player.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    if (format.height <= 0) continue
                    add(CinemaVideoQuality(
                        format.height.toString() + "p", group.mediaTrackGroup, i,
                        DownloadSizeEstimate.bytes(durationSeconds,
                            format.bitrate.toLong().takeIf { it > 0L }
                                ?.plus(audioRates.firstOrNull() ?: 0L))
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

    val audioOptions = remember(player, tracksVersion) {
        val available = buildList {
            var count = 0
            for (group in player.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    count++
                    val format = group.getTrackFormat(i)
                    add(CinemaAudioOption(
                        CinemaAudioTrackLabels.display(
                            format.label, format.language, count
                        ), group.mediaTrackGroup, i
                    ))
                }
            }
        }
        available.mapIndexed { index, option ->
            if (available.count { it.label == option.label } > 1)
                option.copy(label = option.label + " · " + (index + 1))
            else option
        }
    }

    fun selectAudio(option: CinemaAudioOption?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .apply {
                if (option != null) addOverride(
                    TrackSelectionOverride(
                        option.mediaGroup, listOf(option.trackIndex)
                    )
                )
            }.build()
        selectedAudio = option?.label ?: "Авто"
        audioOpen = false
    }

    @Composable
    fun AudioPicker() {
        Box {
            TextButton(
                onClick = { audioOpen = true },
                enabled = CinemaAudioPickerPolicy.canOpen(audioOptions.size)
            ) {
                Text(
                    "Озвучка: " + when {
                        audioOptions.isEmpty() -> "не указана"
                        audioOptions.size == 1 -> audioOptions.first().label
                        else -> selectedAudio
                    }, color = Color(0xFFBCA2FF)
                )
            }
            DropdownMenu(
                expanded = audioOpen,
                onDismissRequest = { audioOpen = false }
            ) {
                Text(
                    CinemaAudioPickerPolicy.status(audioOptions.size),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                DropdownMenuItem(
                    text = { Text("Автоматически") },
                    onClick = { selectAudio(null) }
                )
                audioOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { selectAudio(option) }
                    )
                }
            }
        }
    }

    // Download settings are independent of the current video player's quality override:
    // Media3's playback track index is not a valid HLS/DASH offline StreamKey.
    LaunchedEffect(showVideoDownloadQuality, streamUrl) {
        if (!showVideoDownloadQuality) return@LaunchedEffect
        downloadInspecting = true
        downloadHlsOptions = emptyList()
        downloadDashOptions = emptyList()
        downloadSelectedHls = null
        downloadSelectedDash = null
        downloadMessage = ""
        try {
            when (downloadType) {
                OfflineTransfer.ADAPTIVE_HLS ->
                    downloadHlsOptions = kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        HlsQualityInspector.inspect(streamUrl)
                    }
                OfflineTransfer.ADAPTIVE_DASH ->
                    downloadDashOptions = kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        DashVideoQualityInspector.inspect(streamUrl)
                    }
                OfflineTransfer.DIRECT_FILE ->
                    downloadDirectSize = kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        runCatching {
                            DirectFileSizeInspector.contentLength(streamUrl,
                                DownloadHttpHeaders.validated(pageUrl.orEmpty(), ""))
                        }.getOrNull()
                    }
                else -> Unit
            }
            downloadMessage = if (
                downloadType == OfflineTransfer.ADAPTIVE_HLS && downloadHlsOptions.isEmpty() ||
                downloadType == OfflineTransfer.ADAPTIVE_DASH && downloadDashOptions.isEmpty()
            ) "В потоке нет доступного выбора качества. Будет скачан исходный вариант."
            else CinemaDownloadQualityPolicy.notice(downloadType)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            downloadMessage = "Не удалось получить список качеств: " +
                (failure.message ?: "сеть недоступна") +
                ". Можно оставить «Авто»."
        } finally {
            downloadInspecting = false
        }
    }

    fun downloadCurrentMovie() {
        // The same choice dialog is used from both compact and fullscreen cinema.
        showDownloadsLibrary = false
        showVideoDownloadQuality = true
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
                IconButton(onClick = ::downloadCurrentMovie) {
                    Icon(Icons.Filled.Download, "Скачать фильм", tint = Color.White)
                }
                IconButton(onClick = { showDownloadsLibrary = true }) {
                    Icon(Icons.Filled.FolderOpen, "Мои загрузки", tint = Color.White)
                }
                IconButton(onClick = { fullScreen = true }) {
                    Icon(Icons.Filled.Fullscreen, "На весь экран", tint = Color.White)
                }
                IconButton(onClick = ::closeVideo) {
                    Icon(Icons.Filled.Close, "Закрыть плеер", tint = Color.White)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                text = { Text(DownloadSizeEstimate.choice(
                                    option.label, option.estimatedBytes)) },
                                onClick = { selectQuality(option) }
                            )
                        }
                    }
                }
                AudioPicker()
                PlayerEqualizerButton(tint = Color(0xFFBCA2FF))
            }
            CinemaPlayerView(player, keepVideoScreenAwake,
                Modifier.fillMaxWidth().height(218.dp)) {
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
            // Dialog has its own Window: Activity-only immersive flags are insufficient.
            val dialogView = LocalView.current
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            DisposableEffect(dialogWindow) {
                dialogWindow?.let(::hideCinemaSystemBars)
                onDispose {
                    dialogWindow?.let(::showCinemaSystemBars)
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CinemaPlayerView(player, keepVideoScreenAwake, Modifier.fillMaxSize()) {
                    controllerVisible = it
                }
                if (controllerVisible || qualityOpen || audioOpen || seriesOpen) {
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
                                        text = { Text(DownloadSizeEstimate.choice(
                                        option.label, option.estimatedBytes)) },
                                        onClick = { selectQuality(option) }
                                    )
                                }
                            }
                        }
                        AudioPicker()
                        PlayerEqualizerButton(tint = Color.White)
                        IconButton(onClick = ::downloadCurrentMovie) {
                            Icon(Icons.Filled.Download, "Скачать фильм", tint = Color.White)
                        }
                        IconButton(onClick = { showDownloadsLibrary = true }) {
                            Icon(Icons.Filled.FolderOpen, "Мои загрузки", tint = Color.White)
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

    if (showVideoDownloadQuality) {
        AlertDialog(
            onDismissRequest = { showVideoDownloadQuality = false },
            title = { Text("Качество скачивания видео") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (downloadInspecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if (downloadMessage.isNotBlank()) downloadMessage
                        else CinemaDownloadQualityPolicy.notice(downloadType),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (downloadType == OfflineTransfer.ADAPTIVE_HLS &&
                        downloadHlsOptions.isNotEmpty()) {
                        FilterChip(
                            selected = downloadSelectedHls == null,
                            onClick = { downloadSelectedHls = null },
                            label = { Text("Авто") }
                        )
                        downloadHlsOptions.forEach { option ->
                            FilterChip(
                                selected = downloadSelectedHls == option.index,
                                onClick = { downloadSelectedHls = option.index },
                                enabled = !downloadInspecting && !option.externalAudio,
                                label = { Text(DownloadSizeEstimate.choice(
                                    option.label, option.estimatedBytes) +
                                    if (option.externalAudio) " · отдельное аудио — Авто" else "") }
                            )
                        }
                    }
                    if (downloadType == OfflineTransfer.ADAPTIVE_DASH &&
                        downloadDashOptions.isNotEmpty()) {
                        FilterChip(
                            selected = downloadSelectedDash == null,
                            onClick = { downloadSelectedDash = null },
                            label = { Text("Авто") }
                        )
                        downloadDashOptions.forEachIndexed { index, option ->
                            FilterChip(
                                selected = downloadSelectedDash == index,
                                onClick = { downloadSelectedDash = index },
                                enabled = !downloadInspecting,
                                label = { Text(DownloadSizeEstimate.choice(
                                    option.label, option.estimatedBytes)) }
                            )
                        }
                    }
                    if (downloadType == OfflineTransfer.ADAPTIVE_HLS ||
                        downloadType == OfflineTransfer.ADAPTIVE_DASH) {
                        Text("≈ — расчёт по длительности и битрейту. " +
                            "При потоковой загрузке окончательный размер может отличаться.",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    if (downloadType == OfflineTransfer.DIRECT_FILE) {
                        Text("Размер файла по данным сервера: " +
                            DownloadSizeEstimate.format(downloadDirectSize, false),
                            style = MaterialTheme.typography.bodySmall)
                        Text("Прямой MP4/MKV уже закодирован в одном качестве. " +
                            "Выбор 360p/720p/1080p возможен только при наличии " +
                            "вариантов HLS/DASH или отдельных файлов.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !downloadInspecting,
                    onClick = {
                        val hlsIndex = if (downloadType == OfflineTransfer.ADAPTIVE_HLS)
                            CinemaDownloadQualityPolicy.hlsIndex(
                                downloadHlsOptions, downloadSelectedHls
                            ) else null
                        val dashKeys = if (downloadType == OfflineTransfer.ADAPTIVE_DASH)
                            downloadSelectedDash?.let { downloadDashOptions.getOrNull(it)?.streamKeys }
                        else null
                        val outcome = MediaDownloads.enqueue(
                            context, streamUrl, title, DownloadMediaKind.VIDEO,
                            referer = pageUrl, hlsVariantIndex = hlsIndex,
                            dashStreamKeys = dashKeys
                        )
                        MediaDownloads.showResult(context, outcome)
                        if (outcome is DownloadPlanResult.Accepted)
                            showVideoDownloadQuality = false
                    }
                ) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = { showVideoDownloadQuality = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDownloadsLibrary) {
        Dialog(onDismissRequest = { showDownloadsLibrary = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.88f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { showDownloadsLibrary = false }) {
                            Icon(Icons.Filled.Close, "Вернуться к фильму")
                        }
                    }
                    MusicDownloadsScreen(
                        onAddLink = {
                            showDownloadsLibrary = false
                            downloadCurrentMovie()
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onBeforePlayback = { player.pause() }
                    )
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
            expected = seriesExpected,
            guideUrl = seriesGuideUrl,
            auditMessage = seriesAuditMessage,
            immersive = fullScreen,
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
    expected: List<CinemaExpectedEpisode>,
    guideUrl: String?,
    auditMessage: String,
    immersive: Boolean,
    onSeason: (Int) -> Unit,
    onEpisode: (CinemaTitle) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val known = episodes.mapNotNull { it.episodeNumber }.toSet()
    val authoritative = expected.isNotEmpty()
    val inferred = CinemaEpisodeAudit.coverage(null, season, episodes).expected
    val expectedIndex = if (authoritative) expected else inferred
    val missing = expectedIndex.map { it.number }.filterNot { it in known }
    val displayed: List<Pair<Int, CinemaTitle?>> =
        (episodes.map { (it.episodeNumber ?: Int.MAX_VALUE) to (it as CinemaTitle?) } +
            missing.map { it to null }).sortedBy { it.first }
    LaunchedEffect(season) { listState.scrollToItem(0) }
    Dialog(onDismissRequest = onDismiss) {
        val dialogView = LocalView.current
        val window = (dialogView.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, immersive) {
            if (immersive) window?.let(::hideCinemaSystemBars)
            onDispose {
                if (immersive) window?.let(::showCinemaSystemBars)
            }
        }
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
                Text(
                    if (authoritative) {
                        "По TVmaze: " + expected.size +
                            " серий. Найдено: " + expected.count { it.number in known }
                    } else {
                        "Полное количество не подтверждено. Найдено записей: " +
                            episodes.size
                    },
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
                if (guideUrl != null) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse(guideUrl)))
                        }
                    }) { Text("Источник сведений: TVmaze") }
                }
                if (auditMessage.isNotBlank()) {
                    Text(auditMessage, color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall)
                }
                if (displayed.isEmpty() && !loading) {
                    Text("Серии этого сезона не найдены.", color = Color.LightGray)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 355.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayed) { (number, item) ->
                        if (item == null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF303648),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "Серия " + number + " · " + if (loading)
                                        "идёт поиск"
                                    else "не найдена в подключённых источниках",
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.LightGray
                                )
                            }
                        } else Surface(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onEpisode(item) },
                            color = Color(0xFF303648),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.width(94.dp).height(63.dp)
                                        .background(Color(0xFF414052)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!item.poster.isNullOrBlank()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(item.poster),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Filled.List,
                                            contentDescription = null,
                                            tint = Color(0xFFBCA2FF))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Сезон " + (item.seasonNumber ?: season) +
                                            " · Серия " +
                                            (item.episodeNumber?.toString() ?: "—"),
                                        color = Color(0xFFBCA2FF),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        item.title, color = Color.White,
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(
                                    Icons.Filled.List,
                                    contentDescription = "Выбрать серию",
                                    tint = Color.White
                                )
                            }
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
    keepScreenOn: Boolean,
    modifier: Modifier,
    onControlsVisible: (Boolean) -> Unit
) {
    val callback by rememberUpdatedState(onControlsVisible)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                this.keepScreenOn = keepScreenOn
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
        update = { view ->
            view.player = player
            view.keepScreenOn = keepScreenOn
        },
        onRelease = { view ->
            view.keepScreenOn = false
            view.player = null
        },
        modifier = modifier.background(Color.Black)
    )
}

/** An immersive video hides OS status and navigation bars, not merely app labels. */
private fun hideCinemaSystemBars(window: Window) {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun showCinemaSystemBars(window: Window) {
    WindowInsetsControllerCompat(window, window.decorView).show(
        WindowInsetsCompat.Type.systemBars()
    )
}
