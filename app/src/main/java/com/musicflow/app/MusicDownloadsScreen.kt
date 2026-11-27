package com.musicflow.app

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.ui.AspectRatioFrameLayout
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
import kotlinx.coroutines.launch
import java.util.Locale

/** Offline HLS/DASH cache entries and regular files in public Android Downloads. */
@OptIn(UnstableApi::class)
private sealed interface LibraryFile {
    val key: String
    val title: String
    val subtitle: String
    val ready: Boolean
    val state: DownloadUiState
    val progress: Float?
    val actions: Set<DownloadUiAction>
    val isMusic: Boolean
    val downloadedBytes: Long
    val expectedBytes: Long?

    data class Cached(val value: Download) : LibraryFile {
        override val key: String get() = "cache:" + value.request.id
        override val title: String get() =
            String(value.request.data, Charsets.UTF_8)
                .substringAfter('\n', value.request.uri.lastPathSegment.orEmpty())
                .ifBlank { value.request.uri.lastPathSegment ?: "Видео" }
        override val ready: Boolean get() = value.state == Download.STATE_COMPLETED
        override val downloadedBytes: Long get() = value.bytesDownloaded.coerceAtLeast(0L)
        override val expectedBytes: Long? get() = null
        override val state: DownloadUiState get() = when(value.state) {
            Download.STATE_QUEUED -> DownloadUiState.QUEUED
            Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadUiState.DOWNLOADING
            Download.STATE_STOPPED -> DownloadUiState.STOPPED
            Download.STATE_FAILED -> DownloadUiState.FAILED
            Download.STATE_COMPLETED -> DownloadUiState.COMPLETED
            Download.STATE_REMOVING -> DownloadUiState.REMOVING
            else -> DownloadUiState.QUEUED
        }
        override val progress: Float? get() =
            value.percentDownloaded.takeIf { it >= 0f }?.div(100f)?.coerceIn(0f, 1f)
        override val actions: Set<DownloadUiAction> get() =
            DownloadActionPolicy.forMedia3(state)
        override val isMusic: Boolean get() =
            String(value.request.data, Charsets.UTF_8).startsWith("MUSIC\n")
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
        val status: Int,
        val sourceUrl: String,
        override val downloadedBytes: Long,
        val totalBytes: Long
    ) : LibraryFile {
        override val key: String get() = "direct:$id"
        override val expectedBytes: Long? get() = totalBytes.takeIf { it > 0L }
        override val state: DownloadUiState get() = when (status) {
            DownloadManager.STATUS_RUNNING -> DownloadUiState.DOWNLOADING
            DownloadManager.STATUS_PENDING -> DownloadUiState.QUEUED
            DownloadManager.STATUS_PAUSED -> DownloadUiState.PAUSED_SYSTEM
            DownloadManager.STATUS_FAILED -> DownloadUiState.FAILED
            DownloadManager.STATUS_SUCCESSFUL -> DownloadUiState.COMPLETED
            else -> DownloadUiState.FAILED
        }
        override val actions: Set<DownloadUiAction> get() =
            DownloadActionPolicy.forDirect(state)
        override val isMusic: Boolean get() =
            mime.startsWith("audio/", ignoreCase = true)
        override val progress: Float? get() =
            if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes)
                .toFloat().coerceIn(0f, 1f) else null
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
        val descriptionCol = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
        val remoteCol = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
        val doneCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        if (idCol < 0 || uriCol < 0 || statusCol < 0) return@use
        while (cursor.moveToNext()) {
            val local = cursor.getString(uriCol).orEmpty()
            val id = cursor.getLong(idCol)
            val description = if (descriptionCol >= 0) cursor.getString(descriptionCol).orEmpty() else ""
            // Android may expose a content:// URI without a recognizable /MusicFlow/ path.
            // Track our enqueued IDs and also recognize the legacy MusicFlow description.
            val legacyPath = local.contains("/MusicFlow/", ignoreCase = true) ||
                local.contains("%2FMusicFlow%2F", ignoreCase = true)
            if (!MediaDownloads.isOwnFile(context, id, description) && !legacyPath) continue
            val status = cursor.getInt(statusCol)
            val uri = if (status == DownloadManager.STATUS_SUCCESSFUL)
                manager.getUriForDownloadedFile(id) else null
            val title = if (titleCol >= 0) cursor.getString(titleCol).orEmpty() else ""
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
            val remote = if (remoteCol >= 0) cursor.getString(remoteCol).orEmpty() else ""
            val done = if (doneCol >= 0) cursor.getLong(doneCol) else 0L
            val total = if (totalCol >= 0) cursor.getLong(totalCol) else -1L
            result += LibraryFile.Direct(id, title.ifBlank { "Медиафайл" }, uri,
                mime.ifBlank { "application/octet-stream" }, status,
                remote, done, total)
        }
    }
    return result.sortedByDescending { it.id }
}

