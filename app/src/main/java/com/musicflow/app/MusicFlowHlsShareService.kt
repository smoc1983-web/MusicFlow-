package com.musicflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** No upstream network source: every requested byte must already exist in SimpleCache. */
@OptIn(UnstableApi::class)
internal class Media3HlsCache(private val cache: Cache) : OfflineHlsCache {
    override fun baseUri(uri: String): String =
        ContentMetadata.getRedirectedUri(cache.getContentMetadata(uri))?.toString() ?: uri
    override fun size(uri: String): Long {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(uri))
        return if (length > 0 && cache.isCached(uri, 0, length)) length else -1
    }
    override fun read(uri: String, offset: Long, length: Long, output: OutputStream) {
        require(offset >= 0 && length > 0 && cache.isCached(uri, offset, length)) {
            "Сегмент удалён или не полностью скачан"
        }
        val source = CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY)
            .setCacheWriteDataSinkFactory(null).createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(Uri.parse(uri)).setPosition(offset).setLength(length).build())
            val bytes = ByteArray(64 * 1024)
            var left = length
            while (left > 0) {
                val count = source.read(bytes, 0, minOf(left, bytes.size.toLong()).toInt())
                check(count > 0) { "Неполный кеш HLS" }
                output.write(bytes, 0, count)
                left -= count
            }
        } finally { source.close() }
    }
}

internal data class ExternalHlsState(val active: Boolean = false, val title: String = "",
    val url: String? = null, val message: String? = null, val openRequested: Boolean = false)

/** User-started, temporary loopback-only access to the selected completed download. */
@OptIn(UnstableApi::class)
internal class MusicFlowHlsShareService : Service() {
    companion object {
        private const val CHANNEL = "musicflow_hls_share"
        private const val ID = 3202
        private const val STOP = "com.musicflow.app.STOP_HLS_SHARE"
        private val mutable = MutableStateFlow(ExternalHlsState())
        val state = mutable.asStateFlow()

        fun start(context: Context, id: String, title: String) {
            check(!mutable.value.active) { "Сначала закройте текущий HLS-поток" }
            mutable.value = ExternalHlsState(true, title)
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, MusicFlowHlsShareService::class.java)
                        .putExtra("id", id).putExtra("title", title))
            } catch (error: Exception) {
                mutable.value = ExternalHlsState(message = "Не удалось открыть локальный HLS")
                throw error
            }
        }
        fun takeOpenUrl(): String? {
            val value = mutable.value
            if (!value.openRequested) return null
            mutable.value = value.copy(openRequested = false)
            return value.url
        }
        fun stop(context: Context) {
            if (mutable.value.active) context.startService(
                Intent(context, MusicFlowHlsShareService::class.java).setAction(STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bridge: OfflineHlsBridge? = null
    private var wake: PowerManager.WakeLock? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish("Локальный поток закрыт"); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val id = intent?.getStringExtra("id")
        if (id == null) { finish("Нет скачанного HLS"); return START_NOT_STICKY }
        val title = intent.getStringExtra("title").orEmpty()
        try {
            val notice = notification(title, "Подготовка локального HLS")
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, notice)
            running = true
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicFlow:HlsShare").apply {
                    setReferenceCounted(false)
                    acquire(6L * 60 * 60 * 1000)
                }
            val index = OfflineDownloads.manager(this).downloadIndex
            val backend = Media3HlsCache(OfflineDownloads.cache(this))
            // The preparation worker closes its result if the service has already stopped.
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            Thread {
                try {
                    val download = index.getDownload(id) ?: error("Загрузка больше недоступна")
                    require(download.state == Download.STATE_COMPLETED && download.request.keySetId == null &&
                        OfflineDownloadRoute.classify(download.request.uri.toString()) == OfflineTransfer.ADAPTIVE_HLS) {
                        "Доступен только завершённый HLS без DRM"
                    }
                    val variants = download.request.streamKeys.filter {
                        it.groupIndex == HlsMultivariantPlaylist.GROUP_INDEX_VARIANT
                    }.map { it.streamIndex }
                    val opened = OfflineHlsBridge.open(download.request.uri.toString(), variants, backend)
                    handler.post {
                        if (!running) opened.close()
                        else {
                            bridge = opened
                            mutable.value = ExternalHlsState(true, title, opened.url, openRequested = true)
                            runCatching { getSystemService(NotificationManager::class.java).notify(ID,
                                notification(title, "Офлайн HLS · без перекодирования")) }
                            scope.launch {
                                while (running) {
                                    delay(30000L)
                                    if (System.nanoTime() - opened.lastAccessNanos > TimeUnit.MINUTES.toNanos(15)) {
                                        finish("Локальный поток закрыт"); break
                                    }
                                }
                            }
                        }
                    }
                } catch (error: Exception) {
                    handler.post { if (running) finish("Не удалось открыть HLS: " + error.message.orEmpty()) }
                }
            }.start()
        } catch (error: Exception) {
            finish("Не удалось открыть HLS: " + error.message.orEmpty())
        }
        return START_NOT_STICKY
    }

    private fun notification(title: String, text: String): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Локальный HLS", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, ID, Intent(this, MusicFlowHlsShareService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MusicFlow • " + title).setContentText(text)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,
                android.R.drawable.ic_menu_close_clear_cancel), "Закрыть поток", stop).build())
            .setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun release() {
        running = false
        bridge?.close(); bridge = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }
    private fun finish(message: String) {
        release()
        mutable.value = ExternalHlsState(message = message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { finish("Системный лимит фоновой службы") }
    override fun onDestroy() {
        scope.cancel()
        val wasRunning = running
        release()
        if (wasRunning) mutable.value = ExternalHlsState(message = "Локальный поток закрыт")
        super.onDestroy()
    }
}
