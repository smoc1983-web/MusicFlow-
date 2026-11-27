package com.musicflow.app

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Offline HLS/DASH cache entries and regular files in public Android Downloads. */
@OptIn(UnstableApi::class)
private sealed interface LibraryFile {
    val key: String
    val title: String
    val subtitle: String
    val ready: Boolean

    data class Cached(val value: Download) : LibraryFile {
        override val key: String get() = "cache:" + value.request.id
        override val title: String get() =
            String(value.request.data, Charsets.UTF_8)
                .substringAfter('\n', value.request.uri.lastPathSegment.orEmpty())
                .ifBlank { value.request.uri.lastPathSegment ?: "Видео" }
        override val ready: Boolean get() = value.state == Download.STATE_COMPLETED
        override val subtitle: String get() {
            val format = if (value.request.uri.toString().contains(".mpd", true))
                "DASH" else "HLS"
            val state = when (value.state) {
                Download.STATE_COMPLETED -> "Скачано для просмотра без интернета"
                Download.STATE_DOWNLOADING -> "Скачивается ${value.percentDownloaded.toInt().coerceIn(0, 100)}%"
                Download.STATE_STOPPED -> "Приостановлено"
                Download.STATE_FAILED -> "Ошибка загрузки"
                Download.STATE_QUEUED -> "Ожидает загрузки"
                else -> "Обрабатывается"
            }
            return "$format · Media3 • $state"
        }
    }

    data class Direct(
        val id: Long,
        override val title: String,
        val uri: Uri?,
        val mime: String,
        val status: Int
    ) : LibraryFile {
        override val key: String get() = "direct:$id"
        override val ready: Boolean get() =
            status == DownloadManager.STATUS_SUCCESSFUL && uri != null
        override val subtitle: String get() = when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> "Файл в папке Загрузки/MusicFlow"
            DownloadManager.STATUS_RUNNING -> "Скачивается через Android"
            DownloadManager.STATUS_PENDING -> "В очереди загрузки"
            DownloadManager.STATUS_PAUSED -> "Загрузка приостановлена"
            DownloadManager.STATUS_FAILED -> "Ошибка загрузки"
            else -> "Состояние загрузки неизвестно"
        }
    }
}

/** DownloadManager returns own requests, including files from previous TEST builds. */
private fun loadDirectFiles(context: Context): List<LibraryFile.Direct> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query()
    val result = mutableListOf<LibraryFile.Direct>()
    manager.query(query)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val mimeCol = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (idCol < 0 || uriCol < 0 || statusCol < 0) return@use
        while (cursor.moveToNext()) {
            val local = cursor.getString(uriCol).orEmpty()
            // Never show APK updater downloads or other apps' files in the media library.
            if (!local.contains("/MusicFlow/", ignoreCase = true) &&
                !local.contains("%2FMusicFlow%2F", ignoreCase = true)) continue
            val id = cursor.getLong(idCol)
            val status = cursor.getInt(statusCol)
            val uri = if (status == DownloadManager.STATUS_SUCCESSFUL)
                manager.getUriForDownloadedFile(id) else null
            val title = if (titleCol >= 0) cursor.getString(titleCol).orEmpty() else ""
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
            result += LibraryFile.Direct(id, title.ifBlank { "Медиафайл" }, uri,
                mime.ifBlank { "application/octet-stream" }, status)
        }
    }
    return result.sortedByDescending { it.id }
}

@OptIn(UnstableApi::class)
@Composable
internal fun MusicDownloadsScreen(
    onAddLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<LibraryFile>>(emptyList()) }
    var selected by remember { mutableStateOf<LibraryFile?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) {
        while (true) {
            try {
                files = withContext(Dispatchers.IO) {
                    OfflineDownloads.snapshot(context).map { LibraryFile.Cached(it) } +
                        loadDirectFiles(context)
                }
                loadError = null
            } catch (error: Exception) {
                AppDiagnostics.failure("Downloads", "list local media", error)
                loadError = "Не удалось открыть список файлов"
            }
            delay(2500)
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Загруженные файлы", style = MaterialTheme.typography.titleLarge)
        Text("Обычные MP4/MP3 доступны другим плеерам. HLS/DASH хранятся в офлайн-кеше Media3 и воспроизводятся внутри MusicFlow.",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddLink) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Скачать")
            }
            OutlinedButton(onClick = { refresh++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("Обновить")
            }
            OutlinedButton(onClick = { MediaDownloads.openDownloads(context) }) {
                Text("Файлы Android")
            }
        }
        if (loadError != null) Text(loadError!!, color = MaterialTheme.colorScheme.error)
        if (files.isEmpty()) {
            Text("Файлов пока нет. Скачай песню или фильм через кнопку «Скачать».",
                modifier = Modifier.padding(vertical = 24.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp)) {
                items(files, key = { it.key }) { file ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(file.title, style = MaterialTheme.typography.titleMedium)
                            Text(file.subtitle, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                TextButton(onClick = { selected = file },
                                    enabled = file.ready) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text("Смотреть")
                                }
                                if (file is LibraryFile.Direct) {
                                    TextButton(onClick = {
                                        openInOtherPlayer(context, file.uri, file.mime)
                                    }, enabled = file.ready) {
                                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                                        Text("Другое приложение")
                                    }
                                } else if (file is LibraryFile.Cached) {
                                    TextButton(onClick = {
                                        android.widget.Toast.makeText(context,
                                            "HLS/DASH хранится в кеше Media3, это не отдельный MP4. Используй плеер MusicFlow.",
                                            android.widget.Toast.LENGTH_LONG).show()
                                    }) { Text("Почему нет внешнего плеера?") }
                                    TextButton(onClick = {
                                        OfflineDownloads.remove(context, file.value.request.id)
                                        refresh++
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                        Text("Удалить")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { current ->
        if (current.ready) {
            OfflineFilePlayer(current, onDismiss = { selected = null })
        } else {
            LaunchedEffect(current) { selected = null }
        }
    }
}

private fun openInOtherPlayer(context: Context, uri: Uri?, mime: String) {
    if (uri == null) return
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть файл через"))
    } catch (error: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "На телефоне нет подходящего плеера",
            android.widget.Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
        AppDiagnostics.failure("Downloads", "external player", error)
        android.widget.Toast.makeText(context, "Не удалось передать файл плееру",
            android.widget.Toast.LENGTH_LONG).show()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun OfflineFilePlayer(file: LibraryFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(file.key) {
        val sourceFactory = when (file) {
            is LibraryFile.Cached -> {
                // Offline-only: no fallback to the network when the file is missing.
                val source = CacheDataSource.Factory()
                    .setCache(OfflineDownloads.cache(context))
                    .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY)
                    .setCacheWriteDataSinkFactory(null)
                DefaultMediaSourceFactory(source)
            }
            is LibraryFile.Direct ->
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
        }
        ExoPlayer.Builder(context).setMediaSourceFactory(sourceFactory)
            .build().apply {
                setMediaItem(when (file) {
                    is LibraryFile.Cached -> file.value.request.toMediaItem()
                    is LibraryFile.Direct -> MediaItem.fromUri(file.uri!!)
                })
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp)) {
            Text(file.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                }
            }, update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(260.dp))
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Закрыть")
            }
        }
    }
}
