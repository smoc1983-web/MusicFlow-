package com.musicflow.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TvAccent = Color(0xFFAD83FF)
private val TvAccentPanel = Color(0xFF30273E)

@Composable
internal fun TvHubModeTabs(mode: TvHubMode, onSelect: (TvHubMode) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TvHubMode.entries.forEach { item ->
            FilterChip(
                selected = mode == item,
                onClick = { onSelect(item) },
                label = { Text(item.title, maxLines = 1) },
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

/** Three modes share one real set of channels/Media3 handoff; no fictional VOD. */
@Composable
internal fun TvModernHub(
    mode: TvHubMode,
    channels: List<TvChannel>,
    selected: TvChannel?,
    programmes: List<TvProgramme>,
    sourceId: String,
    sourceName: String,
    sources: List<IptvSource>,
    loading: Boolean,
    error: String?,
    onPlay: (TvChannel) -> Unit,
    onSource: (String) -> Unit,
    onAddPlaylist: () -> Unit,
    onAddPlugin: () -> Unit,
    onImportFile: () -> Unit,
    onRefresh: () -> Unit,
    onExternal: (TvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Все") }
    var favorites by remember { mutableStateOf(TvHubLibrary.getKeys(context, "favorites")) }
    var later by remember { mutableStateOf(TvHubLibrary.getKeys(context, "later")) }
    var recently by remember { mutableStateOf(TvHubLibrary.recent(context)) }

    fun key(channel: TvChannel): String = TvHubLibrary.channelKey(sourceId, channel)
    fun play(channel: TvChannel) {
        recently = TvHubLibrary.addRecent(context, key(channel))
        onPlay(channel)
    }
    fun favorite(channel: TvChannel) {
        val id = key(channel)
        favorites = if (id in favorites) favorites - id else favorites + id
        TvHubLibrary.setKeys(context, "favorites", favorites)
    }
    fun bookmark(channel: TvChannel) {
        val id = key(channel)
        later = if (id in later) later - id else later + id
        TvHubLibrary.setKeys(context, "later", later)
    }
    fun programme(channel: TvChannel): TvProgramme? = programmes.firstOrNull {
        it.channelId.equals(channel.epgId ?: channel.id, true) && it.isNow()
    }
    val current = selected?.takeIf { active -> channels.any { it.id == active.id } }
    val hero = current ?: channels.firstOrNull()
    val filtered = channels.filter {
        TvHubLibrary.channelMatchesGenre(it, genre) &&
            (query.isBlank() || it.title.contains(query.trim(), true) ||
                it.group.contains(query.trim(), true))
    }
    val starred = channels.filter { key(it) in favorites }
    val saved = channels.filter { key(it) in later }
    val history = recently.mapNotNull { value ->
        channels.firstOrNull { key(it) == value }
    }
    val grouped = starred.map { it.group }.toSet()
    val suggested = (channels.filter { it.group in grouped && key(it) !in favorites } +
        channels.filter { key(it) !in favorites }).distinctBy { it.id }.take(12)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "source-header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ТВ · " + sourceName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = palette.foreground,
                        maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, "Обновить каналы", tint = palette.accent)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = sourceId == "ruvideohub",
                        onClick = { onSource("ruvideohub") },
                        label = { Text("RuVideoHub") })
                    sources.forEach { item ->
                        FilterChip(selected = sourceId == item.id,
                            onClick = { onSource(item.id) },
                            label = { Text(
                                (if (item.kind == IptvSourceKind.PLUGIN) "◈ " else "") +
                                    item.name, maxLines = 1) })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = onAddPlaylist) { Text("+ IPTV") }
                    OutlinedButton(onClick = onAddPlugin) { Text("+ Плагин") }
                    OutlinedButton(onClick = onImportFile) { Text("+ M3U-файл") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) {
                    Text(error, color = Color(0xFFFFB9AF),
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(80) },
                    label = { Text("Поиск каналов") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Все", "Новости", "Спорт", "Музыка", "Детям").forEach { candidate ->
                        FilterChip(selected = genre == candidate,
                            onClick = { genre = candidate },
                            label = { Text(candidate) })
                    }
                }
            }
        }
        when (mode) {
            TvHubMode.OVERVIEW -> {
                if (hero != null) item(key = "overview-hero") {
                    TvHero(hero, programme(hero), current?.id == hero.id,
                        onPlay = { play(hero) },
                        onFavorite = { favorite(hero) }, isFavorite = key(hero) in favorites,
                        onBookmark = { bookmark(hero) }, isSaved = key(hero) in later)
                }
                item(key = "overview-summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TvCount("Каналы", channels.size, Modifier.weight(1f))
                        TvCount("Избранное", starred.size, Modifier.weight(1f))
                        TvCount("История", history.size, Modifier.weight(1f))
                    }
                }
                if (history.isNotEmpty()) item(key = "overview-history") {
                    TvCarousel("Продолжить просмотр", history, ::play,
                        { key(it) in favorites }, ::favorite)
                }
                if (starred.isNotEmpty()) item(key = "overview-favs") {
                    TvCarousel("Избранные каналы", starred, ::play,
                        { key(it) in favorites }, ::favorite)
                }
                item(key = "overview-channels") {
                    TvCarousel("Популярные каналы", filtered.take(30), ::play,
                        { key(it) in favorites }, ::favorite)
                }
                item(key = "overview-epg") {
                    TvGuide(channels.take(6), programmes, ::play)
                }
                if (suggested.isNotEmpty()) item(key = "overview-suggested") {
                    TvCarousel("Рекомендации по категориям", suggested, ::play,
                        { key(it) in favorites }, ::favorite)
                }
                item(key = "overview-all") { TvHeader("Все каналы · " + filtered.size) }
                items(filtered.take(150), key = { "o:" + it.id }) { channel ->
                    TvRow(channel, programme(channel), current?.id == channel.id,
                        onPlay = { play(channel) }, onFavorite = { favorite(channel) },
                        isFavorite = key(channel) in favorites,
                        onBookmark = { bookmark(channel) }, isSaved = key(channel) in later)
                }
            }
            TvHubMode.LIVE -> {
                if (hero != null) item(key = "live-hero") {
                    TvHero(hero, programme(hero), current?.id == hero.id,
                        onPlay = { play(hero) }, onExternal = { onExternal(hero) },
                        onFavorite = { favorite(hero) }, isFavorite = key(hero) in favorites,
                        onBookmark = { bookmark(hero) }, isSaved = key(hero) in later)
                }
                if (starred.isNotEmpty()) item(key = "live-favs") {
                    TvCarousel("Быстрый доступ · избранное", starred, ::play,
                        { true }, ::favorite)
                }
                item(key = "live-epg") { TvGuide(channels, programmes, ::play) }
                item(key = "live-heading") {
                    TvHeader("Сейчас в эфире · " + filtered.size + " каналов")
                }
                items(filtered, key = { "l:" + it.id }) { channel ->
                    TvRow(channel, programme(channel), current?.id == channel.id,
                        onPlay = { play(channel) }, onFavorite = { favorite(channel) },
                        isFavorite = key(channel) in favorites,
                        onBookmark = { bookmark(channel) }, isSaved = key(channel) in later)
                }
            }
            TvHubMode.PERSONAL -> {
                item(key = "personal-title") {
                    Column {
                        TvHeader("Моё ТВ")
                        Text("Избранное и история сохраняются на этом устройстве.",
                            color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item(key = "personal-favs") {
                    TvCarousel("Избранные каналы", starred, ::play, { true }, ::favorite,
                        "Добавьте каналы сердечком во вкладке «Эфир».")
                }
                item(key = "personal-later") {
                    TvCarousel("Смотреть позже", saved, ::play,
                        { key(it) in favorites }, ::favorite,
                        "Нажмите закладку у телеканала.")
                }
                item(key = "personal-recent") {
                    TvCarousel("Недавно просмотренные", history, ::play,
                        { key(it) in favorites }, ::favorite,
                        "История появится после первого включённого канала.")
                }
                item(key = "personal-suggestions") {
                    TvCarousel("Могут понравиться", suggested, ::play,
                        { key(it) in favorites }, ::favorite)
                }
            }
            TvHubMode.CLASSIC -> Unit
        }
        if (channels.isEmpty() && !loading && error == null) {
            item(key = "empty") {
                Text("Нет каналов. Добавьте M3U или ТВ-плагин.",
                    color = palette.muted, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun TvHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold)
}

@Composable
private fun TvCount(label: String, count: Int, modifier: Modifier) {
    Surface(modifier, color = TvAccentPanel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(count.toString(), color = TvAccent,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TvHero(
    channel: TvChannel,
    programme: TvProgramme?,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    isFavorite: Boolean,
    onBookmark: () -> Unit,
    isSaved: Boolean,
    onExternal: (() -> Unit)? = null
) {
    Surface(color = TvAccentPanel, shape = RoundedCornerShape(21.dp)) {
        Column(Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(
                listOf(Color(0xFF372448), Color(0xFF161922))))
            .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvLogo(channel, 72.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("● ПРЯМОЙ ЭФИР", color = Color(0xFFFF9299),
                        style = MaterialTheme.typography.labelSmall)
                    Text(channel.title, maxLines = 2,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(channel.group, color = Color(0xFFCBC4D9),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(programme?.title ?: "Телепрограмма не предоставлена источником",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDFD3ED))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(if (isCurrent) "Смотреть" else "Включить")
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Избранное", tint = TvAccent)
                }
                IconButton(onClick = onBookmark) {
                    Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        "Смотреть позже", tint = TvAccent)
                }
                if (onExternal != null) {
                    TextButton(onClick = onExternal) { Text("Внешний") }
                }
            }
        }
    }
}

@Composable
private fun TvCarousel(
    title: String,
    channels: List<TvChannel>,
    onPlay: (TvChannel) -> Unit,
    isFavorite: (TvChannel) -> Boolean,
    onFavorite: (TvChannel) -> Unit,
    emptyText: String = "Нет каналов"
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvHeader(title)
        if (channels.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                channels.take(30).forEach { channel ->
                    Surface(Modifier.width(120.dp).clickable { onPlay(channel) },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                TvLogo(channel, 52.dp)
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    if (isFavorite(channel)) Icons.Filled.Favorite
                                    else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Избранное", tint = TvAccent,
                                    modifier = Modifier.size(22.dp).clickable { onFavorite(channel) })
                            }
                            Text(channel.title, minLines = 2, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(channel.group, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvRow(
    channel: TvChannel,
    programme: TvProgramme?,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    isFavorite: Boolean,
    onBookmark: () -> Unit,
    isSaved: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) TvAccentPanel else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TvLogo(channel, 46.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text(programme?.title ?: channel.group,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Избранное", tint = TvAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onBookmark, modifier = Modifier.size(34.dp)) {
                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    "Смотреть позже", tint = TvAccent, modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Filled.PlayArrow, "Смотреть", tint = TvAccent)
        }
    }
}

@Composable
private fun TvLogo(channel: TvChannel, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF282532)), contentAlignment = Alignment.Center) {
        if (channel.logo.isNullOrBlank()) {
            Icon(Icons.Filled.Tv, contentDescription = null, tint = TvAccent)
        } else {
            Image(rememberAsyncImagePainter(channel.logo), contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp))
        }
    }
}

@Composable
private fun TvGuide(
    channels: List<TvChannel>,
    programmes: List<TvProgramme>,
    onPlay: (TvChannel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TvHeader("Телепрограмма (EPG)")
        if (programmes.isEmpty()) {
            Text("Источник не предоставляет EPG. Для расписания добавьте ТВ-плагин с XMLTV.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        } else {
            val now = System.currentTimeMillis()
            val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            channels.take(30).forEach { channel ->
                val id = channel.epgId ?: channel.id
                val shows = programmes.filter {
                    it.channelId.equals(id, true) && it.endMillis > now &&
                        it.startMillis < now + 8 * 3_600_000L
                }.take(3)
                if (shows.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onPlay(channel) }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TvLogo(channel, 38.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(channel.title, maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold)
                            shows.forEach { show ->
                                Text(
                                    format.format(Date(show.startMillis)) + "–" +
                                        format.format(Date(show.endMillis)) + "  " + show.title,
                                    color = if (show.isNow(now)) TvAccent else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(Icons.Filled.PlayArrow, "Смотреть", tint = TvAccent)
                    }
                }
            }
        }
    }
}
