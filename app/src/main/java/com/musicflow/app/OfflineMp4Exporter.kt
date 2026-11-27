package com.musicflow.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Export only a completed HLS offline item, without network fallback. */
internal object OfflineMp4ExportPolicy {
    fun allowed(completed: Boolean, hls: Boolean, drm: Boolean, androidVersion: Int): Boolean =
        completed && hls && !drm && androidVersion >= Build.VERSION_CODES.Q
}

internal enum class OfflineMp4ExportPhase { TRANSFORMING, SAVING }

internal data class OfflineMp4ExportProgress(
    val phase: OfflineMp4ExportPhase,
    val percent: Int?,
    val processedBytes: Long,
    val totalBytes: Long? = null
)

/** Unknown totals are indeterminate, never reported as a fake percentage. */
internal object OfflineMp4ProgressPolicy {
    fun savingPercent(copied: Long, total: Long): Int? =
        if (total <= 0L || copied < 0L) null
        else ((copied.coerceAtMost(total).toDouble() / total) * 100).toInt().coerceIn(0, 100)
}

/**
 * TEST-only: completed offline HLS SimpleCache -> Transformer -> MediaStore.
 * A real Transformer progress percentage is reported while converting.
 * The final MediaStore copy reports exact copied bytes and known output size.
 * Cancellation removes the partial output and keeps the original cache intact.
 */
@OptIn(UnstableApi::class)
internal object OfflineMp4Exporter {
    private class Active(
        val temp: File,
        val onProgress: (OfflineMp4ExportProgress) -> Unit,
        val onResult: (Boolean, String) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val cancelled = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        var transformer: Transformer? = null
        var monitor: Runnable? = null
        @Volatile var saving = false

        fun report(progress: OfflineMp4ExportProgress) {
            val send = Runnable {
                if (!cancelled.get() && !finished.get()) onProgress(progress)
            }
            if (Looper.myLooper() == Looper.getMainLooper()) send.run()
            else handler.post(send)
        }

        fun finish(success: Boolean, message: String) {
            if (!finished.compareAndSet(false, true)) return
            handler.post {
                monitor?.let(handler::removeCallbacks)
                if (current === this) current = null
                onResult(success, message)
            }
        }
    }

    // Transformer is confined to the main Looper. Do not access it from
    // the file-copy background thread.
    private var current: Active? = null

