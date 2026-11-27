package com.musicflow.app

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.rememberAsyncImagePainter
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

private val Violet = Color(0xFFB99CFF)
private val Panel = Color(0xFF1D2130)

internal data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val stream: String,
    val durationSec: Long,
    val collectionTitle: String? = null,
    val alternatives: List<MusicStream> = emptyList()
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val playerState = mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDiagnostics.initialize(this)
        // Erase credentials used by the retired provider; this release does
        // not retain account tokens or related Android Keystore entries.
        runCatching { deleteSharedPreferences("musicflow_vk_private") }
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("com.musicflow.app.vk.audio.credentials.v1")
        }
        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        AppDiagnostics.info("App", "MusicFlow " + appVersion +
            " launched; package=" + packageName + "; Android SDK=" + android.os.Build.VERSION.SDK_INT)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                runCatching {
                    playerState.value = future.get().also {
                        TvVideoSurfaceBridge.registerMainController(it)
                    }
                    AppDiagnostics.info("Player", "controller connected")
                }.onFailure { AppDiagnostics.failure("Player", "connect", it) }
            }, ContextCompat.getMainExecutor(this))
        }
        setContent {
            var themeStyle by remember {
                val preferences = getSharedPreferences("musicflow", MODE_PRIVATE)
                val oldKey = preferences.getString("ui_theme", null)
                val selected = MusicFlowStyle.fromSaved(oldKey)
                // One-time canonical migration. Legacy theme names never leak back
                // into the new picker and Obsidian is the default for old installs.
                if (oldKey != selected.name) {
                    preferences.edit().putString("ui_theme", selected.name).apply()
                }
                mutableStateOf(selected)
            }
            val palette = musicFlowPalette(themeStyle)
            // Material3 DropdownMenu/AlertDialog use surfaceContainer, not surface.
            // Override the container family so popups match the AudioBook theme.
            val scheme = if (palette.isLight) lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.background,
                secondary = palette.accent,
                background = palette.background,
                surface = palette.surface,
                surfaceContainer = palette.surface,
                surfaceContainerLow = palette.surface,
                surfaceContainerHigh = palette.surface,
                surfaceContainerHighest = palette.surface,
                surfaceVariant = palette.surface,
                onBackground = palette.foreground,
                onSurface = palette.foreground,
                onSurfaceVariant = palette.foreground,
                outline = palette.muted
            ) else darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.background,
                secondary = palette.accent,
                background = palette.background,
                surface = palette.surface,
                surfaceContainer = palette.surface,
                surfaceContainerLow = palette.surface,
                surfaceContainerHigh = palette.surface,
                surfaceContainerHighest = palette.surface,
                surfaceVariant = palette.surface,
                onBackground = palette.foreground,
                onSurface = palette.foreground,
                onSurfaceVariant = palette.foreground,
                outline = palette.muted
            )
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = palette.isLight
                    isAppearanceLightNavigationBars = palette.isLight
                }
            }
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalMusicFlowPalette provides palette) {
                    Surface(color = palette.background) {
                        Box(Modifier.fillMaxSize()) {
                        MusicScreen(
                            player = playerState.value,
                            saved = { loadFavorites() },
                            save = { saveFavorites(it) },
                            themeStyle = themeStyle,
                            onThemeSelect = { selected ->
                                themeStyle = selected
                                getSharedPreferences("musicflow", MODE_PRIVATE).edit()
                                    .putString("ui_theme", selected.name).apply()
                            }
                        )
                        // Update manager stays off for this differently signed test package.
                        if (packageName == "com.musicflow.preview") MusicFlowAutoUpdate()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TvVideoSurfaceBridge.onMainResume()
    }

    override fun onPause() {
        TvVideoSurfaceBridge.onMainPause()
        super.onPause()
    }

    override fun onDestroy() {
        TvVideoSurfaceBridge.unregisterMainController(playerState.value)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    private fun loadFavorites(): List<Track> = runCatching {
        val text = getSharedPreferences("musicflow", MODE_PRIVATE).getString("favorites", "[]")
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            val stream = obj.optString("stream")
            if (id.isBlank() || id.startsWith("archive:") ||
                stream.contains("api.audius.co", ignoreCase = true) ||
                stream.contains("discoveryprovider.audius.co", ignoreCase = true) ||
                !(stream.startsWith("https://") ||
                    (id.startsWith("zaycev:") &&
                        Regex("zaycev://track/[0-9]{1,16}").matches(stream)))) null
            else {
                // Keep a favorite only when a usable non-retired stream exists.
                val alternatives = decodeStreams(obj.optString("alternatives"))
                val retired = id.startsWith("vk:")
                val fallback = if (retired) alternatives.firstOrNull() else null
                if (retired && fallback == null) null
                else Track(
                    fallback?.id ?: id,
                    obj.optString("title"),
                    obj.optString("artist"),
                    obj.optString("artwork").takeIf { it.startsWith("https://") },
                    fallback?.uri ?: stream,
                    obj.optLong("durationSec"),
                    obj.optString("collectionTitle").takeIf { it.isNotBlank() },
                    if (retired) alternatives.drop(1) else alternatives
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveFavorites(items: List<Track>) {
        val a = JSONArray()
        items.forEach { t ->
            a.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("artist", t.artist)
                put("artwork", t.artwork ?: "")
                put("stream", t.stream)
                put("durationSec", t.durationSec)
                put("collectionTitle", t.collectionTitle ?: "")
                put("alternatives", encodeStreams(t.alternatives))
            })
        }
        getSharedPreferences("musicflow", MODE_PRIVATE)
            .edit().putString("favorites", a.toString()).apply()
    }
}

@Composable
private fun MusicScreen(
    player: MediaController?,
    saved: () -> List<Track>,
    save: (List<Track>) -> Unit,
    themeStyle: MusicFlowStyle,
    onThemeSelect: (MusicFlowStyle) -> Unit
) {
    val palette = LocalMusicFlowPalette.current
    val scope = rememberCoroutineScope()
    val resultListState = rememberLazyListState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val homeSequence = remember { AtomicInteger(0) }
    var pendingHomeSong by remember { mutableStateOf<String?>(null) }
    var homePlaybackMessage by remember { mutableStateOf<String?>(null) }
    val favorites = remember { mutableStateListOf<Track>().also { it.addAll(saved()) } }
    var radioFavoritesRevision by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var appliedSearch by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.RELEVANCE) }
    // Unified MusicFlow opens the usual music home. Cinema TEST v12 is available as "Кино".
    var tab by remember { mutableIntStateOf(0) }
    var sectionMenu by remember { mutableStateOf(false) }
    var showAppearanceMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDirectDownloadDialog by remember { mutableStateOf(false) }
    var showTrackQualityDialog by remember { mutableStateOf(false) }
    var trackDownloadChoices by remember { mutableStateOf<List<MusicDownloadChoice>>(emptyList()) }
    var trackDownloadTitle by remember { mutableStateOf("") }
    var directDownloadUrl by remember { mutableStateOf("") }
    var directDownloadIsMovie by remember { mutableStateOf(false) }
    var downloadReferer by remember { mutableStateOf("") }
    var downloadUserAgent by remember { mutableStateOf("") }
    var hlsQualities by remember { mutableStateOf<List<HlsQuality>>(emptyList()) }
    var inspectedHlsUrl by remember { mutableStateOf("") }
    var chosenHlsIndex by remember { mutableStateOf<Int?>(null) }
    var dashQualities by remember { mutableStateOf<List<DashVideoQuality>>(emptyList()) }
    var inspectedDashUrl by remember { mutableStateOf("") }
    var chosenDashIndex by remember { mutableStateOf<Int?>(null) }
    var qualityChecking by remember { mutableStateOf(false) }
    var qualityMessage by remember { mutableStateOf("") }
    var headerEditorOpen by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var expandedPlayer by remember { mutableStateOf(false) }
    var queueCatalog by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playbackStatus by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var currentArtwork by remember { mutableStateOf<String?>(null) }
    var currentMediaId by remember { mutableStateOf("") }
    var trackTitle by remember { mutableStateOf("") }
    var trackArtist by remember { mutableStateOf("") }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    fun fetch(query: String = "") {
        val requested = query.trim()
        searchJob?.cancel()
        // Reset the next LazyColumn measurement, not only the old list layout.
        resultListState.requestScrollToItem(0)
        tracks = emptyList()
        appliedSearch = requested
        problem = null
        busy = requested.isNotEmpty()
        if (!busy) return
        searchJob = scope.launch {
            if (tab == 1) resultListState.scrollToItem(0)
            var failures = 0
            AppDiagnostics.info("Search", "request started")
            searchMusic(requested) { source, found, error ->
                if (error != null) {
                    failures++
                    AppDiagnostics.failure("Search", "source=$source", error)
                } else {
                    // Results arrive independently from different providers and
                    // can insert new album groups above the first visible item.
                    resultListState.requestScrollToItem(0)
                    tracks = combineMusicTracks(tracks, found.orEmpty())
                    AppDiagnostics.info("Search", "source=$source matches=" + found.orEmpty().size)
                }
            }
            busy = false
            if (failures > 0) {
                problem = if (tracks.isEmpty()) "Результаты недоступны. Проверьте подключение и повторите поиск."
                else "Часть источников временно недоступна."
            }
            if (tracks.isNotEmpty()) {
                val batch = tracks
                try {
                    val enriched = AlbumCatalog.enrichTracks(context, batch, requested)
                    resultListState.requestScrollToItem(0)
                    tracks = combineMusicTracks(tracks, enriched)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    AppDiagnostics.failure("Album", "enrich search results", failure)
                }
            }
        }
    }
    LaunchedEffect(Unit) { AppDiagnostics.info("Search", "Awaiting music search input") }

    // Back-fill album tags for favorites saved by older versions, but do not
    // request metadata for every song separately.
    LaunchedEffect(tab, favorites.size) {
        if (tab != 2 || favorites.isEmpty()) return@LaunchedEffect
        val pending = favorites.toList().filter { it.collectionTitle.isNullOrBlank() }
        val artistGroups = pending.groupBy { it.artist.trim() }.entries.take(4)
        for ((artist, items) in artistGroups) {
            val updates = runCatching {
                AlbumCatalog.enrichTracks(context, items, artist)
            }.getOrElse {
                AppDiagnostics.failure("Album", "enrich saved tracks", it)
                emptyList()
            }
            if (tab != 2) return@LaunchedEffect
            if (updates.isNotEmpty()) {
                val byId = updates.filter { !it.collectionTitle.isNullOrBlank() }
                    .associateBy { it.id }
                if (byId.isNotEmpty()) {
                    val refreshed = favorites.map { byId[it.id] ?: it }
                    favorites.clear()
                    favorites.addAll(refreshed)
                    save(refreshed)
                }
            }
        }
    }
    fun openTvFullscreen() {
        if (TvPlaybackHandoff.snapshot() == null) return
        if (!TvVideoSurfaceBridge.requestFullscreen()) return
        try {
            context.startActivity(Intent(context, TvFullscreenActivity::class.java))
        } catch (error: Exception) {
            TvVideoSurfaceBridge.cancelFullscreenRequest()
            AppDiagnostics.failure("TV", "open fullscreen", error)
        }
    }

    // Expand TV on a rotation into landscape only, never on return from fullscreen.
    val orientation = LocalConfiguration.current.orientation
    var previousOrientation by remember { mutableIntStateOf(orientation) }
    LaunchedEffect(orientation) {
        val rotatedToLandscape = previousOrientation != orientation &&
            orientation == Configuration.ORIENTATION_LANDSCAPE
        previousOrientation = orientation
        if (TvVideoSurfaceBridge.allowRotationLaunch(
                rotatedToLandscape,
                portrait = orientation == Configuration.ORIENTATION_PORTRAIT
            ) &&
            player?.currentMediaItem?.mediaId?.startsWith("ruvideo:") == true &&
            TvPlaybackHandoff.snapshot() != null) {
            openTvFullscreen()
        }
    }

    fun updateActiveMedia(item: MediaItem?) {
        // React to Media3 transitions immediately. The old polling-only path
        // could display the previous title/cover for up to 650 ms.
        currentMediaId = item?.mediaId.orEmpty()
        currentArtwork = item?.mediaMetadata?.artworkUri?.toString()
        trackTitle = item?.mediaMetadata?.title?.toString().orEmpty()
        trackArtist = item?.mediaMetadata?.artist?.toString().orEmpty()
    }

    DisposableEffect(player) {
        val active = player
        if (active == null) {
            onDispose { }
        } else {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateActiveMedia(mediaItem)
                }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    updateActiveMedia(active.currentMediaItem)
                }
            }
            active.addListener(listener)
            updateActiveMedia(active.currentMediaItem)
            onDispose { active.removeListener(listener) }
        }
    }

    // Preload images for the next chart entries while this song is playing.
    // CoverLookup uses a bounded IO semaphore and a persistent positive cache.
    // No audio streams are requested here.
    LaunchedEffect(player, currentMediaId) {
        val active = player ?: return@LaunchedEffect
        if (currentMediaId.isBlank() ||
            active.currentMediaItem?.mediaMetadata?.extras
                ?.getBoolean(MUSIC_CHART_PLAYLIST, false) != true) return@LaunchedEffect
        val next = active.currentMediaItemIndex
        for (index in (next + 1)..minOf(next + 2, active.mediaItemCount - 1)) {
            val neighbor = active.getMediaItemAt(index).mediaMetadata
            val nextArtist = neighbor.artist?.toString().orEmpty()
            val nextTitle = neighbor.title?.toString().orEmpty()
            val provided = neighbor.artworkUri?.toString()
            if (nextArtist.isNotBlank() && nextTitle.isNotBlank()) {
                launch {
                    runCatching {
                        CoverLookup.resolve(context, nextArtist, nextTitle, provided)
                    }.onFailure {
                        if (it !is CancellationException) {
                            AppDiagnostics.failure("Cover", "prefetch next chart artwork", it)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(player) {
        while (player != null) {
            playing = player.isPlaying
            playbackStatus = when {
                player.playerError != null -> "Поток недоступен"
                player.playbackState == androidx.media3.common.Player.STATE_BUFFERING -> "Подключение…"
                player.isPlaying -> "Сейчас играет"
                player.playbackState == androidx.media3.common.Player.STATE_ENDED -> "Воспроизведение завершено"
                !player.playWhenReady -> "На паузе"
                else -> null
            }
            currentMediaId = player.currentMediaItem?.mediaId.orEmpty()
            currentArtwork = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
            trackTitle = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
            trackArtist = player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            delay(650)
        }
    }

    fun queueAndPlay(
        list: List<Track>, index: Int,
        homePlaylist: Boolean = false,
        chartPlaylist: Boolean = false,
        radioPlaylist: Boolean = false
    ) {
        val p = player ?: run {
            problem = "Плеер подключается. Повторите попытку."
            AppDiagnostics.warning("Player", "play requested before connection")
            return
        }
        homeSequence.incrementAndGet()
        pendingHomeSong = null
        homePlaybackMessage = null
        queueCatalog = list.toList()
        if (radioPlaylist) {
            // Preserve the chosen station at its visible-list index, then allow
            // previous/next in order or Media3 shuffle from compact radio player.
            p.shuffleModeEnabled = false
            p.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        } else {
            p.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        AppDiagnostics.info("Player", "queue size=" + list.size + "; index=" + index)
        p.setMediaItems(list.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(t.stream)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.collectionTitle)
                        .setExtras(Bundle().apply {
                            putString(MUSIC_STREAMS, encodeStreams(t.streams()))
                            putString(MUSIC_SOURCE, t.id.substringBefore(':'))
                            putLong(MUSIC_DURATION_SECONDS, t.durationSec)
                            putBoolean(MUSIC_HOME_PLAYLIST, homePlaylist)
                            putBoolean(MUSIC_CHART_PLAYLIST, chartPlaylist)
                        })
                        .setArtworkUri(t.artwork?.let(Uri::parse))
                        .build()
                ).build()
        }, index, PlaybackJournal.resumePosition(
            context, list[index].id, list[index].durationSec
        ))
        p.prepare()
        p.play()
    }

    fun stopMusic() {
        val controller = player ?: return
        controller.stop()
        controller.clearMediaItems()
        queueCatalog = emptyList()
        expandedPlayer = false
        pendingHomeSong = null
        homeSequence.incrementAndGet()
        AppDiagnostics.info("Player", "Music stopped and playback queue cleared by user")
    }

    fun playHomeSong(song: HomeSong, section: List<HomeSong>) {
        val request = homeSequence.incrementAndGet()
        pendingHomeSong = song.id
        homePlaybackMessage = "Подключение: " + song.title
        scope.launch {
            // Resolve only the selected entry upfront. The remaining Zaycev links
            // are resolved lazily by Media3 as playback advances through the queue.
            val candidate = resolveRecommendedTrack(song.artist, song.title)
            if (request != homeSequence.get()) return@launch
            pendingHomeSong = null
            val queue = buildHomePlaylistQueue(song, section, candidate)
            if (queue == null) {
                homePlaybackMessage = "Для этой записи не найден доступный поток. Попробуй другую композицию."
                AppDiagnostics.warning("Home", "Selected song has no playable queue entry")
            } else {
                homePlaybackMessage = null
                queueAndPlay(queue.tracks, queue.selectedIndex, homePlaylist = true)
                AppDiagnostics.info("Home", "collection queue size=" + queue.tracks.size +
                    "; chosen=" + queue.selectedIndex)
            }
        }
    }

    fun playChartSong(song: TopSong, chartSection: List<TopSong>) {
        val request = homeSequence.incrementAndGet()
        pendingHomeSong = "chart:" + musicKey(song.artist) + ":" + musicKey(song.title)
        homePlaybackMessage = "Поиск записи: " + song.artist + " — " + song.title
        scope.launch {
            val candidate = try {
                findRecording(song.artist, song.title).firstOrNull()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                AppDiagnostics.failure("Charts", "play selection", failure)
                null
            }
            if (request != homeSequence.get()) return@launch
            pendingHomeSong = null
            val chartQueue = ChartPlaylistQueue.build(song, chartSection, candidate)
            if (chartQueue != null) {
                queueAndPlay(chartQueue.tracks, chartQueue.selectedIndex,
                    homePlaylist = true, chartPlaylist = true)
                AppDiagnostics.info("Charts", "queue=" + chartQueue.tracks.size +
                    "; selected=" + chartQueue.selectedIndex)
            } else {
                homePlaybackMessage = "Запись не найдена: " + song.artist + " — " + song.title
            }
        }
    }

    fun isCurrentRadioFavorite(): Boolean {
        radioFavoritesRevision // trigger refresh after saving the radio favorite
        return RussianRadioDirectory.favorites(context).any {
            it.mediaId == currentMediaId
        }
    }

    fun toggleCurrentFavorite() {
        val item = player?.currentMediaItem ?: return
        val id = item.mediaId
        if (id.startsWith("radio:")) {
            val uuid = id.removePrefix("radio:")
            val savedStations = RussianRadioDirectory.favorites(context)
            val updated = if (savedStations.any { it.uuid == uuid }) {
                savedStations.filterNot { it.uuid == uuid }
            } else {
                val stream = item.localConfiguration?.uri?.toString().orEmpty()
                if (!RussianRadioDirectory.isPublicStream(stream)) return
                val station = RussianRadioStation(
                    uuid = uuid,
                    name = item.mediaMetadata.title?.toString().orEmpty()
                        .ifBlank { "Радиостанция" },
                    stream = stream,
                    favicon = item.mediaMetadata.artworkUri?.toString()
                        ?.takeIf { it.startsWith("https://") },
                    tags = "", codec = "", bitrate = 0
                )
                (listOf(station) + savedStations).take(100)
            }
            RussianRadioDirectory.saveFavorites(context, updated)
            radioFavoritesRevision++
            return
        }
        if (favorites.any { it.id == id }) {
            favorites.removeAll { it.id == id }
        } else {
            val existing = queueCatalog.firstOrNull { it.id == id }
                ?: tracks.firstOrNull { it.id == id }
            val currentTrack = existing ?: item.localConfiguration?.uri?.toString()?.let { uri ->
                Track(
                    id = id,
                    title = item.mediaMetadata.title?.toString() ?: "Без названия",
                    artist = item.mediaMetadata.artist?.toString() ?: "Неизвестный исполнитель",
                    artwork = item.mediaMetadata.artworkUri?.toString(),
                    stream = uri,
                    durationSec = player.duration.coerceAtLeast(0) / 1000
                )
            }
            if (currentTrack != null) favorites.add(0, currentTrack)
        }
        save(favorites.toList())
    }

    fun downloadCurrentSong() {
        val current = player?.currentMediaItem
        if (current == null || current.mediaId.startsWith("radio:")) {
            MediaDownloads.showResult(context, DownloadPlanResult.Rejected(
                "Сначала запустите музыкальную композицию"
            ))
            return
        }
        val source = queueCatalog.firstOrNull { it.id == current.mediaId }
            ?: tracks.firstOrNull { it.id == current.mediaId }
            ?: favorites.firstOrNull { it.id == current.mediaId }
        val streams = (source?.streams().orEmpty() +
            decodeStreams(current.mediaMetadata.extras?.getString(MUSIC_STREAMS)) +
            listOfNotNull(current.localConfiguration?.uri?.toString()?.let {
                MusicStream(current.mediaId, it)
            })).distinctBy { it.uri }
        trackDownloadTitle = listOf(trackArtist, trackTitle)
            .filter(String::isNotBlank).joinToString(" — ")
        trackDownloadChoices = MusicDownloadChoicePolicy.choices(streams)
        if (trackDownloadChoices.isEmpty()) {
            MediaDownloads.showResult(context, DownloadPlanResult.Rejected(
                "Нет прямой ссылки доступного файла для этой песни. " +
                    "Попробуйте «Загрузить по ссылке»."
            ))
            return
        }
        showTrackQualityDialog = true
    }

    val visible = when (tab) {
        2 -> favorites.toList()
        1 -> filterAndSortTracks(tracks, appliedSearch, searchMode, sortMode)
        else -> tracks
    }
    LaunchedEffect(tab, appliedSearch, searchMode, sortMode, tracks) {
        if (tab == 1) {
            resultListState.requestScrollToItem(0)
            resultListState.scrollToItem(0)
        } else if (tab == 2) resultListState.scrollToItem(0)
    }
    // Android 15+ draws behind status and navigation bars by default.
    // Insets belong to the whole screen, including header actions and floating TV.
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(palette.gradientTop, palette.background, palette.gradientBottom)
            ))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
    Column(
        Modifier.fillMaxSize()
            .background(palette.background.copy(
                alpha = if (palette.isLight) 0.58f else 0.64f
            ))
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MusicFlow", style = MaterialTheme.typography.titleLarge,
                color = palette.foreground, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (PlayerMinimizePolicy.canShowNowPlaying(
                    hasTrack = trackTitle.isNotBlank(),
                    isTelevision = currentMediaId.startsWith("ruvideo:")
                )) {
                TextButton(
                    onClick = { expandedPlayer = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Text("Сейчас играет", style = MaterialTheme.typography.labelSmall,
                        maxLines = 1)
                }
            }
            IconButton(onClick = {
                tab = 1
                if (search.isNotBlank()) fetch(search)
            }) { Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = palette.foreground) }
            Box {
                IconButton(onClick = { showAppearanceMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Настройки",
                        tint = palette.muted)
                }
                DropdownMenu(expanded = showAppearanceMenu,
                    onDismissRequest = { showAppearanceMenu = false },
                    modifier = Modifier.musicFlowMenuSurface(palette),
                    containerColor = palette.surface,
                    shadowElevation = 14.dp) {
                    DropdownMenuItem(text = { Text("Оформление") }, onClick = {
                        showAppearanceMenu = false
                        showThemePicker = true
                    })
                    DropdownMenuItem(text = { Text("Таймер сна") }, onClick = {
                        showAppearanceMenu = false
                        showSleepTimerDialog = true
                    })
                    DropdownMenuItem(text = { Text("История прослушивания") }, onClick = {
                        showAppearanceMenu = false
                        tab = 5
                    })
                    DropdownMenuItem(text = { Text("Загрузить по ссылке · TEST") }, onClick = {
                        showAppearanceMenu = false
                        showDirectDownloadDialog = true
                    })
                    DropdownMenuItem(text = { Text("Открыть загрузки") }, onClick = {
                        showAppearanceMenu = false
                        MediaDownloads.openDownloads(context)
                    })
                    DropdownMenuItem(text = { Text("Диагностика") }, onClick = {
                        showAppearanceMenu = false
                        tab = 4
                    })
                    DropdownMenuItem(text = { Text("О приложении · обновления") }, onClick = {
                        showAppearanceMenu = false
                        showAboutDialog = true
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { sectionMenu = true },
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(when (tab) {
                        2 -> "Избранное"
                        3 -> "ТВ"
                        7 -> "Кино"
                        8 -> "Загрузки"
                        6 -> "Радио"
                        5 -> "История"
                        else -> "Музыка"
                    })
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = sectionMenu,
                    onDismissRequest = { sectionMenu = false },
                    modifier = Modifier.musicFlowMenuSurface(palette),
                    containerColor = palette.surface,
                    shadowElevation = 14.dp) {
                    listOf(0 to "Музыка", 2 to "Избранное", 6 to "Радио",
                        5 to "История", 3 to "ТВ", 7 to "Кино",
                        8 to "Загрузки")
                        .forEach { (index, label) ->
                            DropdownMenuItem(text = { Text(label) },
                                onClick = {
                                    tab = index
                                    sectionMenu = false
                                })
                        }
                }
            }
            if (tab == 1 || tab == 4) {
                Spacer(Modifier.width(12.dp))
                Text(if (tab == 1) "Поиск музыки" else "Диагностика",
                    color = palette.accent, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(5.dp))
        if (tab == 0) {
            MusicHomeScreen(
                onSearch = { suggested ->
                    tab = 1
                    search = suggested
                    if (suggested.isNotBlank()) fetch(suggested)
                },
                onPlay = { song, section -> playHomeSong(song, section) },
                onPlayChart = { song, section -> playChartSong(song, section) },
                pendingSongId = pendingHomeSong,
                playbackMessage = homePlaybackMessage,
                modifier = Modifier.weight(1f)
            )
        } else if (tab == 3) {
            RuVideoHubTvScreen(player = player, modifier = Modifier.weight(1f))
        } else if (tab == 7) {
            CinemaLabScreen(modifier = Modifier.weight(1f))
        } else if (tab == 8) {
            MusicDownloadsScreen(
                onAddLink = { showDirectDownloadDialog = true },
                modifier = Modifier.weight(1f)
            )
        } else if (tab == 4) {
            DiagnosticsScreen(modifier = Modifier.weight(1f))
        } else if (tab == 6) {
            RussianRadioScreen(
                activeId = currentMediaId,
                playing = playing,
                onPlay = { station, shown ->
                    val (ordered, index) = RadioQueuePolicy.playlist(station, shown)
                    val tracks = ordered.map {
                        (if (it.uuid == station.uuid) station else it).toTrack()
                    }
                    queueAndPlay(tracks, index, radioPlaylist = true)
                },
                onStop = { stopMusic() },
                modifier = Modifier.weight(1f)
            )
        } else if (tab == 5) {
            MusicPlaybackHistoryScreen(
                activeId = currentMediaId,
                onPlay = { track -> queueAndPlay(listOf(track), 0) },
                modifier = Modifier.weight(1f)
            )
        } else {
        if (tab == 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SearchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = searchMode == mode,
                        onClick = { searchMode = mode },
                        label = { Text(mode.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Исполнитель или название песни") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    fetch(search)
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { fetch(search) },
                enabled = !busy && search.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Искать")
            }
            Spacer(Modifier.height(6.dp))
            Text("Сортировка", color = palette.muted, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(mode.label) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (busy && tab != 2) LinearProgressIndicator(Modifier.fillMaxWidth())
        problem?.let {
            Text(it, color = Color(0xFFFFB3A8), modifier = Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fetch(search) }) {
                    Text("Повторить")
                }
            }
        }
        Text(
            if (tab == 2) "Сохранённые треки" else if (tab == 1) "Результаты: " + visible.size else "Музыка онлайн",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val albumGroups = groupTracksByAlbum(visible)
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = resultListState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            albumGroups.forEach { albumGroup ->
                item(key = "section:" + albumGroup.id) {
                    if (albumGroup.albumTitle != null) {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                            Text(
                                "♫ " + albumGroup.albumTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                albumGroup.artist.orEmpty() + " · " +
                                    albumGroup.songs.size + " композиций",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted
                            )
                        }
                    } else if (albumGroups.size > 1) {
                        Text("Отдельные треки", color = palette.muted,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
                items(albumGroup.songs, key = { "track:" + it.id }) { track ->
                val cover by rememberCoverArt(track.artist, track.title, track.artwork)
                val isFavorite = favorites.any { it.id == track.id }
                val isActive = currentMediaId == track.id
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .musicFlowGlass(palette, 12.dp, active = isActive).clickable {
                            val albumQueue = if (albumGroup.albumTitle != null &&
                                albumGroup.songs.size > 1) albumGroup.songs else visible
                            val index = albumQueue.indexOfFirst { it.id == track.id }
                            if (index >= 0) queueAndPlay(albumQueue, index)
                        }.padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(cover),
                        contentDescription = "Обложка",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(9.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.muted)
                        if (isActive) {
                            Text(
                                playbackStatus.orEmpty(),
                                color = palette.accent,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        track.collectionTitle?.takeIf { it.isNotBlank() }?.let {
                            Text("Альбом: " + it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                 color = palette.muted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (track.durationSec > 0L) {
                            Text(
                                formatTrackDuration(track.durationSec),
                                color = palette.accent, style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    TextButton(onClick = {
                        if (isFavorite) favorites.removeAll { it.id == track.id }
                        else favorites.add(0, track)
                        save(favorites.toList())
                    }) { Text(if (isFavorite) "♥" else "♡") }
                }
                }
            }
            if (visible.isEmpty() && !busy) item {
                Text(
                    when {
                        tab == 2 -> "Избранного пока нет"
                        tab == 1 && appliedSearch.isBlank() -> "Введите название песни или исполнителя для поиска"
                        tab == 1 && tracks.isNotEmpty() -> "Нет совпадений по выбранному полю. Попробуй режим «Везде»."
                        else -> "Треки не найдены"
                    },
                    color = palette.muted, modifier = Modifier.padding(12.dp)
                )
            }
        }
        }
        if (trackTitle.isNotBlank() && !currentMediaId.startsWith("ruvideo:")) {
            val isRadio = currentMediaId.startsWith("radio:")
            val musicMiniCover by rememberCoverArt(trackArtist, trackTitle, currentArtwork)
            val radioStreamUrl = player?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            val radioMiniCover by produceState<String?>(
                initialValue = currentArtwork,
                currentMediaId, trackTitle, currentArtwork, radioStreamUrl
            ) {
                value = currentArtwork
                if (isRadio && RussianRadioDirectory.isPublicStream(radioStreamUrl)) {
                    val station = RussianRadioStation(
                        currentMediaId.removePrefix("radio:"),
                        trackTitle, radioStreamUrl, currentArtwork, "", "", 0
                    )
                    value = RadioArtworkCatalog.resolve(context, station) ?: currentArtwork
                }
            }
            val miniCover = if (isRadio) radioMiniCover else musicMiniCover
            Column(
                modifier = Modifier.fillMaxWidth()
                    .musicFlowGlass(palette, radius = 18.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).clickable { expandedPlayer = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(palette.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRadio) {
                                RadioStationArtwork(
                                    name = trackTitle,
                                    logo = miniCover,
                                    modifier = Modifier.fillMaxSize(),
                                    radius = 12.dp
                                )
                            } else {
                            Icon(Icons.Filled.MusicNote, contentDescription = null,
                                tint = palette.accent.copy(alpha = 0.6f),
                                modifier = Modifier.size(26.dp))
                            if (!miniCover.isNullOrBlank()) {
                                Image(
                                    painter = rememberAsyncImagePainter(miniCover),
                                    contentDescription = "Обложка текущей композиции",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trackTitle, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (!isRadio) Text(trackArtist, color = palette.muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            Text(playbackStatus.orEmpty(), color = palette.accent,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = {
                        if (player?.playWhenReady == true && player.playerError == null) player.pause()
                        else {
                            if (player?.playerError != null ||
                                player?.playbackState == androidx.media3.common.Player.STATE_IDLE)
                                player?.prepare()
                            player?.play()
                        }
                    }) {
                        Icon(
                            if (player?.playWhenReady == true && player.playerError == null) androidx.compose.material.icons.Icons.Filled.Pause
                            else androidx.compose.material.icons.Icons.Filled.PlayArrow,
                            contentDescription = if (player?.playWhenReady == true && player.playerError == null) "Пауза" else "Воспроизвести",
                            tint = palette.foreground
                        )
                    }
                    PlayerEqualizerButton(
                        modifier = Modifier.size(42.dp),
                        tint = palette.accent
                    )
                    if (isRadio) {
                        IconButton(onClick = { toggleCurrentFavorite() }) {
                            Icon(
                                if (isCurrentRadioFavorite())
                                    androidx.compose.material.icons.Icons.Filled.Favorite
                                else androidx.compose.material.icons.Icons.Filled.FavoriteBorder,
                                contentDescription = "Радио в избранное",
                                tint = palette.accent
                            )
                        }
                        IconButton(onClick = { stopMusic() }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Close,
                                contentDescription = "Закрыть радио-плеер",
                                tint = palette.foreground
                            )
                        }
                    } else {
                    IconButton(onClick = { player?.seekToNextMediaItem() }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.SkipNext,
                            contentDescription = "Следующий трек",
                            tint = palette.foreground
                        )
                    }
                    IconButton(onClick = { stopMusic() }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.Close,
                            contentDescription = "Выключить музыку",
                            tint = palette.foreground
                        )
                    }
                    }
                }
                if (!isRadio) LinearProgressIndicator(
                    progress = { if (durationMs > 0L) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    color = palette.accent,
                    trackColor = palette.accent.copy(alpha = 0.23f)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    // Floating TV controls do not occupy list height. Channel rows scroll below.
    if (currentMediaId.startsWith("ruvideo:") && player != null) {
        TvMiniPlayer(
            player = player,
            channelTitle = trackTitle,
            onExpand = {
                openTvFullscreen()
            },
            onStop = {
                stopMusic()
                TvPlaybackHandoff.clear()
            }
        )
    }
    }
    if (showAboutDialog) {
        MusicFlowAboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showSleepTimerDialog) {
        MusicFlowSleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    }
    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Liquid Glass · оформление MusicFlow") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MusicFlowStyle.entries.forEach { style ->
                        val choice = musicFlowPalette(style)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onThemeSelect(style)
                                    showThemePicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(34.dp)
                                    .musicFlowGlass(choice, radius = 11.dp,
                                        active = style == themeStyle),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(choice.accent))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(style.title, fontWeight = FontWeight.SemiBold)
                                Text(style.subtitle,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (style == themeStyle) Text("✓", color = palette.accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemePicker = false }) { Text("Закрыть") }
            }
        )
    }
    LaunchedEffect(directDownloadUrl) {
        if (directDownloadUrl.trim() != inspectedHlsUrl) {
            hlsQualities = emptyList()
            chosenHlsIndex = null
        }
        if (directDownloadUrl.trim() != inspectedDashUrl) {
            dashQualities = emptyList()
            chosenDashIndex = null
        }
        qualityMessage = ""
    }
    if (showTrackQualityDialog) {
        AlertDialog(
            onDismissRequest = { showTrackQualityDialog = false },
            title = { Text("Выбрать качество скачивания") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("MusicFlow предлагает только доступные исходные потоки. " +
                        "Если сервер не сообщил битрейт, его качество неизвестно; " +
                        "приложение не создаёт искусственные 320 кбит/с.",
                        style = MaterialTheme.typography.bodySmall)
                    trackDownloadChoices.forEach { choice ->
                        OutlinedButton(
                            onClick = {
                                showTrackQualityDialog = false
                                if (choice.isAdaptive) {
                                    directDownloadUrl = choice.url
                                    directDownloadIsMovie = false
                                    showDirectDownloadDialog = true
                                } else {
                                    MediaDownloads.showResult(context, MediaDownloads.enqueue(
                                        context, choice.url, trackDownloadTitle,
                                        DownloadMediaKind.MUSIC
                                    ))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(choice.label) }
                    }
                    if (trackDownloadChoices.any { it.isAdaptive }) {
                        Text("Для HLS после выбора откроется проверка плейлиста " +
                            "с доступными вариантами битрейта.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackQualityDialog = false }) { Text("Закрыть") }
            }
        )
    }
    if (showDirectDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDirectDownloadDialog = false },
            title = { Text("Скачать медиафайл · TEST") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Обычные MP4/MP3 сохраняются в папке Загрузки. " +
                        "HLS/DASH загружаются для офлайн-просмотра в Media3. " +
                        "DRM и ограничения источников не обходятся.")
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val raw = clipboard.primaryClip?.getItemAt(0)
                            ?.coerceToText(context)?.toString().orEmpty()
                        val pasted = ClipboardDownloadUrl.extract(raw)
                        if (pasted != null) {
                            directDownloadUrl = pasted
                        } else {
                            android.widget.Toast.makeText(context,
                                "В буфере нет HTTPS-ссылки на файл",
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Вставить из буфера") }
                    OutlinedTextField(
                        value = directDownloadUrl,
                        onValueChange = { directDownloadUrl = it },
                        label = { Text("Ссылка на MP4, MP3, HLS или DASH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val adaptive = OfflineDownloadRoute.classify(directDownloadUrl)
                    if (adaptive == OfflineTransfer.ADAPTIVE_HLS ||
                        adaptive == OfflineTransfer.ADAPTIVE_DASH) {
                        OutlinedButton(
                            onClick = {
                                qualityChecking = true
                                qualityMessage = ""
                                val targetUrl = directDownloadUrl.trim()
                                val targetMode = adaptive
                                scope.launch {
                                    try {
                                        val headerMap = DownloadHttpHeaders.validated(
                                            downloadReferer, downloadUserAgent
                                        )
                                        if (targetMode == OfflineTransfer.ADAPTIVE_HLS) {
                                            val found = kotlinx.coroutines.withContext(
                                                kotlinx.coroutines.Dispatchers.IO
                                            ) {
                                                HlsQualityInspector.inspect(targetUrl, headerMap)
                                            }
                                            if (targetUrl == directDownloadUrl.trim()) {
                                                hlsQualities = found
                                                inspectedHlsUrl = targetUrl
                                                chosenHlsIndex = null
                                                qualityMessage = if (found.isEmpty())
                                                    "HLS без вариантов качества: загружается исходный поток."
                                                else "Выберите разрешение видео или оставьте «Авто»."
                                            }
                                        } else {
                                            val found = kotlinx.coroutines.withContext(
                                                kotlinx.coroutines.Dispatchers.IO
                                            ) { DashVideoQualityInspector.inspect(targetUrl) }
                                            if (targetUrl == directDownloadUrl.trim()) {
                                                dashQualities = found
                                                inspectedDashUrl = targetUrl
                                                chosenDashIndex = null
                                                qualityMessage = if (found.isEmpty())
                                                    "DASH не содержит выбираемых вариантов видео."
                                                else "Выберите качество DASH или оставьте «Авто»."
                                            }
                                        }
                                    } catch (error: Exception) {
                                        qualityMessage = "Не удалось проверить качество: " +
                                            (error.message ?: "неизвестная ошибка")
                                    } finally {
                                        qualityChecking = false
                                    }
                                }
                            },
                            enabled = !qualityChecking,
                        ) {
                            Text(if (qualityChecking) "Проверяем…" else
                                if (adaptive == OfflineTransfer.ADAPTIVE_HLS)
                                    "Выбрать качество HLS" else "Выбрать качество DASH")
                        }
                        if (qualityMessage.isNotBlank()) {
                            Text(qualityMessage, style = MaterialTheme.typography.bodySmall)
                        }
                        if (adaptive == OfflineTransfer.ADAPTIVE_HLS &&
                            hlsQualities.isNotEmpty() &&
                            inspectedHlsUrl == directDownloadUrl.trim()) {
                            Text("Качество видео HLS", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = chosenHlsIndex == null,
                                    onClick = { chosenHlsIndex = null },
                                    label = { Text("Авто") }
                                )
                                hlsQualities.forEach { variant ->
                                    FilterChip(
                                        selected = chosenHlsIndex == variant.index,
                                        onClick = { chosenHlsIndex = variant.index },
                                        enabled = !variant.externalAudio,
                                        label = { Text(variant.label) }
                                    )
                                }
                            }
                            if (hlsQualities.any { it.externalAudio }) {
                                Text("Варианты с отдельным аудио доступны в «Авто», " +
                                    "чтобы не потерять звуковую дорожку.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (adaptive == OfflineTransfer.ADAPTIVE_DASH &&
                            dashQualities.isNotEmpty() &&
                            inspectedDashUrl == directDownloadUrl.trim()) {
                            Text("Качество видео DASH", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = chosenDashIndex == null,
                                    onClick = { chosenDashIndex = null },
                                    label = { Text("Авто") }
                                )
                                dashQualities.forEachIndexed { index, variant ->
                                    FilterChip(
                                        selected = chosenDashIndex == index,
                                        onClick = { chosenDashIndex = index },
                                        label = { Text(variant.label) }
                                    )
                                }
                            }
                            Text("При выборе DASH сохраняется и подходящая аудиодорожка. " +
                                "Оригинальный поток остаётся в офлайн-кеше.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (adaptive == OfflineTransfer.DIRECT_FILE &&
                        directDownloadIsMovie) {
                        Text("Прямой MP4 имеет одно исходное качество. " +
                            "Для выбора 360p/720p/1080p источник должен предоставить " +
                            "несколько файлов или HLS/DASH.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { headerEditorOpen = !headerEditorOpen }) {
                        Text(if (headerEditorOpen) "Скрыть заголовки" else "Дополнительные HTTP-заголовки")
                    }
                    if (headerEditorOpen) {
                        OutlinedTextField(value = downloadReferer,
                            onValueChange = { downloadReferer = it },
                            label = { Text("Referer (HTTPS)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = downloadUserAgent,
                            onValueChange = { downloadUserAgent = it },
                            label = { Text("User-Agent") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Заголовки применяются при скачивании MP3/MP4 и при проверке HLS-плейлиста. " +
                            "Media3 пока не передаёт их сегментам HLS/DASH. Пароли и токены не вводите.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !directDownloadIsMovie,
                            onClick = { directDownloadIsMovie = false },
                            label = { Text("Музыка") })
                        FilterChip(selected = directDownloadIsMovie,
                            onClick = { directDownloadIsMovie = true },
                            label = { Text("Фильм") })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val kind = if (directDownloadIsMovie)
                        DownloadMediaKind.VIDEO else DownloadMediaKind.MUSIC
                    val outcome = MediaDownloads.enqueue(
                        context, directDownloadUrl,
                        if (directDownloadIsMovie) "Фильм" else "Музыка", kind,
                        referer = downloadReferer.ifBlank { null },
                        userAgent = downloadUserAgent.ifBlank { null },
                        hlsVariantIndex = chosenHlsIndex.takeIf {
                            inspectedHlsUrl == directDownloadUrl.trim()
                        },
                        dashStreamKeys = chosenDashIndex?.let { index ->
                            if (inspectedDashUrl == directDownloadUrl.trim())
                                dashQualities.getOrNull(index)?.streamKeys else null
                        }
                    )
                    MediaDownloads.showResult(context, outcome)
                    if (outcome is DownloadPlanResult.Accepted) {
                        showDirectDownloadDialog = false
                        directDownloadUrl = ""
                        chosenHlsIndex = null
                        hlsQualities = emptyList()
                        chosenDashIndex = null
                        dashQualities = emptyList()
                    }
                }) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = { showDirectDownloadDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    if (expandedPlayer && player != null && trackTitle.isNotBlank() && !currentMediaId.startsWith("ruvideo:")) {
        FullPlayerDialog(
            player = player,
            title = trackTitle,
            artist = trackArtist,
            artwork = currentArtwork,
            playing = playing,
            playbackStatus = playbackStatus,
            positionMs = positionMs,
            durationMs = durationMs,
            favorite = if (currentMediaId.startsWith("radio:"))
                isCurrentRadioFavorite() else favorites.any { it.id == currentMediaId },
            onFavorite = { toggleCurrentFavorite() },
            onDownload = { downloadCurrentSong() },
            onStop = { stopMusic() },
            availableTracks = combineMusicTracks(queueCatalog, tracks + favorites.toList()),
            onPlayAlbumTrack = { track ->
                queueAndPlay(listOf(track), 0)
            },
            onClose = { expandedPlayer = false }
        )
    }
}

internal fun combineMusicTracks(existing: List<Track>, incoming: List<Track>): List<Track> {
    val combined = existing.toMutableList()
    for (track in incoming) {
        val index = combined.indexOfFirst {
            it.id == track.id || (track.artist != "Неизвестный исполнитель" &&
                recordingMatches(it, track.artist, track.title, track.durationSec))
        }
        if (index < 0) {
            combined.add(track)
            continue
        }
        val original = combined[index]
        // Retain the original playable source; other results stay as alternatives.
        val primary = original
        val secondary = track
        combined[index] = primary.copy(
            artwork = primary.artwork?.takeIf { it.isNotBlank() } ?: secondary.artwork,
            collectionTitle = primary.collectionTitle?.takeIf { it.isNotBlank() }
                ?: secondary.collectionTitle,
            durationSec = primary.durationSec.takeIf { it > 0 } ?: secondary.durationSec,
            alternatives = (primary.streams() + secondary.streams()).distinctBy { it.uri }
                .drop(1).take(11)
        )
    }
    return combined
}
