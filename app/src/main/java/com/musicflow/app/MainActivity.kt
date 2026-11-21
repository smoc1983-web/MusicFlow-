package com.musicflow.app

import android.content.ComponentName
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
import androidx.compose.material.icons.filled.Close
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
        AppDiagnostics.info("App", "MusicFlow 0.7.33 launched; Android SDK=" + android.os.Build.VERSION.SDK_INT)
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
                mutableStateOf(MusicFlowStyle.fromSaved(
                    getSharedPreferences("musicflow", MODE_PRIVATE)
                        .getString("ui_theme", MusicFlowStyle.AB_ICY_NIGHT.name)
                ))
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
                        MusicFlowAutoUpdate()
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
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var appliedSearch by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.RELEVANCE) }
    var tab by remember { mutableIntStateOf(0) }
    var sectionMenu by remember { mutableStateOf(false) }
    var showAppearanceMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
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
        TvVideoSurfaceBridge.requestFullscreen()
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
        if (rotatedToLandscape &&
            player?.currentMediaItem?.mediaId?.startsWith("ruvideo:") == true &&
            TvPlaybackHandoff.snapshot() != null) {
            openTvFullscreen()
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

    fun queueAndPlay(list: List<Track>, index: Int, homePlaylist: Boolean = false) {
        val p = player ?: run {
            problem = "Плеер подключается. Повторите попытку."
            AppDiagnostics.warning("Player", "play requested before connection")
            return
        }
        homeSequence.incrementAndGet()
        pendingHomeSong = null
        homePlaybackMessage = null
        queueCatalog = list.toList()
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

    fun toggleCurrentFavorite() {
        val item = player?.currentMediaItem ?: return
        val id = item.mediaId
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

    val visible = when (tab) {
        2 -> favorites.toList()
        1 -> filterAndSortTracks(tracks, appliedSearch, searchMode, sortMode)
        else -> tracks
    }
    LaunchedEffect(tab, appliedSearch, searchMode, sortMode) {
        if (tab == 1 || tab == 2) resultListState.scrollToItem(0)
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
            .background(palette.background.copy(alpha = 0.83f))
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
                    DropdownMenuItem(text = { Text("Звук · эквалайзер") }, onClick = {
                        showAppearanceMenu = false
                        showEqualizerDialog = true
                    })
                    DropdownMenuItem(text = { Text("Таймер сна") }, onClick = {
                        showAppearanceMenu = false
                        showSleepTimerDialog = true
                    })
                    DropdownMenuItem(text = { Text("История прослушивания") }, onClick = {
                        showAppearanceMenu = false
                        tab = 5
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
                        5 to "История", 3 to "ТВ")
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
                pendingSongId = pendingHomeSong,
                playbackMessage = homePlaybackMessage,
                modifier = Modifier.weight(1f)
            )
        } else if (tab == 3) {
            RuVideoHubTvScreen(player = player, modifier = Modifier.weight(1f))
        } else if (tab == 4) {
            DiagnosticsScreen(modifier = Modifier.weight(1f))
        } else if (tab == 6) {
            RussianRadioScreen(
                activeId = currentMediaId,
                playing = playing,
                onPlay = { station -> queueAndPlay(listOf(station.toTrack()), 0) },
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
            val miniCover by rememberCoverArt(trackArtist, trackTitle, currentArtwork)
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
                        Image(
                            painter = rememberAsyncImagePainter(miniCover),
                            contentDescription = "Обложка текущей композиции",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trackTitle, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(trackArtist, color = palette.muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                LinearProgressIndicator(
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
    if (showEqualizerDialog) {
        MusicFlowEqualizerDialog(onDismiss = { showEqualizerDialog = false })
    }
    if (showSleepTimerDialog) {
        MusicFlowSleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    }
    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Оформление MusicFlow") },
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
                            Box(Modifier.size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(choice.background)) {
                                Box(Modifier.align(Alignment.Center).size(16.dp)
                                    .clip(RoundedCornerShape(7.dp))
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
            favorite = favorites.any { it.id == currentMediaId },
            onFavorite = { toggleCurrentFavorite() },
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
