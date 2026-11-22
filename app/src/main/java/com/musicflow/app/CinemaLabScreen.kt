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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CinemaViolet = Color(0xFFB697FF)
private val CinemaSurface = Color(0xFF252236)

private enum class CinemaCategory(val label: String) {
    ALL("Главная"), MOVIES("Фильмы"), SERIES("Сериалы"), FAVORITES("Избранное")
}

@Composable
internal fun CinemaLabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    val scope = rememberCoroutineScope()
    val demo = remember(context) { CinemaLabSources.bundled(context) }
    var plugins by remember { mutableStateOf(CinemaLabSources.plugins(context)) }
    var fromPlugins by remember {
        mutableStateOf<Map<String, List<CinemaTitle>>>(emptyMap())
    }
    var pluginsBusy by remember { mutableStateOf(false) }
    var pluginLoadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var remoteShows by remember { mutableStateOf<List<CinemaTitle>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var selectedKind by remember { mutableStateOf(CinemaCategory.ALL) }
    var selected by remember { mutableStateOf<CinemaTitle?>(null) }
    var playing by remember { mutableStateOf<Triple<CinemaTitle, String, String>?>(null) }
    var showPlugins by remember { mutableStateOf(false) }
    // Native source adapters; no separate Lampa window or web-based playback.
    var nativeSources by remember { mutableStateOf(CinemaNativeSources.sources(context)) }
    var nativeTitles by remember {
        mutableStateOf<Map<String, List<CinemaTitle>>>(emptyMap())
    }
    var nativeBusy by remember { mutableStateOf(false) }
    var nativeError by remember { mutableStateOf<String?>(null) }
    var showNativeSources by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(CinemaLabSources.favoriteKeys(context)) }
    var watchLater by remember { mutableStateOf(CinemaLabSources.laterKeys(context)) }
    var recent by remember { mutableStateOf(CinemaLabSources.recentKeys(context)) }
    var activeSource by remember { mutableStateOf("all") }

    LaunchedEffect(plugins) {
        fromPlugins = fromPlugins.filterKeys { key -> plugins.any { it.id == key } }
        pluginsBusy = plugins.isNotEmpty()
        pluginLoadError = null
        try {
            for (plugin in plugins) {
                try {
                    val titles = CinemaLabSources.fetchPluginCatalog(plugin)
                    fromPlugins = fromPlugins + (plugin.id to titles)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    pluginLoadError = plugin.name + ": " +
                        (error.message ?: "Не удалось прочитать каталог")
                }
            }
        } finally {
            pluginsBusy = false
        }
    }

    LaunchedEffect(nativeSources) {
        nativeBusy = nativeSources.any { it.playable }
        nativeError = null
        nativeTitles = nativeTitles.filterKeys { id -> nativeSources.any { it.id == id } }
        try {
            for (source in nativeSources.filter { it.playable }) {
                try {
                    val titles = CinemaNativeSources.load(source)
                    nativeTitles = nativeTitles + (source.id to titles)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    nativeError = source.name + ": " +
                        (error.message ?: "Источник недоступен")
                }
            }
        } finally {
            nativeBusy = false
        }
    }

    LaunchedEffect(search) {
        remoteShows = emptyList()
        if (search.trim().length < 2) return@LaunchedEffect
        delay(600)
        searchBusy = true
        try {
            remoteShows = CinemaLabSources.searchSeries(search)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            AppDiagnostics.failure("Cinema", "TVMaze metadata search", error)
        } finally {
            searchBusy = false
        }
    }

    val allCatalog = (demo + fromPlugins.values.flatten() +
        nativeTitles.values.flatten()).distinctBy { it.key }
    val base = if (search.trim().length > 1) {
        allCatalog + remoteShows
    } else allCatalog
    val visible = base.filter { title ->
        (activeSource == "all" || title.sourceId == activeSource) &&
            when (selectedKind) {
                CinemaCategory.ALL -> true
                CinemaCategory.MOVIES -> title.kind == CinemaKind.MOVIE
                CinemaCategory.SERIES -> title.kind == CinemaKind.SERIES
                CinemaCategory.FAVORITES -> title.key in favorites
            } &&
            (search.isBlank() || title.title.contains(search.trim(), ignoreCase = true) ||
                title.genre.contains(search.trim(), ignoreCase = true))
    }
    val continueItems = recent.mapNotNull { key ->
        allCatalog.firstOrNull { it.key == key }
    }
    val starred = allCatalog.filter { it.key in favorites }
    val saveLater = allCatalog.filter { it.key in watchLater }

    fun toggleFavorite(title: CinemaTitle) {
        favorites = if (title.key in favorites) favorites - title.key
            else favorites + title.key
        CinemaLabSources.saveFavorites(context, favorites)
    }
    fun toggleLater(title: CinemaTitle) {
        watchLater = if (title.key in watchLater) watchLater - title.key
            else watchLater + title.key
        CinemaLabSources.saveLater(context, watchLater)
    }
    fun start(title: CinemaTitle, stream: String, episodeKey: String) {
        require(CinemaLabSources.isPlayableUri(title.sourceId, stream))
        CinemaLabSources.markRecent(context, title.key)
        recent = CinemaLabSources.recentKeys(context)
        selected = null
        playing = Triple(title, stream, episodeKey)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Movie, null, tint = CinemaViolet)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Кино · тестовый раздел",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Text("Каталог • сериалы • подключаемые источники",
                    style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
            TextButton(onClick = { showNativeSources = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Источник")
            }
            IconButton(onClick = { showPlugins = true }) {
                Icon(Icons.Filled.Settings, "Ранее добавленные JSON-плагины",
                    tint = CinemaViolet)
            }
        }
        playing?.let { (title, url, episodeId) ->
            key(title.key + ":" + episodeId + ":" + url) {
                CinemaNativeInlinePlayer(
                    title = title.title,
                    sourceKey = title.key + ":" + episodeId,
                    streamUrl = url,
                    allowBuiltInFallback =
                        title.sourceId == CinemaLabSources.DEMO_SOURCE,
                    onClose = { playing = null }
                )
            }
            Spacer(Modifier.height(9.dp))
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it.take(75) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Найти фильм или сериал") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CinemaCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedKind == category,
                    onClick = { selectedKind = category },
                    label = { Text(category.label) },
                    shape = RoundedCornerShape(15.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = activeSource == "all",
                onClick = { activeSource = "all" },
                label = { Text("Все источники") })
            FilterChip(selected = activeSource == CinemaLabSources.DEMO_SOURCE,
                onClick = { activeSource = CinemaLabSources.DEMO_SOURCE },
                label = { Text("Демо-фильмы") })
            plugins.forEach { plugin ->
                FilterChip(selected = activeSource == plugin.id,
                    onClick = { activeSource = plugin.id },
                    label = { Text(plugin.name, maxLines = 1) })
            }
            nativeSources.filter { it.playable }.forEach { source ->
                FilterChip(
                    selected = activeSource == source.id,
                    onClick = { activeSource = source.id },
                    label = { Text(source.name, maxLines = 1) }
                )
            }
            TextButton(onClick = { showNativeSources = true }) {
                Icon(Icons.Filled.Add, null)
                Text("Источник")
            }
        }
        if (pluginsBusy || searchBusy || nativeBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (nativeError != null) {
            Text("Нативный источник: " + nativeError.orEmpty(),
                color = Color(0xFFFFABA9),
                style = MaterialTheme.typography.labelSmall)
        }
        if (pluginLoadError != null) {
            Text("Плагин: " + pluginLoadError.orEmpty(), color = Color(0xFFFFABA9),
                style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            if (search.isBlank() && selectedKind == CinemaCategory.ALL && activeSource == "all") {
                item(key = "hero") {
                    val hero = allCatalog.firstOrNull()
                    if (hero != null) CinemaHero(hero, onClick = { selected = hero })
                }
                if (continueItems.isNotEmpty()) {
                    item(key = "history") {
                        CinemaRail("Продолжить просмотр", continueItems,
                            onClick = { selected = it })
                    }
                }
                if (starred.isNotEmpty()) {
                    item(key = "favorites") {
                        CinemaRail("Избранное", starred, onClick = { selected = it })
                    }
                }
                if (saveLater.isNotEmpty()) {
                    item(key = "later") {
                        CinemaRail("Смотреть позже", saveLater, onClick = { selected = it })
                    }
                }
                item(key = "demo-note") {
                    Text("Демо: открытые короткометражки Blender Foundation. " +
                        "Сериалы TVMaze показывают метаданные без обещания воспроизведения.",
                        color = palette.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key = "allheading") {
                Text(
                    when (selectedKind) {
                        CinemaCategory.ALL -> "Каталог · " + visible.size
                        CinemaCategory.MOVIES -> "Фильмы · " + visible.size
                        CinemaCategory.SERIES -> "Сериалы · " + visible.size
                        CinemaCategory.FAVORITES -> "Избранное · " + visible.size
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            items(visible, key = { it.key }) { title ->
                CinemaRow(title, title.key in favorites,
                    onClick = { selected = title },
                    onFavorite = { toggleFavorite(title) })
            }
            if (visible.isEmpty() && !pluginsBusy && !searchBusy && !nativeBusy) {
                item(key = "empty") {
                    Text("Пока нет совпадений. Проверьте запрос или подключите источник через «Плагин».",
                        color = palette.muted, modifier = Modifier.padding(15.dp))
                }
            }
        }
    }

    if (showNativeSources) {
        CinemaNativeSourcesDialog(
            sources = nativeSources,
            busy = nativeBusy,
            error = nativeError,
            onClose = { showNativeSources = false },
            onAdd = { name, link ->
                scope.launch {
                    nativeBusy = true
                    nativeError = null
                    try {
                        require(nativeSources.size < 24) {
                            "Максимальное количество источников: 24"
                        }
                        require(nativeSources.none { it.url.equals(link, ignoreCase = true) }) {
                            "Этот источник уже добавлен"
                        }
                        val source = CinemaNativeSources.register(name, link)
                        nativeSources = nativeSources + source
                        CinemaNativeSources.save(context, nativeSources)
                        if (source.playable) {
                            activeSource = source.id
                        } else {
                            nativeError = "Ссылка Lampa сохранена, но этот JS-плагин " +
                                "не предоставляет совместимый нативный Media3-адаптер."
                        }
                    } catch (error: Exception) {
                        nativeError = error.message ?: "Ошибка подключения"
                    } finally {
                        nativeBusy = false
                    }
                }
            },
            onRemove = { source ->
                nativeSources = nativeSources.filterNot { it.id == source.id }
                CinemaNativeSources.save(context, nativeSources)
                if (activeSource == source.id) activeSource = "all"
            }
        )
    }

    if (showPlugins) {
        CinemaPluginsDialog(
            plugins = plugins,
            busy = pluginsBusy,
            error = pluginLoadError,
            onClose = { showPlugins = false },
            onAdd = { link ->
                scope.launch {
                    pluginsBusy = true
                    pluginLoadError = null
                    try {
                        require(plugins.size < 20) { "Максимум 20 источников" }
                        val added = CinemaLabSources.installPlugin(link)
                        require(plugins.none { it.manifestUrl == added.manifestUrl }) {
                            "Этот плагин уже подключён"
                        }
                        val check = CinemaLabSources.fetchPluginCatalog(added)
                        require(check.isNotEmpty()) { "Плагин не содержит фильмов или сериалов." }
                        plugins = plugins + added
                        CinemaLabSources.savePlugins(context, plugins)
                        activeSource = added.id
                    } catch (error: Exception) {
                        pluginLoadError = error.message ?: "Ошибка подключения"
                    } finally {
                        pluginsBusy = false
                    }
                }
            },
            onRemove = { item ->
                plugins = plugins.filterNot { it.id == item.id }
                CinemaLabSources.savePlugins(context, plugins)
                if (activeSource == item.id) activeSource = "all"
            }
        )
    }

    selected?.let { title ->
        CinemaDetailsDialog(
            title = title,
            favorite = title.key in favorites,
            later = title.key in watchLater,
            onFavorite = { toggleFavorite(title) },
            onLater = { toggleLater(title) },
            onPlay = { url, episodeId -> start(title, url, episodeId) },
            onClose = { selected = null }
        )
    }

}

@Composable
private fun CinemaHero(title: CinemaTitle, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp), color = CinemaSurface) {
        Column(Modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF3F315B), Color(0xFF171A28))))
            .padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CinemaPoster(title.poster, 95.dp, 118.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("КИНО • ДЕМО", color = CinemaViolet,
                        style = MaterialTheme.typography.labelMedium)
                    Text(title.title, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text((title.year?.toString() ?: "") + " · " + title.genre,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(7.dp))
                    Button(onClick = onClick) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text("Подробнее")
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaRail(
    title: String, titles: List<CinemaTitle>, onClick: (CinemaTitle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            titles.take(20).forEach { item ->
                Surface(Modifier.width(130.dp).clickable { onClick(item) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(8.dp)) {
                        CinemaPoster(item.poster, 114.dp, 130.dp)
                        Spacer(Modifier.height(7.dp))
                        Text(item.title, maxLines = 2, minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaRow(
    item: CinemaTitle,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CinemaPoster(item.poster, 85.dp, 105.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold)
                Text(
                    (if (item.kind == CinemaKind.MOVIE) "Фильм" else "Сериал") +
                        " · " + (item.year?.toString() ?: "—"),
                    style = MaterialTheme.typography.bodySmall, color = CinemaViolet)
                Text(item.genre, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (item.hasPlayback) "Есть видеоисточник" else "Только сведения",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Добавить в избранное", tint = CinemaViolet)
            }
        }
    }
}

@Composable
private fun CinemaPoster(poster: String?, width: Dp, height: Dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(12.dp))
        .background(CinemaSurface), contentAlignment = Alignment.Center) {
        if (poster == null) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = CinemaViolet)
        } else {
            Image(rememberAsyncImagePainter(poster), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CinemaDetailsDialog(
    title: CinemaTitle,
    favorite: Boolean,
    later: Boolean,
    onFavorite: () -> Unit,
    onLater: () -> Unit,
    onPlay: (String, String) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title.title, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    CinemaPoster(title.poster, 105.dp, 135.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text((if (title.kind == CinemaKind.MOVIE) "Фильм" else "Сериал") +
                            " · " + (title.year?.toString() ?: "—"),
                            color = CinemaViolet)
                        Text(title.genre)
                        if (title.attribution.isNotBlank()) {
                            Text(title.attribution,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (title.description.isNotBlank()) Text(title.description)
                Row {
                    TextButton(onClick = onFavorite) {
                        Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null)
                        Text(if (favorite) "В избранном" else "Избранное")
                    }
                    TextButton(onClick = onLater) {
                        Icon(if (later) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null)
                        Text("Позже")
                    }
                }
                title.streamUrl?.let { url ->
                    Button(onClick = { onPlay(url, "main") }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Смотреть")
                    }
                }
                if (title.episodes.isNotEmpty()) {
                    Text("Выберите серию", fontWeight = FontWeight.Bold)
                    title.episodes.forEach { episode ->
                        OutlinedButton(onClick = { onPlay(episode.streamUrl, episode.id) },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(episode.title)
                        }
                    }
                }
                if (!title.hasPlayback) {
                    Text("Источник содержит только описание. " +
                        "Для просмотра нужен подключённый плагин с разрешённой ссылкой на поток.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        }
    )
}

@Composable
private fun CinemaPluginsDialog(
    plugins: List<CinemaPlugin>,
    busy: Boolean,
    error: String?,
    onClose: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (CinemaPlugin) -> Unit
) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Источники кино · TEST") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Добавьте адрес JSON-плагина, как источник в каталоге расширений. " +
                    "Встроенные JavaScript-плагины Lampa (например, bwa.ad/rc) " +
                    "не совместимы с Android Media3 и не исполняются.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = link, onValueChange = { link = it.take(2048) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ссылка на плагин HTTPS") }, maxLines = 3)
                OutlinedButton(
                    onClick = { link = CinemaLabSources.DEMO_MANIFEST_URL },
                    enabled = !busy
                ) { Text("Вставить тестовый плагин") }
                Button(onClick = { onAdd(link.trim()) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy && link.isNotBlank()) {
                    Text(if (busy) "Проверяю источник…" else "Подключить источник")
                }
                error?.let { Text(it, color = Color(0xFFFFB2A4)) }
                if (plugins.isNotEmpty()) {
                    Text("Подключённые источники", fontWeight = FontWeight.Bold)
                    plugins.forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(source.name, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onRemove(source) }, enabled = !busy) {
                                Text("Удалить")
                            }
                        }
                    }
                }
                Text("Поддерживается manifest musicflow.cinema.v1 → catalog_url → " +
                    "фильмы/сериалы/серии с HTTPS-потоками. Никаких скриптов, APK или " +
                    "обхода прав доступа.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Готово") } }
    )
}