private data class CleanupPrompt(
    val title: String,
    val estimate: CleanupEstimate,
    val hasActive: Boolean
)

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes Б"
    val unit = when {
        bytes >= 1_073_741_824L -> 1_073_741_824.0
        bytes >= 1_048_576L -> 1_048_576.0
        else -> 1024.0
    }
    val label = when (unit) {
        1_073_741_824.0 -> "ГБ"
        1_048_576.0 -> "МБ"
        else -> "КБ"
    }
    return String.format(Locale.getDefault(), "%.1f %s", bytes / unit, label)
}

@OptIn(UnstableApi::class)
private fun LibraryFile.cleanupCandidate(): CleanupCandidate = when (this) {
    is LibraryFile.Cached -> CleanupCandidate(
        key, true, state != DownloadUiState.REMOVING,
        value.bytesDownloaded.takeIf { it >= 0L }
    )
    is LibraryFile.Direct -> CleanupCandidate(
        key, false, true,
        (if (ready && totalBytes > 0L) totalBytes else downloadedBytes)
            .takeIf { it > 0L }
    )
}

@OptIn(UnstableApi::class)
@Composable
internal fun MusicDownloadsScreen(
    onAddLink: () -> Unit,
    modifier: Modifier = Modifier,
    onBeforePlayback: () -> Unit = {}
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<LibraryFile>>(emptyList()) }
    var selected by remember { mutableStateOf<LibraryFile?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var transferRates by remember { mutableStateOf<Map<String, DownloadSpeedSample>>(emptyMap()) }
    var pendingRemoval by remember { mutableStateOf<CleanupPrompt?>(null) }
    var pendingExport by remember { mutableStateOf<LibraryFile.Cached?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportTitle by remember { mutableStateOf("") }
    var exportProgress by remember { mutableStateOf<OfflineMp4ExportProgress?>(null) }
    var selectedFilter by remember { mutableStateOf("Все") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var removing by remember { mutableStateOf(false) }
    val cleanupScope = rememberCoroutineScope()
    val candidates = files.map { it.cleanupCandidate() }
    val shownFiles = files.filter { file ->
        selectedFilter == "Все" || (selectedFilter == "Музыка") == file.isMusic
    }
    val validKeys = candidates.filter { it.removable }.map { it.key }.toSet()
    val visibleKeys = shownFiles.map { it.key }.toSet().intersect(validKeys)
    val markedKeys = selectedKeys.intersect(validKeys)

    fun requestCleanup(scope: CleanupScope, keys: Set<String> = emptySet(), single: LibraryFile? = null) {
        if (removing || exporting) return
        val estimate = DownloadCleanupPolicy.plan(candidates, scope, keys)
        if (estimate.count == 0) return
        val title = when {
            single != null -> if (single.ready) "Удалить файл?" else "Отменить загрузку?"
            scope == CleanupScope.SELECTED -> "Удалить выбранные загрузки?"
            scope == CleanupScope.ALL -> "Удалить все загрузки?"
            else -> "Очистить кеш HLS/DASH?"
        }
        pendingRemoval = CleanupPrompt(
            title, estimate,
            files.any { it.key in estimate.keys && !it.ready }
        )
    }

    LaunchedEffect(validKeys) {
        selectedKeys = selectedKeys.intersect(validKeys)
    }

    fun runAction(file: LibraryFile, action: DownloadUiAction) {
        try {
            when (action) {
                DownloadUiAction.PLAY -> {
                    onBeforePlayback()
                    selected = file
                }
                DownloadUiAction.OPEN_EXTERNAL -> if (file is LibraryFile.Direct) {
                    onBeforePlayback()
                    openInOtherPlayer(context, file.uri, file.mime)
                }
                DownloadUiAction.PAUSE -> if (file is LibraryFile.Cached)
                    OfflineDownloads.pause(context, file.value.request.id)
                DownloadUiAction.RESUME -> if (file is LibraryFile.Cached)
                    OfflineDownloads.resume(context, file.value.request.id)
                DownloadUiAction.RETRY -> when (file) {
                    is LibraryFile.Cached -> OfflineDownloads.retry(context, file.value)
                    is LibraryFile.Direct -> {
                        val kind = if (file.isMusic) DownloadMediaKind.MUSIC
                            else DownloadMediaKind.VIDEO
                        val result = MediaDownloads.enqueue(context, file.sourceUrl,
                            file.title, kind)
                        MediaDownloads.showResult(context, result)
                        if (result is DownloadPlanResult.Accepted)
                            MediaDownloads.cancel(context, file.id)
                    }
                }
                DownloadUiAction.OPEN_SYSTEM -> MediaDownloads.openDownloads(context)
                DownloadUiAction.CANCEL, DownloadUiAction.DELETE ->
                    requestCleanup(CleanupScope.SELECTED, setOf(file.key), file)
            }
            refresh++
        } catch (error: Exception) {
            AppDiagnostics.failure("Downloads", "action " + action, error)
            android.widget.Toast.makeText(context,
                "Не удалось выполнить действие: ${action.name}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(refresh) {
        // Media3 DownloadManager must be constructed on the main Looper; only
        // the persisted database snapshot is read on the IO dispatcher.
        OfflineDownloads.manager(context)
        while (true) {
            try {
                val nextFiles = withContext(Dispatchers.IO) {
                    OfflineDownloads.snapshot(context).map { LibraryFile.Cached(it) } +
                        loadDirectFiles(context)
                }
                val now = android.os.SystemClock.elapsedRealtime()
                transferRates = nextFiles.associate { file ->
                    file.key to DownloadSpeed.next(
                        transferRates[file.key], file.downloadedBytes, now
                    )
                }
                files = nextFiles
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
        Text("Загрузки · ход скачивания", style = MaterialTheme.typography.titleLarge)
        Text("Здесь видны текущие загрузки: процент выполнения, объём, скорость и оставшееся время (если сервер сообщил размер). Можно остановить, продолжить или открыть готовый файл. Обычные MP4/MP3 доступны другим плеерам; HLS/DASH хранятся внутри MusicFlow.",
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Все", "Музыка", "Видео").forEach { option ->
                FilterChip(
                    selected = selectedFilter == option,
                    onClick = { selectedFilter = option },
                    label = { Text(option) }
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectionMode) {
                OutlinedButton(onClick = { selectionMode = false; selectedKeys = emptySet() }) {
                    Text("Готово")
                }
                OutlinedButton(onClick = { selectedKeys = selectedKeys + visibleKeys },
                    enabled = visibleKeys.isNotEmpty() && !removing) {
                    Text("Выбрать показанные")
                }
                OutlinedButton(onClick = { selectedKeys = selectedKeys - visibleKeys },
                    enabled = markedKeys.isNotEmpty() && !removing) {
                    Text("Снять выбор")
                }
            } else {
                OutlinedButton(onClick = { selectionMode = true }, enabled = !removing) {
                    Text("Выбрать файлы")
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { requestCleanup(CleanupScope.SELECTED, markedKeys) },
                enabled = markedKeys.isNotEmpty() && !removing) {
                Text("Удалить выбранные (${markedKeys.size})")
            }
            OutlinedButton(onClick = { requestCleanup(CleanupScope.ALL) },
                enabled = validKeys.isNotEmpty() && !removing) {
                Text("Удалить всё")
            }
            OutlinedButton(onClick = { requestCleanup(CleanupScope.STREAM_CACHE) },
                enabled = candidates.any { it.stream && it.removable } && !removing) {
                Text("Очистить HLS/DASH")
            }
        }
        if (selectionMode) Text("Отмечено: ${markedKeys.size}. «Выбрать показанные» относится к текущему фильтру.",
            style = MaterialTheme.typography.bodySmall)
        if (exporting) {
            val progress = exportProgress
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Экспорт MP4", style = MaterialTheme.typography.titleMedium)
                    Text(exportTitle, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2)
                    val phase = when (progress?.phase) {
                        OfflineMp4ExportPhase.TRANSFORMING -> "Создание MP4"
                        OfflineMp4ExportPhase.SAVING -> "Сохранение в «Фильмы/MusicFlow»"
                        null -> "Подготовка экспорта"
                    }
                    val percent = progress?.percent
                    Text(phase + (percent?.let { " · $it%" } ?: " · расчёт прогресса…"),
                        style = MaterialTheme.typography.labelMedium)
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (progress != null) {
                        val bytes = humanBytes(progress.processedBytes)
                        val total = progress.totalBytes?.takeIf { it > 0L }
                            ?.let { " из " + humanBytes(it) }.orEmpty()
                        Text((if (progress.phase == OfflineMp4ExportPhase.TRANSFORMING)
                            "Создано временного MP4: " else "Скопировано: ") +
                            bytes + total, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { OfflineMp4Exporter.cancel() }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Отменить экспорт")
                    }
                }
            }
        } else if (removing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (loadError != null) Text(loadError!!, color = MaterialTheme.colorScheme.error)
        if (shownFiles.isEmpty()) {
            Text("Здесь пока нет загруженных файлов.",
                modifier = Modifier.padding(vertical = 24.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.weight(1f)) {
                items(shownFiles, key = { it.key }) { file ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode && file.key in validKeys) {
                                    Checkbox(
                                        checked = file.key in markedKeys,
                                        onCheckedChange = { checked ->
                                            selectedKeys = if (checked) selectedKeys + file.key
                                                else selectedKeys - file.key
                                        },
                                        enabled = !removing
                                    )
                                }
                                Text(file.title, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                            }
                            Text(file.subtitle, style = MaterialTheme.typography.bodySmall)
                            if (file is LibraryFile.Cached && file.ready &&
                                !file.isMusic && OfflineMp4ExportPolicy.allowed(
                                    true,
                                    OfflineDownloadRoute.classify(file.value.request.uri.toString()) ==
                                        OfflineTransfer.ADAPTIVE_HLS,
                                    file.value.request.keySetId != null,
                                    android.os.Build.VERSION.SDK_INT
                                )) {
                                OutlinedButton(onClick = { pendingExport = file },
                                    enabled = !exporting && !removing) {
                                    Text("Экспорт MP4")
                                }
                            }
                            if (file.state == DownloadUiState.DOWNLOADING ||
                                file.state == DownloadUiState.PAUSED_SYSTEM ||
                                file.state == DownloadUiState.STOPPED) {
                                val rate = transferRates[file.key]?.bytesPerSecond ?: 0L
                                val amount = humanBytes(file.downloadedBytes)
                                val total = file.expectedBytes?.let { " из ${humanBytes(it)}" }.orEmpty()
                                val speedText = if (rate > 0L) " · ${humanBytes(rate)}/с" else ""
                                val remaining = file.expectedBytes?.let { it - file.downloadedBytes }
                                val eta = if (rate > 0L && remaining != null && remaining > 0L)
                                    " · осталось ~${remaining / rate} с" else ""
                                Text(amount + total + speedText + eta,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (file.state in listOf(DownloadUiState.DOWNLOADING,
                                    DownloadUiState.STOPPED, DownloadUiState.PAUSED_SYSTEM,
                                    DownloadUiState.QUEUED)) {
                                file.progress?.let { value ->
                                    LinearProgressIndicator(
                                        progress = { value },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                file.actions.forEach { action ->
                                    val label = when (action) {
                                        DownloadUiAction.PAUSE -> "Пауза"
                                        DownloadUiAction.RESUME -> "Продолжить"
                                        DownloadUiAction.CANCEL -> "Отменить"
                                        DownloadUiAction.RETRY -> "Повторить"
                                        DownloadUiAction.OPEN_SYSTEM -> "Загрузки Android"
                                        DownloadUiAction.PLAY -> "Смотреть"
                                        DownloadUiAction.OPEN_EXTERNAL -> "Другой плеер"
                                        DownloadUiAction.DELETE -> "Удалить"
                                    }
                                    TextButton(onClick = { runAction(file, action) },
                                        enabled = !removing,
                                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        when (action) {
                                            DownloadUiAction.PAUSE ->
                                                Icon(Icons.Filled.Pause, contentDescription = null)
                                            DownloadUiAction.RESUME, DownloadUiAction.PLAY ->
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                            DownloadUiAction.CANCEL ->
                                                Icon(Icons.Filled.Close, contentDescription = null)
                                            DownloadUiAction.DELETE ->
                                                Icon(Icons.Filled.Delete, contentDescription = null)
                                            DownloadUiAction.RETRY ->
                                                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                            DownloadUiAction.OPEN_EXTERNAL ->
                                                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                                            else -> Unit
                                        }
                                        Text(label, maxLines = 1)
                                    }
                                }
                            }
                            if (file is LibraryFile.Direct &&
                                file.state == DownloadUiState.PAUSED_SYSTEM) {
                                Text("Android продолжит загрузку при восстановлении сети. " +
                                    "Здесь нельзя принудительно возобновить отдельный системный файл.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingExport?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text("Сохранить отдельный MP4?") },
            text = {
                Text("MusicFlow попытается преобразовать завершённый HLS из офлайн-кеша " +
                    "в MP4 и сохранить в Фильмы/MusicFlow. Это потребует дополнительного " +
                    "свободного места. Не все видеокодеки совместимы; исходный кеш не удаляется.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExport = null
                    try {
                        exporting = true
                        exportTitle = file.title
                        exportProgress = null
                        OfflineMp4Exporter.start(
                            context, file.value,
                            onProgress = { progress -> exportProgress = progress }
                        ) { success, message ->
                            exporting = false
                            exportProgress = null
                            android.widget.Toast.makeText(context, message,
                                android.widget.Toast.LENGTH_LONG).show()
                            refresh++
                        }
                    } catch (error: Exception) {
                        exporting = false
                        exportProgress = null
                        AppDiagnostics.failure("Downloads", "start offline export", error)
                        android.widget.Toast.makeText(context,
                            "Экспорт не запущен: ${error.message.orEmpty()}",
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("Экспортировать") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExport = null }) { Text("Отмена") }
            }
        )
    }
    pendingRemoval?.let { prompt ->
        AlertDialog(
            onDismissRequest = { if (!removing) pendingRemoval = null },
            title = { Text(prompt.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Затронуто загрузок: ${prompt.estimate.count} (HLS/DASH: ${prompt.estimate.streamCount}).")
                    Text("Оценка освобождаемого места: ${humanBytes(prompt.estimate.knownBytes)}" +
                        if (prompt.estimate.unknownSizeCount > 0)
                            " + ${prompt.estimate.unknownSizeCount} с неизвестным размером"
                        else "")
                    Text("Это приблизительная оценка: сегменты потоков могут использоваться совместно.")
                    if (prompt.hasActive) {
                        Text("Незавершённые загрузки будут отменены; их нельзя будет продолжить с прежнего места.")
                    }
                    Text("Удаляются только загрузки MusicFlow. Операция необратима.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = files.filter { it.key in prompt.estimate.keys &&
                        it.key in validKeys }.distinctBy { it.key }
                    pendingRemoval = null
                    if (toDelete.isNotEmpty()) cleanupScope.launch {
                        removing = true
                        if (selected?.key?.let { it in prompt.estimate.keys } == true) selected = null
                        var directDeleted = 0
                        var streamRequested = 0
                        var failures = 0
                        try {
                            for (file in toDelete) {
                                try {
                                    when (file) {
                                        is LibraryFile.Cached -> {
                                            OfflineDownloads.remove(context, file.value.request.id)
                                            streamRequested++
                                        }
                                        is LibraryFile.Direct -> {
                                            if (withContext(Dispatchers.IO) {
                                                MediaDownloads.cancel(context, file.id)
                                            }) directDeleted++ else failures++
                                        }
                                    }
                                } catch (error: Exception) {
                                    failures++
                                    AppDiagnostics.failure("Downloads", "bulk cleanup " + file.key, error)
                                }
                            }
                            val message = "Удалено обычных файлов: $directDeleted; " +
                                "передано Media3: $streamRequested" +
                                if (failures > 0) "; ошибок: $failures" else ""
                            android.widget.Toast.makeText(context, message,
                                android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            selectedKeys = selectedKeys - prompt.estimate.keys
                            refresh++
                            removing = false
                        }
                    }
                }, enabled = !removing) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }, enabled = !removing) {
                    Text("Назад")
                }
            }
        )
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
    val activity = context as? Activity
    // Keep the SAME player when switching compact/fullscreen. A new player
    // would restart an offline video and may release its Media3 cache too early.
    val player = remember(file.key) {
        val sourceFactory = when (file) {
            is LibraryFile.Cached -> {
                // Offline-only: never silently fetch the missing segments online.
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
    var fullScreen by remember(file.key) { mutableStateOf(false) }
    val canExpand = !file.isMusic
    BackHandler(enabled = fullScreen) { fullScreen = false }

    // MainActivity declares configChanges for orientation/screenSize, allowing
    // a landscape transition without restarting the one Media3 player.
    DisposableEffect(activity, fullScreen) {
        val previousOrientation = activity?.requestedOrientation
        if (fullScreen && canExpand) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fullScreen && canExpand && previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    Dialog(
        onDismissRequest = { if (fullScreen) fullScreen = false else onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = !fullScreen,
            decorFitsSystemWindows = !fullScreen
        )
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, fullScreen) {
            if (fullScreen && window != null) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                if (fullScreen && window != null) {
                    WindowInsetsControllerCompat(window, window.decorView).show(
                        WindowInsetsCompat.Type.systemBars()
                    )
                }
            }
        }

        if (fullScreen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                OfflineMediaPlayerView(
                    player = player,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { fullScreen = false }) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Свернуть видео",
                            tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть просмотр",
                            tint = Color.White)
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.title, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f), maxLines = 3
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть просмотр")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OfflineMediaPlayerView(
                    player = player,
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
                if (canExpand) {
                    TextButton(
                        onClick = { fullScreen = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("На весь экран")
                    }
                }
            }
        }
    }
}

/**
 * One rendering policy for both direct MP4 and cached HLS/DASH.
 * Retain player instance and seek position when moving between windows.
 */
@OptIn(UnstableApi::class)
@Composable
private fun OfflineMediaPlayerView(
    player: ExoPlayer,
    modifier: Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null; it.keepScreenOn = false },
        modifier = modifier.background(Color.Black)
    )
}
