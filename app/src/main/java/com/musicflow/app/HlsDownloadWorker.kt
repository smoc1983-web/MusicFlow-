package com.musicflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Finite, unencrypted HLS MPEG-TS VOD -> one .ts file in Downloads.
 * Explicitly rejects DRM, live playlists, fMP4 and discontinuities.
 * Not a transcoder: no MP4 conversion, protected key extraction or auth bypass.
 */
internal class HlsDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    companion object {
        const val TAG = "musicflow-hls-vod"
        const val URL_INPUT = "playlist_url"
        const val TITLE_INPUT = "media_title"
        const val KIND_INPUT = "media_kind"
        const val REFERER_INPUT = "media_referer"
        private const val CHANNEL = "musicflow_hls_downloads"
        private const val MAX_PLAYLIST_BYTES = 1_048_576
        private const val MAX_SEGMENT_BYTES = 120L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 3L * 1024 * 1024 * 1024
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(URL_INPUT).orEmpty()
        val kind = runCatching {
            DownloadMediaKind.valueOf(inputData.getString(KIND_INPUT).orEmpty())
        }.getOrNull() ?: return@withContext fail("Неизвестный тип медиа")
        val title = inputData.getString(TITLE_INPUT).orEmpty().ifBlank { "HLS" }
        val referer = inputData.getString(REFERER_INPUT)
            ?.takeIf(MediaDownloadPolicy::isSafeHttps)
        val choice = MediaDownloadPolicy.plan(url, title, kind)
        if (choice !is DownloadPlanResult.Accepted ||
            choice.value.transferMode != MediaTransferMode.HLS_VOD) {
            return@withContext fail("Требуется прямая ссылка HTTPS на HLS .m3u8")
        }

