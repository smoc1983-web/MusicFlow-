package com.musicflow.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = Color(0xFF110F18)
private val PanelBackground = Color(0xFF211A2C)
private val Highlight = Color(0xFFAC80FF)

@Composable
internal fun FullPlayerDialog(
    player: MediaController,
    title: String,
    artist: String,
    artwork: String?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onStop: () -> Unit,
    onPlayAlbumTrack: (Track) -> Unit,
    onClose: () -> Unit
) {
    val resolvedArtwork by rememberCoverArt(artist, title, artwork)
    val albumHint = player.currentMediaItem?.mediaMetadata?.albumTitle?.toString()
    val albumScope = rememberCoroutineScope()
    var showAlbum by remember { mutableStateOf(false) }
    var albumInfo by remember { mutableStateOf<AlbumListing?>(null) }
    var albumLoading by remember { mutableStateOf(false) }
    var albumNote by remember { mutableStateOf<String?>(null) }
    var pendingAlbumTrack by remember { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    LaunchedEffect(showAlbum, title, artist, albumHint) {
        if (!showAlbum) return@LaunchedEffect
        albumInfo = null
        albumNote = null
        albumLoading = true
        try {
            albumInfo = AlbumCatalog.lookup(artist, title, albumHint)
            if (albumInfo == null) {
                albumNote = "Подтверждённый список альбома не найден в открытом каталоге."
            }
        } catch (e: Exception) {
            AppDiagnostics.failure("Album", "load album", e)
            albumNote = "Не удалось загрузить треклист альбома."
        } finally { albumLoading = false }
    }

    var sliderDragging by remember(player) { mutableStateOf(false) }
    var seekPending by remember(player) { mutableStateOf(false) }
    var sliderPositionMs by remember(player) { mutableLongStateOf(positionMs.coerceAtLeast(0L)) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeat by remember { mutableIntStateOf(player.repeatMode) }
    var currentIndex by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var queue by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // UI follows playback while idle, but never pulls the slider back during a drag.
    LaunchedEffect(positionMs, sliderDragging, seekPending) {
        if (!sliderDragging && !seekPending) {
            sliderPositionMs = positionMs.coerceAtLeast(0L)
        }
    }
    // ExoPlayer seeks asynchronously; hold the chosen position until its state catches up.
    LaunchedEffect(seekPending) {
        if (seekPending) {
            delay(900L)
            seekPending = false
        }
    }
    LaunchedEffect(currentIndex) {
        sliderDragging = false
        seekPending = false
        sliderPositionMs = player.currentPosition.coerceAtLeast(0L)
    }

    LaunchedEffect(player) {
        while (true) {
            shuffle = player.shuffleModeEnabled
            repeat = player.repeatMode
            currentIndex = player.currentMediaItemIndex
            queue = (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index)
            }
            delay(650)
        }
    }

    Dialog(
        onDismissRequest = {
            if (showAlbum) showAlbum = false
            else if (showQueue) showQueue = false
            else onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        if (showAlbum) showAlbum = false
                        else if (showQueue) showQueue = false else onClose()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Text(
                        if (showAlbum) "Композиции альбома"
                        else if (showQueue) "Очередь воспроизведения" else "Сейчас играет",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            showAlbum = false
                            showQueue = !showQueue
                        }) {
                            Text(if (showQueue || showAlbum) "Плеер" else "Очередь")
                        }
                        IconButton(onClick = onStop) {
                            Icon(Icons.Filled.Close, contentDescription = "Выключить музыку",
                                tint = Color.White)
                        }
                    }
                }
                if (showAlbum) {
                    Spacer(Modifier.height(10.dp))
                    if (albumLoading) {
                        CircularProgressIndicator(color = Highlight)
                        Spacer(Modifier.height(8.dp))
                        Text("Загрузка треклиста…", color = Color.LightGray)
                    }
                    albumNote?.let { Text(it, color = Color(0xFFFFC6BD)) }
                    albumInfo?.let { album ->
                        Text(album.title, style = MaterialTheme.typography.titleLarge,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(album.artist + " · " + album.tracks.size + " композиций",
                            color = Color.LightGray)
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(album.tracks) { idx, song ->
                                Row(Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanelBackground)
                                    .clickable(enabled = pendingAlbumTrack == null) {
                                        pendingAlbumTrack = song.title
                                        albumNote = null
                                        albumScope.launch {
                                            val resolved = resolveRecommendedTrack(
                                                song.artist, song.title
                                            )
                                            pendingAlbumTrack = null
                                            if (resolved == null) {
                                                albumNote = "Аудиопоток выбранной композиции недоступен."
                                            } else {
                                                showAlbum = false
                                                onPlayAlbumTrack(
                                                    resolved.copy(collectionTitle = album.title)
                                                )
                                            }
                                        }
                                    }.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text((idx + 1).toString(), color = Highlight,
                                        modifier = Modifier.width(35.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, maxLines=1,
                                            overflow=TextOverflow.Ellipsis, color=Color.White)
                                        Text(song.artist, color=Color.LightGray,
                                            style=MaterialTheme.typography.bodySmall)
                                    }
                                    if (pendingAlbumTrack == song.title) {
                                        CircularProgressIndicator(Modifier.size(20.dp),
                                            color=Highlight,strokeWidth=2.dp)
                                    } else Text("▶", color=Highlight)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else if (showQueue) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "В списке: " + queue.size.toString() +
                            if (shuffle) " · случайный порядок включён" else "",
                        color = Color.LightGray, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue, key = { index, _ -> index }) { index, item ->
                            val active = currentIndex == index
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (active) Color(0xFF493463) else PanelBackground)
                                    .clickable {
                                        player.seekToDefaultPosition(index)
                                        player.play()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (active) "♫" else (index + 1).toString(),
                                    color = Highlight,
                                    modifier = Modifier.width(34.dp),
                                    textAlign = TextAlign.Center
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.mediaMetadata.title?.toString() ?: "Без названия",
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.mediaMetadata.artist?.toString() ?: "",
                                        color = Color.LightGray,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Image(
                            painter = rememberAsyncImagePainter(resolvedArtwork),
                            contentDescription = "Обложка композиции",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(PanelBackground)
                        )
                        Spacer(Modifier.height(28.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = {
                            showQueue = false
                            showAlbum = true
                        }) {
                            Text("Альбом · Все композиции", color = Highlight)
                        }
                        Text(
                            artist, color = Color.LightGray,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(30.dp))
                        val end = durationMs.coerceAtLeast(1L)
                        val displayedMs = if (sliderDragging || seekPending) sliderPositionMs
                                          else positionMs
                        Slider(
                            value = displayedMs.coerceIn(0L, end).toFloat(),
                            onValueChange = { value ->
                                sliderDragging = true
                                seekPending = false
                                sliderPositionMs = value.toLong().coerceIn(0L, end)
                            },
                            onValueChangeFinished = {
                                if (sliderDragging && durationMs > 0L) {
                                    val target = sliderPositionMs.coerceIn(0L, end)
                                    sliderDragging = false
                                    seekPending = true
                                    player.seekTo(target)
                                } else {
                                    sliderDragging = false
                                }
                            },
                            valueRange = 0f..end.toFloat(),
                            enabled = durationMs > 0L,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Highlight,
                                activeTrackColor = Highlight,
                                inactiveTrackColor = Color(0xFF55445F)
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(timeLabel(displayedMs), color = Color.LightGray,
                                style = MaterialTheme.typography.labelMedium)
                            Text(if (durationMs <= 0L) "--:--" else timeLabel(durationMs),
                                color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { player.seekToPreviousMediaItem() },
                                enabled = player.mediaItemCount > 1) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущий",
                                    tint = Color.White, modifier = Modifier.size(35.dp))
                            }
                            IconButton(
                                onClick = {
                                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                },
                                enabled = durationMs > 0L && player.isCurrentMediaItemSeekable
                            ) {
                                Icon(Icons.Filled.Replay10, "Назад 10 секунд", tint = Color.White)
                            }
                            FilledIconButton(
                                onClick = { if (playing) player.pause() else player.play() },
                                modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Highlight,
                                    contentColor = Color(0xFF180F25)
                                )
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Пауза" else "Играть",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    player.seekTo(
                                        (player.currentPosition + 10_000L).coerceAtMost(durationMs)
                                    )
                                },
                                enabled = durationMs > 0L && player.isCurrentMediaItemSeekable
                            ) {
                                Icon(Icons.Filled.Forward10, "Вперёд 10 секунд", tint = Color.White)
                            }
                            IconButton(onClick = { player.seekToNextMediaItem() },
                                enabled = player.mediaItemCount > 1) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Следующий",
                                    tint = Color.White, modifier = Modifier.size(35.dp))
                            }
                        }
                        Spacer(Modifier.height(25.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    player.setShuffleModeEnabled(!shuffle)
                                    shuffle = !shuffle
                                }) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = "Случайный порядок",
                                        tint = if (shuffle) Highlight else Color.LightGray)
                                }
                                Text("Случайно", color = Color.LightGray,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = onFavorite) {
                                    Icon(
                                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Избранное",
                                        tint = if (favorite) Highlight else Color.LightGray
                                    )
                                }
                                Text("Избранное", color = Color.LightGray,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    val next = when (repeat) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    player.setRepeatMode(next)
                                    repeat = next
                                }) {
                                    Icon(
                                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                                        else Icons.Filled.Repeat,
                                        contentDescription = "Повтор",
                                        tint = if (repeat == Player.REPEAT_MODE_OFF) Color.LightGray
                                               else Highlight
                                    )
                                }
                                Text(
                                    when (repeat) {
                                        Player.REPEAT_MODE_ONE -> "Один трек"
                                        Player.REPEAT_MODE_ALL -> "Вся очередь"
                                        else -> "Без повтора"
                                    },
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private fun timeLabel(timeMs: Long): String {
    val seconds = timeMs.coerceAtLeast(0) / 1000L
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
        else "%d:%02d".format(minutes, remaining)
}
