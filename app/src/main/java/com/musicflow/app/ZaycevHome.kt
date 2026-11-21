package com.musicflow.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Duration

/** Public Zaycev editorial metadata; audio is only played from verified MusicFlow streams. */
internal data class HomeSong(
    val id: String,
    val title: String,
    val artist: String,
    val image: String?,
    val durationSec: Long
)
private data class HomeCollection(val title: String, val link: String)
private val HomePanel = Color(0xFF211A2C)
private val HomeHighlight = Color(0xFFAC80FF)

private suspend fun getZaycevPage(address: String): Document = withContext(Dispatchers.IO) {
    Jsoup.connect(address).userAgent(
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
    ).header("Accept-Language", "ru-RU,ru;q=0.9")
        .timeout(14_000).maxBodySize(1_500_000).get()
}

private fun parseHomeSongs(document: Document): List<HomeSong> {
    val results = mutableListOf<HomeSong>()
    // Semantic metadata is published by Zaycev in list and genre pages.
    for (article in document.select("article[itemtype*=MusicRecording]")) {
        val link = article.selectFirst("link[itemprop=url]")?.attr("href").orEmpty()
        if (!link.startsWith("/pages/")) continue
        val title = article.selectFirst("meta[itemprop=name]")?.attr("content").orEmpty().trim()
        val artist = article.selectFirst("[itemprop=byArtist] meta[itemprop=name]")
            ?.attr("content").orEmpty().trim()
        if (title.isBlank() || artist.isBlank()) continue
        val image = article.selectFirst("meta[itemprop=thumbnailUrl]")
            ?.attr("content")?.takeIf { it.startsWith("https://") }
        val duration = article.selectFirst("meta[itemprop=duration]")
            ?.attr("content").orEmpty()
        val seconds = runCatching { Duration.parse(duration).seconds }.getOrDefault(0L)
        results.add(HomeSong(link, title, artist, image, seconds))
        if (results.size >= 50) break
    }
    // Some featured cards use accessible links rather than structured metadata.
    if (results.isEmpty()) {
        for (link in document.select("a[data-qa=track-link]")) {
            val title = link.text().trim()
            val artist = link.parent()?.selectFirst("a[data-qa=artist-link]")?.text().orEmpty().trim()
            val path = link.attr("href")
            if (title.isBlank() || artist.isBlank() || !path.startsWith("/pages/")) continue
            results.add(HomeSong(path, title, artist, null, 0))
            if (results.size >= 40) break
        }
    }
    return results.distinctBy { it.id }
}

private fun parseCollections(document: Document): List<HomeCollection> =
    document.select("a[href^=/musicset/]")
        .mapNotNull { a ->
            val url = a.attr("href")
            val title = a.text().trim()
            if (title.isBlank() || url == "/musicset/" || title.length > 70) null
            else HomeCollection(title, "https://zaycev.net" + url)
        }.distinctBy { it.link }.take(12)

