package com.musicflow.app

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private const val PLAYLIST_URL =
    "https://raw.githubusercontent.com/ByteBudda/RuVideoHub/main/playlist_iptv.m3u8"
private const val PLAYLIST_FALLBACK =
    "https://cdn.jsdelivr.net/gh/ByteBudda/RuVideoHub@main/playlist_iptv.m3u8"
private const val PROJECT_URL = "https://github.com/ByteBudda/RuVideoHub/"
private val TV_PANEL = Color(0xFF211A2C)
private val TV_ACCENT = Color(0xFFAC80FF)

internal data class TvChannel(
    val id: String,
    val title: String,
    val group: String,
    val logo: String?,
    val streamUrl: String
) {
    val isMusic: Boolean
        get() = title.contains("муз", ignoreCase = true) ||
            title.contains("радио", ignoreCase = true) ||
            title.contains("music", ignoreCase = true) ||
            title.contains("хит", ignoreCase = true)
    val isExpired: Boolean
        get() {
            val link = Uri.parse(streamUrl)
            val expires = link.getQueryParameter("expire")
                ?: link.getQueryParameter("e")
                ?: return false
            val timestamp = expires.toLongOrNull() ?: return false
            return timestamp in 1..(System.currentTimeMillis() / 1000L)
        }
}

private suspend fun retrieveChannels(): List<TvChannel> = withContext(Dispatchers.IO) {
    val failures = mutableListOf<String>()
    for (url in listOf(PLAYLIST_URL, PLAYLIST_FALLBACK)) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/plain, application/vnd.apple.mpegurl")
                setRequestProperty("User-Agent", "MusicFlowAndroid/0.5.0")
            }
            val raw = try {
                if (connection.responseCode !in 200..299) {
                    error("HTTP " + connection.responseCode)
                }
                // Do not permit excessive response sizes from remote playlists.
                connection.inputStream.bufferedReader().use { it.readText().take(800_000) }
            } finally {
                connection.disconnect()
            }
            val channels = parseRuVideoHubPlaylist(raw)
            if (channels.isNotEmpty()) return@withContext channels
            failures.add("Плейлист не содержит доступных HTTPS-потоков")
        } catch (e: Exception) {
            failures.add(e.message ?: "Ошибка соединения")
        }
    }
    error("Не удалось получить IPTV-плейлист. " + failures.take(2).joinToString(" / "))
}

private fun parseRuVideoHubPlaylist(text: String): List<TvChannel> {
    val result = mutableListOf<TvChannel>()
    var pendingInfo: String? = null
    var groupFallback = "Другие"
    var pendingGroup = "Другие"
    for (original in text.lineSequence()) {
        val line = original.trim()
        when {
            line.startsWith("#EXTINF:", ignoreCase = true) -> {
                pendingInfo = line
                pendingGroup = m3uAttribute(line, "group-title") ?: groupFallback
            }
            line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                pendingGroup = line.substringAfter(':').trim().ifBlank { pendingGroup }
            }
            line.startsWith("#") -> {
                // Comments and headings are ignored.
            }
            line.isBlank() -> Unit
            pendingInfo != null -> {
                val info = pendingInfo ?: continue
                pendingInfo = null
                if (!line.startsWith("https://", ignoreCase = true)) continue
                val uri = Uri.parse(line)
                // This integration accepts only the stream origin listed by RuVideoHub.
                if (!uri.host.equals("bl.rutube.ru", ignoreCase = true)) continue
                val title = (m3uAttribute(info, "tvg-name")
                    ?: info.substringAfter(",", "").trim()).ifBlank { "Без названия" }
                val sourceId = m3uAttribute(info, "rutube-id")
                    ?: "channel-" + result.size.toString()
                val logo = m3uAttribute(info, "tvg-logo")
                    ?.takeIf { it.startsWith("https://") }
                result.add(TvChannel(sourceId, title, pendingGroup, logo, line))
            }
        }
    }
    return result.distinctBy { it.id }
}

private fun m3uAttribute(line: String, key: String): String? {
    val match = Regex(Regex.escape(key) + "=\"([^\"]*)\"")
        .find(line) ?: return null
    return match.groupValues[1].takeIf { it.isNotBlank() }
}