        var storedUri: Uri? = null
        var legacyFile: File? = null
        var completed = false
        try {
            setForeground(foreground(title, "Чтение HLS-плейлиста", 0))
            var currentUrl = url
            var playlist: HlsPlaylistResult.Media? = null
            // Master -> variant -> segments. Do not allow infinite playlist cycles.
            repeat(4) {
                val response = readManifest(currentUrl, referer)
                when (val result = HlsPlaylistPolicy.parse(currentUrl, response, kind)) {
                    is HlsPlaylistResult.Unsupported -> throw IOException(result.reason)
                    is HlsPlaylistResult.Master -> currentUrl = result.childPlaylist
                    is HlsPlaylistResult.Media -> {
                        playlist = result
                        return@repeat
                    }
                }
            }
            val media = playlist ?: throw IOException("Слишком много вложенных HLS-плейлистов")
            val extension = media.extension
            val mime = when (extension) {
                "ts" -> "video/mp2t"
                "aac" -> "audio/aac"
                "mp3" -> "audio/mpeg"
                else -> throw IOException("Неподдерживаемый формат HLS")
            }
            val expectedDirectory = choice.value.relativeDirectory
            val name = choice.value.fileName.substringBeforeLast('.') + "-" +
                System.currentTimeMillis() + "." + extension
            val target: OutputStream
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + expectedDirectory)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                storedUri = applicationContext.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw IOException("Не удалось создать файл в Загрузках")
                target = applicationContext.contentResolver.openOutputStream(storedUri!!)
                    ?: throw IOException("Не удалось открыть созданный файл")
            } else {
                // Legacy Android: external storage permission is checked before enqueuing.
                val root = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
                val directory = File(root, expectedDirectory)
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException("Не удалось создать папку загрузок")
                }
                legacyFile = File(directory, name)
                target = legacyFile!!.outputStream()
            }
            var totalBytes = 0L
            target.buffered().use { output ->
                media.segments.forEachIndexed { index, segment ->
                    if (isStopped) throw CancellationException("HLS загрузка отменена")
                    totalBytes += writeSegment(segment, extension, referer, output,
                        MAX_TOTAL_BYTES - totalBytes)
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw IOException("HLS превышает лимит 3 ГБ")
                    }
                    val percent = (100L * (index + 1) / media.segments.size).toInt()
                    setProgress(workDataOf("progress" to percent,
                        "segments" to media.segments.size))
                    if ((index + 1) % 10 == 0 || index + 1 == media.segments.size) {
                        setForeground(foreground(title,
                            "HLS: ${index + 1}/${media.segments.size} сегментов", percent))
                    }
                }
            }
            if (totalBytes == 0L) throw IOException("HLS скачан без данных")
            if (storedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                applicationContext.contentResolver.update(storedUri!!, values, null, null)
            }
            completed = true
            AppDiagnostics.info("HLS", "VOD downloaded segments=${media.segments.size} bytes=$totalBytes")
            Result.success(workDataOf("bytes" to totalBytes, "file" to name))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppDiagnostics.failure("HLS", "download VOD", error)
            fail(error.message ?: "Ошибка загрузки HLS")
        } finally {
            if (!completed) {
                storedUri?.let { runCatching { applicationContext.contentResolver.delete(it, null, null) } }
                legacyFile?.let { runCatching { it.delete() } }
            }
        }
    }

    private fun fail(message: String): Result =
        Result.failure(workDataOf("error" to message.take(200)))

    private fun foreground(title: String, message: String, progress: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "HLS загрузки MusicFlow",
                NotificationManager.IMPORTANCE_LOW))
        }
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.take(65))
            .setContentText(message)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
        val id = 3000 + (this.id.hashCode() and 0x3fff)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, notification)
    }

    private fun readManifest(url: String, referer: String?): String {
        val connection = connect(url, referer)
        return try {
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (bytes.size() + n > MAX_PLAYLIST_BYTES) {
                        throw IOException("HLS-плейлист превышает 1 МБ")
                    }
                    bytes.write(buffer, 0, n)
                }
            }
            bytes.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun writeSegment(
        url: String,
        format: String,
        referer: String?,
        output: OutputStream,
        budget: Long
    ): Long {
        val connection = connect(url, referer)
        return try {
            if (connection.contentLengthLong > MAX_SEGMENT_BYTES ||
                connection.contentLengthLong > budget) {
                throw IOException("Сегмент HLS слишком большой")
            }
            connection.inputStream.buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var first = true
                while (true) {
                    if (isStopped) throw CancellationException("Загрузка HLS отменена")
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (first) {
                        first = false
                        if (format == "ts" && (n == 0 || buffer[0] != 0x47.toByte())) {
                            throw IOException("Ответ не содержит MPEG-TS сегмент")
                        }
                    }
                    total += n
                    if (total > MAX_SEGMENT_BYTES || total > budget) {
                        throw IOException("Превышен лимит HLS")
                    }
                    output.write(buffer, 0, n)
                }
                if (total == 0L) throw IOException("Получен пустой HLS сегмент")
                total
            }
        } finally {
            connection.disconnect()
        }
    }

    /** No downgrade from HTTPS and no authenticated/presigned non-HTTPS redirects. */
    private fun connect(url: String, referer: String?): HttpsURLConnection {
        var current = url
        repeat(4) {
            if (!MediaDownloadPolicy.isSafeHttps(current)) {
                throw IOException("HLS-ссылка должна использовать HTTPS")
            }
            val connection = URL(current).openConnection() as? HttpsURLConnection
                ?: throw IOException("Не удалось открыть защищённое соединение HTTPS")
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "MusicFlow-TEST-Android")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (referer != null) connection.setRequestProperty("Referer", referer)
            val status = connection.responseCode
            if (status in 200..299) return connection
            if (status in 300..399) {
                val to = connection.getHeaderField("Location")
                connection.disconnect()
                if (to.isNullOrBlank()) throw IOException("Пустой адрес перенаправления HLS")
                current = URI(current).resolve(to).toASCIIString()
                return@repeat
            }
            connection.disconnect()
            throw IOException("Источник HLS вернул HTTP $status")
        }
        throw IOException("Слишком много перенаправлений HLS")
    }
}