@Composable
internal fun MusicHomeScreen(
    onSearch: (String) -> Unit,
    onPlay: (HomeSong, List<HomeSong>) -> Unit,
    onPlayChart: (TopSong, List<TopSong>) -> Unit,
    pendingSongId: String?,
    playbackMessage: String?,
    modifier: Modifier = Modifier
) {
    val palette = LocalMusicFlowPalette.current
    var popular by remember { mutableStateOf<List<HomeSong>>(emptyList()) }
    var fresh by remember { mutableStateOf<List<HomeSong>>(emptyList()) }
    var collected by remember { mutableStateOf<List<HomeSong>>(emptyList()) }
    var collections by remember { mutableStateOf<List<HomeCollection>>(emptyList()) }
    var chosenCollection by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var homeQuery by remember { mutableStateOf("") }
    var collectionsExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }
    var feedSection by remember { mutableStateOf("Популярное") }
    var chartRegion by remember { mutableStateOf(TopSongsRegion.RUSSIA) }
    var chartPeriod by remember { mutableStateOf(TopSongsPeriod.WEEK) }
    var chartRefresh by remember { mutableIntStateOf(0) }
    var chartSongs by remember { mutableStateOf<List<TopSong>>(emptyList()) }
    var chartLoading by remember { mutableStateOf(false) }
    var chartError by remember { mutableStateOf<String?>(null) }
    val chartCache = remember { mutableStateMapOf<String, Pair<Long, List<TopSong>>>() }
    var message by remember { mutableStateOf<String?>(null) }
    val genres = remember {
        listOf(
            "Поп" to "pop", "Рок" to "rock", "Рэп" to "rap",
            "Электроника" to "electronic", "Шансон" to "shanson",
            "Метал" to "metal", "Джаз" to "jazz"
        )
    }

    suspend fun refreshHome() {
        loading = true
        message = null
        try {
            coroutineScope {
                val topJob = async { runCatching { parseHomeSongs(getZaycevPage("https://zaycev.net/popular/index.html")) } }
                val freshJob = async { runCatching { parseHomeSongs(getZaycevPage("https://zaycev.net/new/index.html")) } }
                val collectionsJob = async { runCatching { parseCollections(getZaycevPage("https://zaycev.net/")) } }
                popular = topJob.await().getOrDefault(emptyList())
                fresh = freshJob.await().getOrDefault(emptyList())
                collections = collectionsJob.await().getOrDefault(emptyList())
            }
            AppDiagnostics.info("Home", "Zaycev catalog; top=" + popular.size +
                "; new=" + fresh.size + "; collections=" + collections.size)
            if (popular.isEmpty() && fresh.isEmpty()) {
                message = "Не удалось загрузить подборки. Проверь доступ к zaycev.net."
            }
        } catch (e: Exception) {
            AppDiagnostics.failure("Home", "Zaycev catalog", e)
            message = "Не удалось загрузить подборки: " + (e.message ?: "сеть недоступна")
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { refreshHome() }
    // Chart sources are refreshed on selection; each period is cached for 30 minutes.
    LaunchedEffect(feedSection, chartPeriod, chartRegion, chartRefresh) {
        if (feedSection != "ТОП песен") return@LaunchedEffect
        val key = chartRegion.name + ":" + chartPeriod.name
        val cached = chartCache[key]
        chartError = null
        if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60 * 1000L) {
            chartSongs = cached.second
            chartLoading = false
            return@LaunchedEffect
        }
        chartSongs = emptyList()
        chartLoading = true
        try {
            val loaded = TopSongsDirectory.fetch(chartPeriod, chartRegion)
            chartSongs = loaded
            chartCache[key] = System.currentTimeMillis() to loaded
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            AppDiagnostics.failure("Charts", "home chart " + key, failure)
            chartError = "Чарт временно недоступен. Повторите загрузку."
        } finally {
            chartLoading = false
        }
    }
    // Each visible Zaycev section is a separate listening queue.
    // Selecting a row preserves its position within that section.
    val popularQueue = popular.take(20)
    val freshQueue = fresh.take(15)
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = homeQuery,
                onValueChange = { homeQuery = it },
                placeholder = { Text("Песня или исполнитель") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { onSearch(homeQuery.trim()) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Найти")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(homeQuery.trim()) }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { collectionsExpanded = true },
                        enabled = collections.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("Подборки", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = collectionsExpanded,
                        onDismissRequest = { collectionsExpanded = false },
                        modifier = Modifier.musicFlowMenuSurface(palette),
                        containerColor = palette.surface,
                        shadowElevation = 14.dp) {
                        DropdownMenuItem(text = { Text("Все подборки") }, onClick = {
                            collectionsExpanded = false
                            chosenCollection = ""
                            collected = emptyList()
                            loading = false
                        })
                        collections.forEach { collection ->
                            DropdownMenuItem(
                                text = { Text(collection.title, maxLines = 2) },
                                onClick = {
                                    collectionsExpanded = false
                                    chosenCollection = collection.title
                                    collected = emptyList()
                                    loading = true
                                    message = null
                                })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { genresExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("Жанры")
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = genresExpanded,
                        onDismissRequest = { genresExpanded = false },
                        modifier = Modifier.musicFlowMenuSurface(palette),
                        containerColor = palette.surface,
                        shadowElevation = 14.dp) {
                        genres.forEach { (name, _) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                genresExpanded = false
                                chosenCollection = name
                                collected = emptyList()
                                loading = true
                                message = null
                            })
                        }
                    }
                }
            }
            if (chosenCollection.isNotBlank()) {
                Text(chosenCollection, color = palette.accent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = Color(0xFFFFB3A8)) }
            playbackMessage?.let {
                Text(it, color = palette.accent, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (chosenCollection.isNotBlank() && collected.isNotEmpty()) {
            item {
                Text(chosenCollection, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            items(collected, key = { "genre_" + it.id }) { song ->
                HomeSongRow(song, { selected -> onPlay(selected, collected) }, pendingSongId)
            }
        }
        if (chosenCollection.isBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Популярное", "ТОП песен", "Новинки").forEach { section ->
                        FilterChip(
                            selected = feedSection == section,
                            onClick = { feedSection = section },
                            label = { Text(section) }
                        )
                    }
                }
            }
            when (feedSection) {
                "Популярное" -> items(popularQueue, key = { "top_" + it.id }) { song ->
                    HomeSongRow(song, { selected -> onPlay(selected, popularQueue) }, pendingSongId)
                }
                "Новинки" -> items(freshQueue, key = { "fresh_" + it.id }) { song ->
                    HomeSongRow(song, { selected -> onPlay(selected, freshQueue) }, pendingSongId)
                }
                "ТОП песен" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                TopSongsRegion.entries.forEach { choice ->
                                    FilterChip(
                                        selected = choice == chartRegion,
                                        onClick = { chartRegion = choice },
                                        label = { Text(choice.label) }
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TopSongsPeriod.entries.forEach { choice ->
                                    FilterChip(
                                        selected = choice == chartPeriod,
                                        onClick = { chartPeriod = choice },
                                        label = { Text(choice.label) }
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        chartCache.remove(chartRegion.name + ":" + chartPeriod.name)
                                        chartRefresh++
                                    },
                                    enabled = !chartLoading
                                ) { Text("Обновить") }
                            }
                            Text(
                                if (chartRegion == TopSongsRegion.RUSSIA)
                                    "Россия · чарт Яндекс Музыки, анализ UpSound AI. " +
                                    "Сумма дневных слушателей за доступные дни; годовой архив неполный."
                                else "Мир · ListenBrainz. Прослушивания участников сервиса, " +
                                    "не всех стриминговых платформ.",
                                color = palette.muted,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (chartLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            chartError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (!chartLoading && chartSongs.isEmpty() && chartError == null) {
                                Text("За выбранный период нет данных", color = palette.muted)
                            }
                        }
                    }
                    itemsIndexed(chartSongs, key = { index, _ ->
                        "chart:" + chartRegion.name + ":" + chartPeriod.name + ":" + index
                    }) { index, song ->
                        val playbackKey = "chart:" + musicKey(song.artist) + ":" + musicKey(song.title)
                        TopSongHomeRow(
                            song = song,
                            index = index,
                            isPending = pendingSongId == playbackKey,
                            enabled = pendingSongId == null,
                            isRussia = chartRegion == TopSongsRegion.RUSSIA,
                            onPlay = { onPlayChart(song, chartSongs.toList()) }
                        )
                    }
                }
            }
        } else if (collected.isEmpty() && !loading && message == null) {
            item { Text("В этом разделе пока нет доступных треков",
                color = palette.muted, style = MaterialTheme.typography.bodySmall) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    // Load chosen collection or genre after a user taps a chip.
    LaunchedEffect(chosenCollection) {
        if (chosenCollection.isBlank()) {
            collected = emptyList()
            return@LaunchedEffect
        }
        val selected = chosenCollection
        val genre = genres.firstOrNull { it.first == selected }?.second
        val collectionUrl = collections.firstOrNull { it.title == selected }?.link
        val url = if (genre != null) "https://zaycev.net/genres/$genre/index.html"
                  else collectionUrl
        if (url == null) {
            loading = false
            return@LaunchedEffect
        }
        try {
            collected = parseHomeSongs(getZaycevPage(url))
            AppDiagnostics.info("Home", "collection loaded; items=" + collected.size)
        } catch (e: Exception) {
            collected = emptyList()
            AppDiagnostics.failure("Home", "collection", e)
            message = "Не удалось загрузить выбранный раздел"
        } finally { loading = false }
    }
}

@Composable
private fun HomeSongRow(
    song: HomeSong,
    onPlay: (HomeSong) -> Unit,
    pendingSongId: String?
) {
    val palette = LocalMusicFlowPalette.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .musicFlowGlass(palette, 12.dp)
            .clickable(enabled = pendingSongId != song.id) { onPlay(song) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cover by rememberCoverArt(song.artist, song.title, song.image)
        Image(
            painter = rememberAsyncImagePainter(cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = palette.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pendingSongId == song.id) {
            CircularProgressIndicator(
                color = palette.accent, strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else if (song.durationSec > 0) {
            Text(formatTrackDuration(song.durationSec),
                color = palette.accent, style = MaterialTheme.typography.labelSmall)
        } else Text("▶", color = palette.accent)
    }
}