@Composable
internal fun RuVideoHubTvScreen(
    player: MediaController?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Все") }
    var selected by remember { mutableStateOf<TvChannel?>(null) }
    var streamRefreshing by remember { mutableStateOf(false) }
    var streamRefreshError by remember { mutableStateOf<String?>(null) }
    val userSources = remember(context) {
        mutableStateListOf<IptvSource>().also { it.addAll(loadIptvSources(context)) }
    }
    var activeSourceId by remember { mutableStateOf("ruvideohub") }
    val loadSequence = remember { AtomicInteger(0) }
    var showAddSource by remember { mutableStateOf(false) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }
    var addKind by remember { mutableStateOf(IptvSourceKind.PLAYLIST) }
    var sourceInputError by remember { mutableStateOf<String?>(null) }

    fun refresh(sourceId: String = activeSourceId) {
        val request = loadSequence.incrementAndGet()
        scope.launch {
            loading = true
            error = null
            val type = if (sourceId == "ruvideohub") "RuVideoHub" else "custom"
            AppDiagnostics.info("IPTV", "loading " + type)
            try {
                val items = if (sourceId == "ruvideohub") retrieveChannels()
                    else {
                        val custom = userSources.firstOrNull { it.id == sourceId }
                            ?: throw IllegalStateException("Этот источник больше не существует")
                        fetchIptvSource(custom)
                    }
                if (request == loadSequence.get()) {
                    channels = items
                    group = "Все"
                    AppDiagnostics.info("IPTV", "loaded " + type + "; channels=" + items.size)
                }
            } catch (e: Exception) {
                if (request == loadSequence.get()) {
                    channels = emptyList()
                    error = e.message ?: "Не удалось загрузить каналы"
                    AppDiagnostics.failure("IPTV", "source " + type, e)
                }
            } finally {
                if (request == loadSequence.get()) loading = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val groups = listOf("Все", "Музыка") + channels
        .map { it.group }
        .filter { it != "Все" && it != "Музыка" }
        .distinct()
    val filtered = channels.filter { channel ->
        (group == "Все" || (group == "Музыка" && channel.isMusic) ||
            channel.group == group) &&
            (query.isBlank() ||
                channel.title.contains(query.trim(), ignoreCase = true) ||
                channel.group.contains(query.trim(), ignoreCase = true))
    }

    fun playChannel(channel: TvChannel) {
        val controller = player
        if (controller == null) {
            error = "Плеер пока подключается. Попробуйте ещё раз."
            AppDiagnostics.warning("IPTV", "play requested before connection")
            return
        }
        streamRefreshError = null
        AppDiagnostics.info("IPTV", "channel selected; source=" +
            (if (activeSourceId == "ruvideohub") "RuVideoHub" else "custom"))
        selected = channel
        val path = Uri.parse(channel.streamUrl).path.orEmpty().lowercase()
        val mediaBuilder = MediaItem.Builder()
            .setMediaId("ruvideo:" + channel.id)
            .setUri(channel.streamUrl)
        when {
            path.endsWith(".m3u8") -> mediaBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            path.endsWith(".mpd") -> mediaBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            path.endsWith(".ism") -> mediaBuilder.setMimeType(MimeTypes.APPLICATION_SS)
        }
        val media = mediaBuilder.setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(channel.title)
                    .setArtist("RuVideoHub · IPTV")
                    .setArtworkUri(channel.logo?.let(Uri::parse))
                    .build()
            ).build()
        controller.setMediaItem(media)
        controller.prepare()
        controller.play()
    }

    fun refreshPlayingChannel() {
        val channel = selected ?: return
        scope.launch {
            streamRefreshing = true
            streamRefreshError = null
            try {
                val reloaded = if (activeSourceId == "ruvideohub") retrieveChannels()
                    else {
                        val custom = userSources.firstOrNull { it.id == activeSourceId }
                            ?: throw IllegalStateException("Источник удалён")
                        fetchIptvSource(custom)
                    }
                channels = reloaded
                AppDiagnostics.info("IPTV", "stream refresh; channels=" + reloaded.size)
                val replacement = reloaded.firstOrNull { it.id == channel.id }
                if (replacement == null) {
                    streamRefreshError = "Канал исчез из обновлённого плейлиста."
                } else {
                    playChannel(replacement)
                }
            } catch (e: Exception) {
                streamRefreshError = "Не удалось обновить эфир: " + (e.message ?: "ошибка сети")
                AppDiagnostics.failure("IPTV", "stream refresh", e)
            } finally {
                streamRefreshing = false
            }
        }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Источники IPTV", style = MaterialTheme.typography.titleMedium,
                 modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = {
                sourceInputError = null
                addName = ""
                addUrl = ""
                addKind = IptvSourceKind.PLAYLIST
                showAddSource = true
            }) { Text("+ Добавить") }
            if (activeSourceId != "ruvideohub") {
                TextButton(onClick = { showDeleteSource = true }) { Text("Удалить") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = activeSourceId == "ruvideohub",
                onClick = {
                    activeSourceId = "ruvideohub"
                    refresh("ruvideohub")
                },
                label = { Text("RuVideoHub") }
            )
            userSources.forEach { item ->
                FilterChip(
                    selected = activeSourceId == item.id,
                    onClick = {
                        activeSourceId = item.id
                        refresh(item.id)
                    },
                    label = { Text(item.name, maxLines = 1) }
                )
            }
        }
        if (activeSourceId == "ruvideohub") {
            Text(
                "CUB IPTV — плагин Lampa, не плейлист. Свои ссылки M3U и M3U8 добавляй кнопкой выше.",
                color = Color.LightGray, style = MaterialTheme.typography.labelSmall
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://cub.red/iptv"))
                    )
                }
            }) { Text("Настройка плейлистов в CUB") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Прямой эфир", style = MaterialTheme.typography.titleLarge,
                     fontWeight = FontWeight.SemiBold)
                Text(
                    (if (activeSourceId == "ruvideohub") "RuVideoHub"
                     else userSources.firstOrNull { it.id == activeSourceId }?.name ?: "Мой IPTV") +
                        " · " + channels.size + " каналов",
                     color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { refresh() }, enabled = !loading) { Text("Обновить") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Найти телеканал или радио") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groups.forEach { candidate ->
                FilterChip(
                    selected = group == candidate,
                    onClick = { group = candidate },
                    label = { Text(candidate) }
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = Color(0xFFFFB3A8), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "В списке: " + filtered.size + ". Некоторые ссылки могут устареть или быть недоступны в вашей сети.",
            color = Color.LightGray,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { channel ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TV_PANEL)
                        .clickable { playChannel(channel) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(channel.logo),
                        contentDescription = channel.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(54.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF312A3B))
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.title, maxLines = 1,
                             overflow = TextOverflow.Ellipsis,
                             fontWeight = FontWeight.SemiBold)
                        Text(channel.group, color = Color.LightGray,
                             style = MaterialTheme.typography.bodySmall)
                        if (channel.isExpired) {
                            Text("Ссылка в плейлисте устарела",
                                 color = Color(0xFFFFB3A8),
                                 style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("▶", color = TV_ACCENT, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        player?.pause()
                        openTvExternal(context, channel)?.let { error = it }
                    }) { Text("Внешний") }
                }
            }
            if (filtered.isEmpty() && !loading && error == null) {
                item {
                    Text("Каналы не найдены", color = Color.LightGray,
                         modifier = Modifier.padding(12.dp))
                }
            }
        }
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
                )
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Источник: ByteBudda / RuVideoHub")
        }
    }

    if (showAddSource) {
        AlertDialog(
            onDismissRequest = { showAddSource = false },
            title = { Text("Добавить IPTV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Тип ссылки:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = addKind == IptvSourceKind.PLAYLIST,
                            onClick = { addKind = IptvSourceKind.PLAYLIST },
                            label = { Text("M3U-плейлист") }
                        )
                        FilterChip(
                            selected = addKind == IptvSourceKind.CHANNEL,
                            onClick = { addKind = IptvSourceKind.CHANNEL },
                            label = { Text("Один канал") }
                        )
                    }
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addUrl,
                        onValueChange = { addUrl = it },
                        label = { Text(if (addKind == IptvSourceKind.CHANNEL)
                            "Ссылка на поток M3U8 / MP4 / TS" else "Прямая ссылка M3U") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "HTTP не защищён шифрованием. Ссылки с токенами хранятся на устройстве.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                    sourceInputError?.let {
                        Text(it, color = Color(0xFFFFB3A8),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val attempt = runCatching { newIptvSource(addName, addUrl, addKind) }
                    attempt.onSuccess { item ->
                        if (userSources.size >= 40) {
                            sourceInputError = "Максимум 40 источников."
                        } else {
                            userSources.add(item)
                            saveIptvSources(context, userSources.toList())
                            activeSourceId = item.id
                            showAddSource = false
                            refresh(item.id)
                        }
                    }.onFailure { sourceInputError = it.message ?: "Неверная ссылка" }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showAddSource = false }) { Text("Отмена") } }
        )
    }
    if (showDeleteSource) {
        val current = userSources.firstOrNull { it.id == activeSourceId }
        AlertDialog(
            onDismissRequest = { showDeleteSource = false },
            title = { Text("Удалить источник?") },
            text = { Text(current?.name ?: "Выбранный источник") },
            confirmButton = {
                TextButton(onClick = {
                    userSources.removeAll { it.id == activeSourceId }
                    saveIptvSources(context, userSources.toList())
                    showDeleteSource = false
                    activeSourceId = "ruvideohub"
                    refresh("ruvideohub")
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSource = false }) { Text("Отмена") } }
        )
    }

    selected?.let { channel ->
        val controller = player
        if (controller != null) {
            TvPlayerDialog(
                channel = channel,
                channels = channels,
                player = controller,
                refreshing = streamRefreshing,
                refreshError = streamRefreshError,
                onSelectChannel = { playChannel(it) },
                onReload = { refreshPlayingChannel() },
                onOpenExternal = { current ->
                    controller.pause()
                    val launchError = openTvExternal(context, current)
                    if (launchError == null) selected = null
                    else streamRefreshError = launchError
                },
                onClose = {
                    if (controller.currentMediaItem?.mediaId == "ruvideo:" + channel.id) {
                        controller.pause()
                    }
                    selected = null
                }
            )
        }
    }
}
