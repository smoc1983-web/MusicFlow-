package com.musicflow.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CinemaPurple = Color(0xFFBB9BFF)
private val CinemaDark = Color(0xFF151923)
private val CinemaTile = Color(0xFF23283A)
private val CinemaTextMuted = Color(0xFFB5B6C5)

@Composable
internal fun CinemaLabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(CinemaNativeSources.sources(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var shelves by remember { mutableStateOf<List<CinemaRutubeShelf>>(emptyList()) }
    var shelfCache by remember { mutableStateOf<Map<String, CinemaRutubePage>>(emptyMap()) }
    var otherCatalogs by remember { mutableStateOf<Map<String, List<CinemaTitle>>>(emptyMap()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf("Главная") }
    var selectedShelf by remember { mutableStateOf<CinemaRutubeShelf?>(null) }
    var shelfBusy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<List<CinemaTitle>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf("all") }
    var addDialog by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<CinemaTitle?>(null) }
    var detailsBusy by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    var tvShow by remember { mutableStateOf<CinemaTvShow?>(null) }
    var season by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<CinemaTitle>>(emptyList()) }
    var episodesBusy by remember { mutableStateOf(false) }
    var episodeAuditMessage by remember { mutableStateOf("") }
    var seriesLookup by remember { mutableStateOf<CinemaSeriesLookup?>(null) }
    var activeSeriesGroup by remember { mutableStateOf<CinemaSeriesGroup?>(null) }
    var playing by remember { mutableStateOf<Pair<CinemaTitle, String>?>(null) }
    var playerShow by remember { mutableStateOf<CinemaTvShow?>(null) }
    var playerSeriesLookup by remember { mutableStateOf<CinemaSeriesLookup?>(null) }
    var playerGroup by remember { mutableStateOf<CinemaSeriesGroup?>(null) }
    var playerSeason by remember { mutableIntStateOf(1) }
    var playerEpisodes by remember { mutableStateOf<List<CinemaTitle>>(emptyList()) }
    var playerEpisodesBusy by remember { mutableStateOf(false) }
    var playerAuditMessage by remember { mutableStateOf("") }
    var playerError by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(CinemaLabSources.favoriteKeys(context)) }
    val catalogListState = rememberLazyListState()
    LaunchedEffect(query, section, selectedShelf?.url, selectedSource, refresh) {
        catalogListState.scrollToItem(0)
    }

    val firstSource = sources.firstOrNull {
        it.format == CinemaSourceFormat.RUTUBE_FEED
    }
    val displayMedia = selectedSource == "all" || selectedSource == firstSource?.id

    LaunchedEffect(sources, refresh) {
        loading = true
        loadError = null
        shelves = emptyList()
        shelfCache = emptyMap()
        try {
            if (firstSource != null) {
                val available = CinemaRutubeCatalog.shelves()
                shelves = available
                val priority = (
                    available.filter { it.section == "Главная" }.take(3) +
                    available.filter { it.section == "По жанрам" }.take(4)
                ).distinctBy { it.url }
                val list = coroutineScope {
                    priority.map { item ->
                        async(Dispatchers.IO) {
                            item.url to runCatching {
                                CinemaRutubeCatalog.loadShelf(item, firstSource.id)
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                shelfCache = list.mapNotNull { (url, data) ->
                    if (data == null) null else url to data
                }.toMap()
            }
            val extras = sources.filter { it.format != CinemaSourceFormat.RUTUBE_FEED }
            for (source in extras) {
                try {
                    otherCatalogs = otherCatalogs + (source.id to
                        CinemaNativeSources.load(source))
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (e: Exception) {
                    otherCatalogs = otherCatalogs + (source.id to listOf(
                        CinemaNativeSources.placeholder(source, e)
                    ))
                }
            }
            otherCatalogs = otherCatalogs.filterKeys { key ->
                extras.any { it.id == key }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            loadError = "Каталог временно недоступен: " + (e.message ?: "ошибка сети")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedShelf?.url, firstSource?.id) {
        val wanted = selectedShelf ?: return@LaunchedEffect
        val src = firstSource ?: return@LaunchedEffect
        if (shelfCache.containsKey(wanted.url)) return@LaunchedEffect
        shelfBusy = true
        try {
            shelfCache = shelfCache + (wanted.url to
                CinemaRutubeCatalog.loadShelf(wanted, src.id))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            loadError = "Подборка недоступна: " + (e.message ?: "ошибка сети")
        } finally {
            shelfBusy = false
        }
    }

    LaunchedEffect(query, firstSource?.id, shelves, selectedSource) {
        searched = emptyList()
        val text = query.trim()
        if (text.length < 2 || firstSource == null || !displayMedia) return@LaunchedEffect
        delay(550)
        searchBusy = true
        try {
            searched = CinemaRutubeCatalog.search(text, firstSource.id, shelves)
                .distinctBy { it.key }
                .sortedWith(compareBy<CinemaTitle> {
                    if (it.title.equals(text, true)) 0
                    else if (it.title.startsWith(text, true)) 1
                    else 2
                }.thenBy { it.title.lowercase() })
            catalogListState.scrollToItem(0)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            loadError = "Поиск недоступен: " + (e.message ?: "ошибка")
        } finally {
            searchBusy = false
        }
    }

    // One episode must discover its complete show, not only nearby cached cards.
    LaunchedEffect(details?.key) {
        val film = details ?: return@LaunchedEffect
        detailsError = null
        tvShow = null
        seriesLookup = null
        episodes = emptyList()
        episodeAuditMessage = ""
        season = film.seasonNumber ?: 1
        if (film.pageUrl == null) return@LaunchedEffect
        detailsBusy = true
        try {
            val resolved = CinemaNativeSources.resolvePage(film)
            if (details?.key == film.key) {
                details = resolved
                season = resolved.seasonNumber ?: season
            }
            val id = CinemaRutubeSource.videoId(film.pageUrl)
            if (id != null) {
                val lookup = CinemaSeriesSearch.lookup(
                    resolved, shelves,
                    (shelfCache.values.flatMap { it.films } +
                        otherCatalogs.values.flatten() + searched +
                        activeSeriesGroup?.items.orEmpty()).distinctBy { it.key }
                )
                seriesLookup = lookup
                tvShow = CinemaSeriesSearch.fallbackShow(lookup, resolved)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            detailsError = e.message ?: "Не удалось загрузить информацию"
        } finally {
            detailsBusy = false
        }
    }

    LaunchedEffect(tvShow?.id, season, details?.key, seriesLookup) {
        val show = tvShow ?: activeSeriesGroup?.let { group ->
            CinemaTvShow(0, group.title, "", group.seasons.maxOrNull() ?: 1,
                group.representative.poster)
        } ?: return@LaunchedEffect
        val film = details ?: return@LaunchedEffect
        episodesBusy = true
        episodes = emptyList()
        episodeAuditMessage = "Загружаем список серий для сверки…"
        try {
            val cached = (shelfCache.values.flatMap { it.films } +
                otherCatalogs.values.flatten() + searched +
                activeSeriesGroup?.items.orEmpty() + film).distinctBy { it.key }
            val native = if (show.id > 0) {
                try {
                    CinemaRutubeSeries.loadEpisodes(show.id, season, film.sourceId)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                CinemaSeriesSearch.inSeason(
                    seriesLookup ?: CinemaSeriesLookup(null, cached, false, ""), season
                )
            }
            episodes = CinemaSeriesSearch.reconcileSeason(
                show, season, film.sourceId, shelves, cached, native,
                seriesLookup?.guide
            ) { found, progress ->
                episodes = found
                episodeAuditMessage = progress
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            detailsError = "Список серий: " + (e.message ?: "недоступен")
            episodeAuditMessage = "Не удалось завершить сверку"
        } finally {
            episodesBusy = false
        }
    }

    fun openFilm(item: CinemaTitle) {
        activeSeriesGroup = null
        details = item
    }

    fun toggleFavorite(item: CinemaTitle) {
        favorites = if (item.key in favorites) favorites - item.key
            else favorites + item.key
        CinemaLabSources.saveFavorites(context, favorites)
    }

    fun startVideo(item: CinemaTitle) {
        val uri = item.streamUrl ?: return
        if (!CinemaLabSources.isSafeHttps(uri)) return
        CinemaLabSources.markRecent(context, item.key)
        playerGroup = activeSeriesGroup
        playerSeriesLookup = seriesLookup
        playerShow = tvShow ?: activeSeriesGroup?.let { group ->
            CinemaTvShow(
                id = 0, name = group.title, description = "",
                seasons = group.seasons.maxOrNull() ?: 1,
                cover = group.representative.poster
            )
        }
        playerSeason = item.seasonNumber ?: season
        playerError = null
        details = null
        playing = item to uri
    }

    fun openEpisode(item: CinemaTitle) {
        detailsBusy = true
        detailsError = null
        scope.launch {
            try {
                val resolved = CinemaNativeSources.resolvePage(item)
                if (resolved.hasPlayback) startVideo(resolved)
                else detailsError = resolved.playbackDiagnostic ?:
                    "Для выбранной серии нет разрешённого видеопотока"
            } catch (e: Exception) {
                detailsError = e.message ?: "Не удалось открыть серию"
            } finally {
                detailsBusy = false
            }
        }
    }

    fun choosePlayerEpisode(item: CinemaTitle) {
        playerEpisodesBusy = true
        playerError = null
        scope.launch {
            try {
                val resolved = CinemaNativeSources.resolvePage(item)
                val uri = resolved.streamUrl
                if (uri != null && CinemaLabSources.isSafeHttps(uri)) {
                    CinemaLabSources.markRecent(context, resolved.key)
                    playerSeason = resolved.seasonNumber ?: playerSeason
                    playing = resolved to uri
                } else {
                    playerError = resolved.playbackDiagnostic
                        ?: "Для выбранной серии нет доступного потока."
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                playerError = e.message ?: "Не удалось открыть серию"
            } finally {
                playerEpisodesBusy = false
            }
        }
    }

    LaunchedEffect(playing?.first?.key) {
        val item = playing?.first ?: return@LaunchedEffect
        if (playerShow == null &&
            CinemaRutubeSource.videoId(item.pageUrl.orEmpty()) != null) {
            try {
                val lookup = CinemaSeriesSearch.lookup(
                    item, shelves,
                    (shelfCache.values.flatMap { it.films } +
                        otherCatalogs.values.flatten() + searched +
                        playerGroup?.items.orEmpty()).distinctBy { it.key }
                )
                playerSeriesLookup = lookup
                playerShow = CinemaSeriesSearch.fallbackShow(lookup, item)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                playerError = "Поиск сезонов: " + (e.message ?: "ошибка")
            }
        }
    }

    LaunchedEffect(playerShow?.id, playerSeason, playing?.first?.key) {
        val show = playerShow ?: return@LaunchedEffect
        val current = playing?.first ?: return@LaunchedEffect
        playerEpisodesBusy = true
        playerEpisodes = emptyList()
        playerAuditMessage = "Сверяем серии…"
        try {
            val cached = (shelfCache.values.flatMap { it.films } +
                otherCatalogs.values.flatten() + searched +
                playerGroup?.items.orEmpty() + current).distinctBy { it.key }
            val native = if (show.id > 0) {
                try {
                    CinemaRutubeSeries.loadEpisodes(
                        show.id, playerSeason, current.sourceId
                    )
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                CinemaSeriesSearch.organize(
                    playerSeriesLookup?.let {
                        CinemaSeriesSearch.inSeason(it, playerSeason)
                    }.orEmpty() + playerGroup?.episodesIn(playerSeason).orEmpty()
                )
            }
            playerEpisodes = CinemaSeriesSearch.reconcileSeason(
                show, playerSeason, current.sourceId, shelves, cached, native,
                playerSeriesLookup?.guide
            ) { found, progress ->
                playerEpisodes = found
                playerAuditMessage = progress
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            playerError = "Список серий: " + (e.message ?: "ошибка загрузки")
            playerAuditMessage = "Не удалось завершить сверку"
        } finally {
            playerEpisodesBusy = false
        }
    }

    fun requestNextShelf(item: CinemaRutubeShelf) {
        val src = firstSource ?: return
        val old = shelfCache[item.url] ?: return
        val next = old.next ?: return
        shelfBusy = true
        scope.launch {
            try {
                val extra = CinemaRutubeCatalog.loadShelf(item, src.id, next)
                shelfCache = shelfCache + (item.url to CinemaRutubePage(
                    (old.films + extra.films).distinctBy { it.id },
                    extra.next
                ))
            } catch (e: Exception) {
                loadError = "Не удалось загрузить продолжение: " +
                    (e.message ?: "ошибка")
            } finally { shelfBusy = false }
        }
    }

    val allItems = (
        shelfCache.values.flatMap { it.films } +
        otherCatalogs.values.flatten()
    ).distinctBy { it.key }
    val activeSelected = selectedShelf
    val shelfTitles = if (activeSelected == null) emptyList()
        else shelfCache[activeSelected.url]?.films.orEmpty()
    val categoryTitles = when {
        selectedSource != "all" && selectedSource != firstSource?.id ->
            otherCatalogs[selectedSource].orEmpty()
        query.trim().length >= 2 && displayMedia -> searched
        activeSelected != null -> shelfTitles
        section == "Избранное" -> allItems.filter { it.key in favorites }
        section == "Сериалы" -> allItems.filter { it.kind == CinemaKind.SERIES }
        section == "Фильмы" -> allItems.filter { it.kind == CinemaKind.MOVIE }
        else -> allItems
    }.filter { query.isBlank() || (displayMedia && query.trim().length >= 2) ||
        it.title.contains(query.trim(), ignoreCase = true) }
    val groupedSeries = CinemaSeriesGrouping.group(categoryTitles)
    val groupingEnabled = section == "Сериалы" &&
        selectedShelf == null && query.isBlank()

    Column(modifier.fillMaxWidth().background(CinemaDark)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Кино", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Text("Фильмы • Сериалы • Подборки",
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaTextMuted)
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Filled.Refresh, "Обновить каталог", tint = CinemaPurple)
            }
            TextButton(onClick = { addDialog = true }) {
                Icon(Icons.Filled.Add, null, tint = CinemaPurple)
                Text("Источник", color = CinemaPurple)
            }
        }
        playing?.let { (item, url) ->
            CinemaNativeInlinePlayer(
                title = item.title,
                sourceKey = item.key,
                streamUrl = url,
                pageUrl = item.pageUrl,
                seriesShow = playerShow,
                seriesSeason = playerSeason,
                seriesEpisodes = playerEpisodes,
                seriesLoading = playerEpisodesBusy,
                seriesExpected = playerSeriesLookup?.guide?.inSeason(playerSeason).orEmpty(),
                seriesGuideUrl = playerSeriesLookup?.guide?.url,
                seriesAuditMessage = playerAuditMessage,
                onSeasonSelected = { playerSeason = it },
                onEpisodeSelected = ::choosePlayerEpisode,
                onClose = {
                    playing = null
                    playerShow = null
                    playerGroup = null
                    playerSeriesLookup = null
                    playerError = null
                }
            )
            playerError?.let { message ->
                Text(message, color = Color(0xFFFFB4B4),
                    modifier = Modifier.padding(horizontal = 15.dp))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(90) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            placeholder = { Text("Название фильма или сериала") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            shape = RoundedCornerShape(18.dp)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Главная", "Фильмы", "Сериалы", "Жанры", "По годам",
                "По странам", "Избранное").forEach { tab ->
                FilterChip(
                    selected = section == tab,
                    onClick = {
                        section = tab
                        selectedShelf = null
                        query = ""
                        selectedSource = "all"
                    },
                    label = { Text(tab) }
                )
            }
        }
        if (sources.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                sources.forEach { source ->
                    FilterChip(
                        selected = selectedSource == source.id,
                        onClick = { selectedSource = source.id; selectedShelf = null },
                        label = { Text(source.name.take(25)) }
                    )
                }
                if (selectedSource != "all" && selectedSource != firstSource?.id) {
                    TextButton(onClick = {
                        val updated = sources.filterNot { it.id == selectedSource }
                        sources = updated
                        CinemaNativeSources.save(context, updated)
                        selectedSource = "all"
                        selectedShelf = null
                    }) { Text("Удалить источник") }
                }
                TextButton(onClick = { selectedSource = "all" }) {
                    Text("Все источники")
                }
            }
        }
        if (loading || shelfBusy || searchBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        loadError?.let { Text(it,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4B4)) }

        LazyColumn(
            state = catalogListState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 25.dp, top = 7.dp)
        ) {
            val genreTab = when (section) {
                "Жанры" -> "По жанрам"
                "По годам" -> "По годам"
                "По странам" -> "По странам"
                else -> ""
            }
            if (genreTab.isNotEmpty() && displayMedia) {
                item(key = "genre-chips") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        shelves.filter { it.section == genreTab }.forEach { option ->
                            FilterChip(
                                selected = option.url == activeSelected?.url,
                                onClick = { selectedShelf = option },
                                label = { Text(option.title) }
                            )
                        }
                    }
                }
            }

            if (section == "Главная" && query.isBlank() && activeSelected == null &&
                selectedSource == "all" && displayMedia) {
                val featured = shelves.firstOrNull { it.section == "Главная" &&
                    shelfCache[it.url]?.films?.isNotEmpty() == true }
                val hero = featured?.let { shelfCache[it.url]?.films?.firstOrNull() }
                if (hero != null) {
                    item(key = "cinema-hero") {
                        CinemaHeroCard(hero, onClick = { openFilm(hero) })
                    }
                }
                shelves.filter { shelfCache[it.url]?.films?.isNotEmpty() == true }
                    .take(8).forEach { shelf ->
                        item(key = "shelf-" + shelf.url) {
                            CinemaPosterRail(shelf.title,
                                shelfCache[shelf.url]?.films.orEmpty().take(20),
                                onClick = { openFilm(it) },
                                onMore = {
                                    selectedShelf = shelf
                                    section = "Жанры"
                                })
                        }
                    }
            } else {
                if (activeSelected != null) {
                    item(key = "selected-shelf") {
                        TextButton(onClick = { selectedShelf = null }) {
                            Icon(Icons.Filled.ArrowBack, null)
                            Text(activeSelected.title)
                        }
                    }
                }
                if (categoryTitles.isNotEmpty()) {
                    item(key = "count") {
                        Text(
                            if (query.length >= 2) "Найдено: " + categoryTitles.size
                            else activeSelected?.title ?: section,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (groupingEnabled) {
                    item(key = "series-group-hint") {
                        Text(
                            "Сериалы сгруппированы по названию. Выберите сериал, " +
                                "чтобы открыть сезоны и серии.",
                            color = CinemaTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    items(groupedSeries, key = { group ->
                        group.representative.key
                    }) { group ->
                        CinemaSeriesGroupRow(
                            group = group,
                            onClick = {
                                activeSeriesGroup = group
                                details = group.representative
                                season = group.seasons.firstOrNull() ?: 1
                            }
                        )
                    }
                } else {
                    items(categoryTitles, key = { it.key }) { film ->
                        CinemaBrowseRow(film, film.key in favorites,
                            onClick = { openFilm(film) },
                            onFavorite = { toggleFavorite(film) })
                    }
                }
                if (activeSelected?.let { shelfCache[it.url]?.next != null } == true) {
                    item(key = "load-more") {
                        Button(
                            onClick = { requestNextShelf(activeSelected) },
                            enabled = !shelfBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Загрузить ещё") }
                    }
                }
                if (categoryTitles.isEmpty() && !loading && !shelfBusy && !searchBusy) {
                    item(key = "empty") {
                        Text(if (query.length >= 2)
                            "В доступных подборках совпадений нет. Попробуйте другое название."
                            else "Выберите жанр или обновите каталог.",
                            color = CinemaTextMuted, modifier = Modifier.padding(15.dp))
                    }
                }
            }
            item(key = "rights") {
                Text("Доступность просмотра зависит от условий правообладателей.",
                    color = CinemaTextMuted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    details?.let { current ->
        Dialog(
            onDismissRequest = { details = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 670.dp),
                color = CinemaTile, shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        CinemaPoster(current.poster, Modifier.width(110.dp).height(155.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White, fontWeight = FontWeight.Bold)
                            Text(current.genre, color = CinemaPurple)
                            current.year?.let {
                                Text(it.toString(), color = CinemaTextMuted)
                            }
                        }
                    }
                    Text(current.description, color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                    if (detailsBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Загружаем сведения…", color = CinemaTextMuted)
                    }
                    detailsError?.let {
                        Text(it, color = Color(0xFFFFB4B4),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (current.hasPlayback) {
                        Button(onClick = { startVideo(current) },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Text("Смотреть")
                        }
                    }
                    val chosenGroup = activeSeriesGroup
                    val selectedShow = tvShow ?: chosenGroup?.let { group ->
                        CinemaTvShow(
                            id = 0, name = group.title, description = "",
                            seasons = group.seasons.maxOrNull() ?: 1,
                            cover = group.representative.poster
                        )
                    }
                    selectedShow?.let { show ->
                        HorizontalDivider()
                        Text("Сериал: " + show.name, fontWeight = FontWeight.Bold,
                            color = Color.White)
                        Text("Выберите сезон", color = CinemaTextMuted)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..show.seasons).forEach { n ->
                                FilterChip(
                                    selected = season == n,
                                    onClick = { season = n },
                                    label = { Text(
                                        "Сезон $n" + (seriesLookup?.guide
                                            ?.inSeason(n)?.size?.takeIf { it > 0 }
                                            ?.let { " · $it" } ?: "")
                                    ) }
                                )
                            }
                        }
                        if (episodesBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        val groupedEpisodes = CinemaSeriesSearch.mergeSeasonCandidates(
                            show.name, show.id, season, current.sourceId,
                            episodes, chosenGroup?.episodesIn(season).orEmpty()
                        )
                        val guide = seriesLookup?.guide
                        val coverage = CinemaEpisodeAudit.coverage(
                            guide, season, groupedEpisodes
                        )
                        Text(
                            if (coverage.verifiedGuide) {
                                "Эталон TVmaze: " + guide!!.seasons().size +
                                    " сезонов · " + coverage.expectedCount +
                                    " серий в сезоне. Найдено: " + coverage.foundCount
                            } else {
                                "Эталонное количество серий не подтверждено. " +
                                    "В источниках найдено: " + groupedEpisodes.size +
                                    ". Пропуски проверяются по нумерации."
                            },
                            color = CinemaTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (guide != null) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(
                                        Intent.ACTION_VIEW, Uri.parse(guide.url)
                                    ))
                                }
                            }) {
                                Text("Источник списка: TVmaze", color = CinemaPurple)
                            }
                        }
                        if (episodeAuditMessage.isNotBlank()) {
                            Text(episodeAuditMessage, color = CinemaTextMuted,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        if (groupedEpisodes.isEmpty() &&
                            coverage.expected.isEmpty() && !episodesBusy) {
                            Text("Для выбранного сезона серии пока недоступны.",
                                color = CinemaTextMuted)
                        }
                        val rows: List<Pair<Int, CinemaTitle?>> =
                            (groupedEpisodes.map { (it.episodeNumber ?: Int.MAX_VALUE) to
                                (it as CinemaTitle?) } +
                                coverage.missing.map { it to null })
                                .sortedBy { it.first }
                        rows.forEach { (number, episode) ->
                            if (episode == null) {
                                Surface(
                                    color = Color(0xFF30374A),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "Серия $number · " + if (episodesBusy)
                                            "идёт автоматический поиск"
                                        else "не найдена в подключённых источниках",
                                        color = CinemaTextMuted,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                            Surface(
                                color = Color(0xFF30374A),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    openEpisode(episode)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CinemaPoster(episode.poster,
                                        Modifier.width(83.dp).height(58.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (episode.episodeNumber?.let { "Серия $it · " } ?: "") +
                                                episode.title,
                                            maxLines = 2, color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Filled.PlayArrow, null, tint = CinemaPurple)
                                }
                            }
                            }
                        }
                    }
                    current.playbackDiagnostic?.let {
                        Text(it, color = CinemaTextMuted,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { details = null },
                        modifier = Modifier.align(Alignment.End)) {
                        Text("Закрыть", color = CinemaPurple)
                    }
                }
            }
        }
    }

    if (addDialog) {
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Добавить киноисточник") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it.take(2048) },
                        label = { Text("HTTPS-ссылка на каталог или видео") }
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Поддерживаются страницы, JSON, M3U и прямые видеопотоки.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        require(sources.size < 30) { "Максимум 30 источников" }
                        val newSource = CinemaNativeSources.register(
                            "Каталог " + (sources.size + 1), input
                        )
                        require(sources.none { it.url == newSource.url }) {
                            "Этот источник уже добавлен"
                        }
                        val updated = sources + newSource
                        sources = updated
                        CinemaNativeSources.save(context, updated)
                        addDialog = false
                    } catch (e: Exception) {
                        error = e.message ?: "Ошибка адреса"
                    }
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { addDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun CinemaPoster(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF343248)),
        contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else Icon(Icons.Filled.Movie, null, tint = CinemaPurple)
    }
}

@Composable
private fun CinemaHeroCard(film: CinemaTitle, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF342841)
    ) {
        Box(Modifier.fillMaxWidth().height(215.dp)) {
            CinemaPoster(film.poster, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.05f), Color(0xEB11121D))
            )))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("Рекомендуем", color = CinemaPurple,
                    style = MaterialTheme.typography.labelMedium)
                Text(film.title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                    Text("Подробнее", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CinemaPosterRail(
    name: String,
    films: List<CinemaTitle>,
    onClick: (CinemaTitle) -> Unit,
    onMore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onMore) { Text("Все", color = CinemaPurple) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            items(films, key = { it.key }) { film ->
                Column(Modifier.width(126.dp).clickable { onClick(film) }) {
                    CinemaPoster(film.poster, Modifier.fillMaxWidth().height(165.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(film.title, maxLines = 2, minLines = 2,
                        overflow = TextOverflow.Ellipsis, color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CinemaBrowseRow(
    film: CinemaTitle,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Surface(
        color = CinemaTile,
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CinemaPoster(film.poster, Modifier.width(89.dp).height(117.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(film.title, color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(film.genre, color = CinemaPurple,
                    style = MaterialTheme.typography.labelSmall)
                Text(film.description, color = CinemaTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Избранное", tint = CinemaPurple)
            }
        }
    }
}

@Composable
private fun CinemaSeriesGroupRow(
    group: CinemaSeriesGroup,
    onClick: () -> Unit
) {
    Surface(
        color = CinemaTile,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CinemaPoster(
                group.representative.poster,
                Modifier.width(90.dp).height(123.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Сезонов в каталоге: " + group.seasons.size +
                        " · Серий найдено: " + group.episodesCount,
                    color = CinemaPurple,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Сезоны и серии",
                    color = CinemaTextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Icon(Icons.Filled.PlayArrow, "Открыть сериал", tint = CinemaPurple)
        }
    }
}
