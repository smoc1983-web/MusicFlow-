package com.musicflow.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID

/** Export only a completed HLS offline item, without network fallback. */
internal object OfflineMp4ExportPolicy {
    fun allowed(completed: Boolean, hls: Boolean, drm: Boolean, androidVersion: Int): Boolean =
        completed && hls && !drm && androidVersion >= Build.VERSION_CODES.Q
}

/**
 * TEST-only: offline read from the existing SimpleCache -> Media3 Transformer -> MediaStore.
 * Source cache is never deleted. Unsupported codecs or missing segments fail without
 * publishing a corrupt MP4. Transformer may remux or re-encode depending on the tracks.
 */
@OptIn(UnstableApi::class)
internal object OfflineMp4Exporter {
    private var current: Transformer? = null

    fun start(context: Context, download: Download, onResult: (Boolean, String) -> Unit) {
        check(current == null) { "Уже выполняется экспорт другого файла" }
        require(OfflineMp4ExportPolicy.allowed(
            download.state == Download.STATE_COMPLETED,
            OfflineDownloadRoute.classify(download.request.uri.toString()) ==
                OfflineTransfer.ADAPTIVE_HLS,
            download.request.keySetId != null,
            Build.VERSION.SDK_INT
        )) { "Экспорт доступен только для завершённого HLS без DRM на Android 10+" }

        val app = context.applicationContext
        val temp = File(app.cacheDir, "musicflow_export_${UUID.randomUUID()}.mp4")
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
                Thread {
                    try {
                        if (!temp.isFile || temp.length() == 0L) {
                            error("Пустой результат экспорта")
                        }
                        val uri = saveToMovies(app, temp)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            current = null
                            onResult(true, "MP4 сохранён в Фильмы/MusicFlow")
                            AppDiagnostics.info("Downloads", "exported MP4 ${uri.scheme}")
                        }
                    } catch (error: Exception) {
                        AppDiagnostics.failure("Downloads", "save MP4 after export", error)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            current = null
                            onResult(false, "Не удалось сохранить MP4: ${error.message.orEmpty()}")
                        }
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
                AppDiagnostics.failure("Downloads", "offline MP4 export", exportException)
                temp.delete()
                current = null
                onResult(false, "Экспорт не выполнен: ${exportException.message.orEmpty()}")
            }
        }
        try {
            val transformer = Transformer.Builder(app)
                .setAssetLoaderFactory(assetLoader)
                .addListener(listener)
                .build()
            current = transformer
            transformer.start(download.request.toMediaItem(), temp.absolutePath)
        } catch (error: Exception) {
            current = null
            temp.delete()
            throw error
        }
    }

    private fun saveToMovies(context: Context, source: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MusicFlow-${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MusicFlow")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать запись для MP4")
        try {
            val output = resolver.openOutputStream(uri)
                ?: error("Не удалось открыть файл назначения")
            output.use { dest -> source.inputStream().use { it.copyTo(dest) } }
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
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
