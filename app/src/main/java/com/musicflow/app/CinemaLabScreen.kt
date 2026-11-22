package com.musicflow.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CancellationException

private val CinemaAccent = Color(0xFFAB8AFF)

/** URLs only, directly in the app. No demos, plugins or Lampa windows. */
@Composable
internal fun CinemaLabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    var sources by remember { mutableStateOf(CinemaNativeSources.sources(context)) }
    var results by remember { mutableStateOf<Map<String, List<CinemaTitle>>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var activeSource by remember { mutableStateOf("all") }
    var addDialog by remember { mutableStateOf(false) }
    var selectedFilm by remember { mutableStateOf<CinemaTitle?>(null) }
    var playing by remember { mutableStateOf<Pair<CinemaTitle, String>?>(null) }
    var favorites by remember { mutableStateOf(CinemaLabSources.favoriteKeys(context)) }

    LaunchedEffect(sources, refresh) {
        loading = sources.isNotEmpty()
        results = results.filterKeys { id -> sources.any { it.id == id } }
        try {
            for (source in sources) {
                try {
                    results = results + (source.id to CinemaNativeSources.load(source))
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    results = results + (source.id to listOf(
                        CinemaNativeSources.placeholder(source, error)
                    ))
                }
            }
        } finally {
            loading = false
        }
    }

    val allTitles = sources.flatMap { results[it.id].orEmpty() }
    val filtered = allTitles.filter { film ->
        (activeSource == "all" || film.sourceId == activeSource) &&
            (query.isBlank() || film.title.contains(query.trim(), ignoreCase = true) ||
                film.description.contains(query.trim(), ignoreCase = true))
    }
    fun beginPlayback(film: CinemaTitle) {
        val address = film.streamUrl ?: return
        if (!CinemaLabSources.isSafeHttps(address)) return
        CinemaLabSources.markRecent(context, film.key)
        selectedFilm = null
        playing = film to address
    }
    fun toggleFavorite(film: CinemaTitle) {
        favorites = if (film.key in favorites) favorites - film.key
            else favorites + film.key
        CinemaLabSources.saveFavorites(context, favorites)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = CinemaAccent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Кино", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Источники по ссылке · собственный плеер",
                    color = palette.muted, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = CinemaAccent)
            }
            Button(onClick = { addDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Источник")
            }
        }

        playing?.let { (film, media) ->
            key(film.key + ":" + media) {
                CinemaNativeInlinePlayer(
                    title = film.title,
                    sourceKey = film.key,
                    streamUrl = media,
                    allowBuiltInFallback = false,
                    onClose = { playing = null }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it.take(75) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Поиск среди добавленных фильмов") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FilterChip(
                selected = activeSource == "all",
                onClick = { activeSource = "all" },
                label = { Text("Все · " + allTitles.size) }
            )
            sources.forEach { source ->
                FilterChip(
                    selected = activeSource == source.id,
                    onClick = { activeSource = source.id },
                    label = { Text(source.name, maxLines = 1) }
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (sources.isEmpty()) item {
                Text("Добавьте ссылку на страницу фильма или прямой видеопоток.",
                    color = palette.muted, modifier = Modifier.padding(12.dp))
            }
            items(filtered, key = { it.key }) { film ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.width(88.dp).height(116.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color(0xFF302942)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!film.poster.isNullOrBlank()) {
                                    Image(
                                        rememberAsyncImagePainter(film.poster),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else Icon(Icons.Filled.Movie,
                                    contentDescription = null, tint = CinemaAccent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(film.title, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    film.year?.toString() ?: "Киноисточник по ссылке",
                                    color = CinemaAccent,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(film.description, maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                if (!film.hasPlayback) Text(
                                    "Прямая видеоссылка пока не найдена",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (film.hasPlayback) {
                                Button(onClick = { beginPlayback(film) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text("Смотреть")
                                }
                            } else {
                                OutlinedButton(onClick = { selectedFilm = film }) {
                                    Text("О странице")
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { toggleFavorite(film) }) {
                                Icon(
                                    if (film.key in favorites) Icons.Filled.Favorite
                                    else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Избранное", tint = CinemaAccent
                                )
                            }
                            IconButton(onClick = {
                                val updated = sources.filterNot { it.id == film.sourceId }
                                sources = updated
                                CinemaNativeSources.save(context, updated)
                                if (activeSource == film.sourceId) activeSource = "all"
                                if (playing?.first?.sourceId == film.sourceId) playing = null
                            }) {
                                Icon(Icons.Filled.Delete,
                                    contentDescription = "Удалить киноисточник")
                            }
                        }
                    }
                }
            }
            if (!loading && sources.isNotEmpty() && filtered.isEmpty()) item {
                Text("Нет совпадений. Измените поиск или обновите страницу.",
                    color = palette.muted, modifier = Modifier.padding(12.dp))
            }
        }
    }

    selectedFilm?.let { film ->
        AlertDialog(
            onDismissRequest = { selectedFilm = null },
            title = { Text(film.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(film.description)
                    Text(
                        if (film.hasPlayback)
                            "Найден прямой HTTPS-видеопоток."
                        else "Подтверждённый MP4/HLS-поток не найден. Приложение " +
                            "не запускает скрытые JS-плееры и не обходит ограничения сайта.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val source = sources.firstOrNull { it.id == film.sourceId }
                    source?.let {
                        Text(it.url, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFilm = null }) { Text("Закрыть") }
            }
        )
    }

    if (addDialog) {
        CinemaUrlAddDialog(
            onDismiss = { addDialog = false },
            onAdd = { address ->
                require(sources.size < 30) { "Максимум 30 киноисточников." }
                val source = CinemaNativeSources.register("", address)
                require(sources.none { it.url.equals(source.url, ignoreCase = true) }) {
                    "Ссылка уже есть в каталоге."
                }
                val updated = sources + source
                sources = updated
                CinemaNativeSources.save(context, updated)
                activeSource = source.id
                addDialog = false
            }
        )
    }
}

@Composable
private fun CinemaUrlAddDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить киноисточник") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Вставьте HTTPS-ссылку на фильм, каталог M3U/JSON " +
                        "или прямое MP4/HLS-видео. Плагины не нужны.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.take(2048) },
                    label = { Text("Ссылка на источник") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "С обычной страницы берутся открытые сведения и прямые видеоссылки, " +
                        "если они опубликованы. Закрытые плееры не обходятся.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                try { onAdd(link.trim()) }
                catch (errorFromInput: Exception) {
                    error = errorFromInput.message ?: "Неверный адрес"
                }
            }, enabled = link.isNotBlank()) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