    fun start(
        context: Context,
        download: Download,
        onProgress: (OfflineMp4ExportProgress) -> Unit = {},
        onResult: (Boolean, String) -> Unit
    ) {
        check(current == null) { "Уже выполняется экспорт другого файла" }
        require(OfflineMp4ExportPolicy.allowed(
            download.state == Download.STATE_COMPLETED,
            OfflineDownloadRoute.classify(download.request.uri.toString()) ==
                OfflineTransfer.ADAPTIVE_HLS,
            download.request.keySetId != null,
            Build.VERSION.SDK_INT
        )) { "Экспорт доступен только для завершённого HLS без DRM на Android 10+" }

        val app = context.applicationContext
        val temp = File(app.cacheDir, "musicflow_export_" + UUID.randomUUID() + ".mp4")
        val task = Active(temp, onProgress, onResult)
        val source = CacheDataSource.Factory()
            .setCache(OfflineDownloads.cache(app))
            .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY)
            .setCacheWriteDataSinkFactory(null)
        val sourceFactory = DefaultMediaSourceFactory(source)
        val assetLoader = DefaultAssetLoaderFactory(
            app, DefaultDecoderFactory(app), Clock.DEFAULT,
            sourceFactory, DataSourceBitmapLoader(app)
        )
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                task.monitor?.let(task.handler::removeCallbacks)
                if (task.cancelled.get() || task.finished.get()) {
                    temp.delete()
                    return
                }
                task.saving = true
                Thread {
                    try {
                        check(temp.isFile && temp.length() > 0L) {
                            "Пустой результат экспорта"
                        }
                        val uri = saveToMovies(app, temp, task)
                        if (task.cancelled.get()) throw CancellationException()
                        AppDiagnostics.info("Downloads", "exported MP4 " + uri.scheme)
                        task.finish(true, "MP4 сохранён в Фильмы/MusicFlow")
                    } catch (cancel: CancellationException) {
                        task.finish(false, "Экспорт MP4 отменён")
                    } catch (error: Exception) {
                        AppDiagnostics.failure("Downloads", "save MP4 after export", error)
                        task.finish(false, "Не удалось сохранить MP4: " +
                            error.message.orEmpty())
                    } finally {
                        temp.delete()
                    }
                }.start()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                task.monitor?.let(task.handler::removeCallbacks)
                temp.delete()
                if (!task.cancelled.get()) {
                    AppDiagnostics.failure("Downloads", "offline MP4 export", exportException)
                    task.finish(false, "Экспорт не выполнен: " +
                        exportException.message.orEmpty())
                }
            }
        }
        try {
            val transformer = Transformer.Builder(app)
                .setAssetLoaderFactory(assetLoader)
                .addListener(listener)
                .build()
            task.transformer = transformer
            current = task
            task.report(OfflineMp4ExportProgress(
                OfflineMp4ExportPhase.TRANSFORMING, null, 0L))
            transformer.start(download.request.toMediaItem(), temp.absolutePath)
            val monitor = object : Runnable {
                override fun run() {
                    if (current !== task || task.finished.get() ||
                        task.cancelled.get() || task.saving) return
                    val result = runCatching {
                        val progress = ProgressHolder()
                        val state = transformer.getProgress(progress)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE)
                            progress.progress.coerceIn(0, 100)
                        else null
                    }.getOrNull()
                    task.report(OfflineMp4ExportProgress(
                        OfflineMp4ExportPhase.TRANSFORMING,
                        result,
                        temp.length().coerceAtLeast(0L)))
                    task.handler.postDelayed(this, 500L)
                }
            }
            task.monitor = monitor
            task.handler.post(monitor)
        } catch (error: Exception) {
            current = null
            task.monitor?.let(task.handler::removeCallbacks)
            temp.delete()
            throw error
        }
    }

    /** Can be used from the exports screen. A partial MediaStore item is removed. */
    fun cancel(): Boolean {
        val task = current ?: return false
        if (task.finished.get() || !task.cancelled.compareAndSet(false, true)) return false
        task.monitor?.let(task.handler::removeCallbacks)
        if (!task.saving) {
            task.transformer?.cancel()
            task.temp.delete()
        }
        // The active copy loop notices cancelled and deletes its uncommitted URI.
        task.finish(false, "Экспорт MP4 отменён")
        return true
    }

    private fun saveToMovies(context: Context, source: File, task: Active): Uri {
        val size = source.length().coerceAtLeast(0L)
        task.report(OfflineMp4ExportProgress(
            OfflineMp4ExportPhase.SAVING, 0, 0L, size))
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MusicFlow-" +
                System.currentTimeMillis() + ".mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/MusicFlow")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать запись для MP4")
        try {
            val output = resolver.openOutputStream(uri)
                ?: error("Не удалось открыть файл назначения")
            output.use { dest ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    var lastReport = 0L
                    while (true) {
                        if (task.cancelled.get()) throw CancellationException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        dest.write(buffer, 0, count)
                        copied += count
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReport >= 250L || copied == size) {
                            task.report(OfflineMp4ExportProgress(
                                OfflineMp4ExportPhase.SAVING,
                                OfflineMp4ProgressPolicy.savingPercent(copied, size),
                                copied, size))
                            lastReport = now
                        }
                    }
                }
            }
            if (task.cancelled.get()) throw CancellationException()
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            check(resolver.update(uri, published, null, null) > 0) {
                "Не удалось опубликовать MP4"
            }
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
