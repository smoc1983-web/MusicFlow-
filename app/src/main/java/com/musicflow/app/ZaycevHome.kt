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
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Duration

/** Public Zaycev editorial metadata; audio is only played from verified MusicFlow streams. */
private data class HomeSong(
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
    modifier: Modifier = Modifier
) {
    var popular by remember { mutableStateOf<List<HomeSong>>(emptyList()) }
    var fresh by remember { mutableStateOf<List<HomeSong>>(emptyList()) }
    var collected by remember { mutableStateOf<List<HomeSong>>(emptyList()) }
    var collections by remember { mutableStateOf<List<HomeCollection>>(emptyList()) }
    var chosenCollection by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
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
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Что послушаем?", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Подборки обновляются из открытого каталога. Нажми на песню, чтобы найти доступный поток.",
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSearch("") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⌕  Поиск исполнителя или песни") }
            Spacer(Modifier.height(12.dp))
            Text("Жанры", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { (name, slug) ->
                    AssistChip(
                        onClick = {
                            chosenCollection = name
                            loading = true
                            // Selection will be loaded in LaunchedEffect below.
                        },
                        label = { Text(name) }
                    )
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = Color(0xFFFFB3A8)) }
        }
        if (collected.isNotEmpty()) {
            item {
                Text(chosenCollection, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            items(collected, key = { "genre_" + it.id }) { song ->
                HomeSongRow(song, onSearch)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("🔥 Популярное", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
        }
        items(popular.take(20), key = { "top_" + it.id }) { song ->
            HomeSongRow(song, onSearch)
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text("✨ Новинки", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
        }
        items(fresh.take(15), key = { "fresh_" + it.id }) { song ->
            HomeSongRow(song, onSearch)
        }
        if (collections.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("🎼 Тематические подборки", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collections.take(10).chunked(2).forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            line.forEach { collection ->
                                OutlinedButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 62.dp),
                                    onClick = {
                                        chosenCollection = collection.title
                                        loading = true
                                    }
                                ) { Text(collection.title, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis) }
                            }
                            if (line.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }

    // Load chosen collection or genre after a user taps a chip.
    LaunchedEffect(chosenCollection) {
        if (chosenCollection.isBlank()) return@LaunchedEffect
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
private fun HomeSongRow(song: HomeSong, onSearch: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HomePanel)
            .clickable { onSearch(song.artist + " " + song.title) }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cover by rememberCoverArt(song.artist, song.title, song.image)
        Image(
            painter = rememberAsyncImagePainter(cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.LightGray,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (song.durationSec > 0) {
            Text(formatTrackDuration(song.durationSec),
                color = HomeHighlight, style = MaterialTheme.typography.labelSmall)
        } else Text("▶", color = HomeHighlight)
    }
}
