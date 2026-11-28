package com.musicflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

internal data class BackgroundExportState(
    val active: Boolean = false, val title: String = "",
    val progress: OfflineMp4ExportProgress? = null, val message: String? = null
)

/** Export lifetime belongs to a foreground service, not the screen. */
@OptIn(UnstableApi::class)
internal class MusicFlowMp4ExportService : Service() {
    companion object {
        private const val CHANNEL = "musicflow_mp4_export"
        private const val ID = 3201
        private const val CANCEL = "com.musicflow.app.CANCEL_EXPORT"
        private val mutable = MutableStateFlow(BackgroundExportState())
        val state = mutable.asStateFlow()

        fun start(context: Context, id: String, title: String) {
            check(!mutable.value.active) { "Экспорт уже выполняется" }
            mutable.value = BackgroundExportState(true, title)
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, MusicFlowMp4ExportService::class.java)
                        .putExtra("id", id).putExtra("title", title))
            } catch (error: Exception) {
                mutable.value = BackgroundExportState(message = "Не удалось запустить экспорт")
                throw error
            }
        }
        fun cancel(context: Context) {
            if (mutable.value.active) context.startService(
                Intent(context, MusicFlowMp4ExportService::class.java).setAction(CANCEL))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running = false
    private var destroyed = false
    private var wake: PowerManager.WakeLock? = null
    private var lastNotice = 0L

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            if (!OfflineMp4Exporter.cancel()) finish("Экспорт отменён")
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        val id = intent?.getStringExtra("id")
        if (id == null) { finish("Экспорт прерван"); return START_NOT_STICKY }
        val title = intent.getStringExtra("title").orEmpty()
        mutable.value = BackgroundExportState(true, title)
        try {
            val notice = notification()
            if (Build.VERSION.SDK_INT >= 29) {
                val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                startForeground(ID, notice, type)
            } else startForeground(ID, notice)
            running = true
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicFlow:Mp4Export").apply {
                    setReferenceCounted(false)
                    acquire(6L * 60 * 60 * 1000)
                }
            val index = OfflineDownloads.manager(this).downloadIndex
            scope.launch {
                try {
                    val download = withContext(Dispatchers.IO) { index.getDownload(id) }
                        ?: error("Загрузка больше недоступна")
                    require(download.state == Download.STATE_COMPLETED)
                    if (!running) return@launch
                    OfflineMp4Exporter.start(applicationContext, download, onProgress = { progress ->
                        if (running && !destroyed) {
                            mutable.value = BackgroundExportState(true, title, progress)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastNotice >= 900L) {
                                lastNotice = now
                                runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
                            }
                        }
                    }) { _, message -> if (!destroyed) finish(message) }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    AppDiagnostics.failure("Downloads", "foreground MP4 export", error)
                    finish("Экспорт не запущен: " + error.message.orEmpty())
                }
            }
        } catch (error: Exception) {
            AppDiagnostics.failure("Downloads", "start foreground MP4", error)
            finish("Экспорт не запущен: " + error.message.orEmpty())
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Экспорт MP4", NotificationManager.IMPORTANCE_LOW))
        val state = mutable.value
        val phase = when (state.progress?.phase) {
            OfflineMp4ExportPhase.TRANSFORMING -> "Создание MP4"
            OfflineMp4ExportPhase.SAVING -> "Сохранение MP4"
            null -> "Подготовка экспорта"
        }
        val stop = PendingIntent.getService(this, ID, Intent(this, MusicFlowMp4ExportService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, ID, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val percent = state.progress?.percent
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MusicFlow • " + phase)
            .setContentText(state.title + (percent?.let { " · $it%" } ?: ""))
            .setProgress(100, percent ?: 0, percent == null)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,
                android.R.drawable.ic_menu_close_clear_cancel), "Отменить", stop).build())
            .setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun finish(message: String) {
        running = false
        mutable.value = BackgroundExportState(message = message)
        releaseWake()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(ID + 10,
                Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("MusicFlow • экспорт MP4").setContentText(message)
                    .setAutoCancel(true).build())
        }
    }

    private fun releaseWake() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (running) OfflineMp4Exporter.cancel()
        finish("Экспорт остановлен: системный лимит фоновой службы")
    }

    override fun onDestroy() {
        destroyed = true
        scope.cancel()
        if (running) {
            OfflineMp4Exporter.cancel()
            mutable.value = BackgroundExportState(message = "Экспорт прерван")
        }
        running = false
        releaseWake()
        super.onDestroy()
    }
}
