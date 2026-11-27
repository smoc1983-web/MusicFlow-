package com.musicflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

/** Single, non-evicting Media3 cache, shared between downloading and offline playback. */
@OptIn(UnstableApi::class)
internal object OfflineDownloads {
    private lateinit var cache: SimpleCache
    private lateinit var downloadManager: DownloadManager

    @Synchronized
    private fun initialize(context: Context) {
        if (::downloadManager.isInitialized) return
        val app = context.applicationContext
        val database = StandaloneDatabaseProvider(app)
        cache = SimpleCache(File(app.filesDir, "musicflow_offline_v1"),
            NoOpCacheEvictor(), database)
        downloadManager = DownloadManager(
            app, database, cache, DefaultHttpDataSource.Factory(),
            Executor { runnable -> runnable.run() }
        )
        downloadManager.maxParallelDownloads = 2
    }

    @Synchronized fun manager(context: Context): DownloadManager {
        initialize(context)
        return downloadManager
    }

    @Synchronized fun cache(context: Context): SimpleCache {
        initialize(context)
        return cache
    }

    fun enqueue(context: Context, url: String, title: String,
                kind: DownloadMediaKind): Boolean {
        val route = OfflineDownloadRoute.classify(url)
        if (route != OfflineTransfer.ADAPTIVE_HLS &&
            route != OfflineTransfer.ADAPTIVE_DASH) return false
        // DownloadRequest carries the original URI; Media3 stores segments
        // in a private SimpleCache and DownloadIndex, NOT a shareable .mp4.
        val mime = if (route == OfflineTransfer.ADAPTIVE_HLS)
            MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MPD
        val request = DownloadRequest.Builder(UUID.randomUUID().toString(), Uri.parse(url))
            .setMimeType(mime)
            .setData(("${kind.name}\n" + title.take(130)).toByteArray(Charsets.UTF_8))
            .build()
        manager(context)
        DownloadService.sendAddDownload(context,
            MusicFlowOfflineDownloadService::class.java, request, false)
        return true
    }

    fun snapshot(context: Context): List<Download> {
        val cursor = manager(context).downloadIndex.getDownloads()
        val result = mutableListOf<Download>()
        try {
            while (cursor.moveToNext()) result.add(cursor.download)
        } finally {
            cursor.close()
        }
        return result.sortedByDescending { it.startTimeMs }
    }

    fun pause(context: Context, id: String) {
        manager(context)
        DownloadService.sendSetStopReason(context,
            MusicFlowOfflineDownloadService::class.java,
            id, DownloadActionPolicy.USER_PAUSED_STOP_REASON, false)
    }

    fun resume(context: Context, id: String) {
        manager(context)
        DownloadService.sendSetStopReason(context,
            MusicFlowOfflineDownloadService::class.java,
            id, DownloadActionPolicy.RESUME_STOP_REASON, false)
        // If a previous global pause was left over, allow that queued item to run.
        DownloadService.sendResumeDownloads(context,
            MusicFlowOfflineDownloadService::class.java, false)
    }

    fun retry(context: Context, failed: Download) {
        manager(context)
        DownloadService.sendAddDownload(context,
            MusicFlowOfflineDownloadService::class.java,
            failed.request, false)
    }

    fun remove(context: Context, id: String) {
        DownloadService.sendRemoveDownload(context,
            MusicFlowOfflineDownloadService::class.java, id, false)
    }

    fun pauseAll(context: Context) {
        DownloadService.sendPauseDownloads(context,
            MusicFlowOfflineDownloadService::class.java, false)
    }

    fun resumeAll(context: Context) {
        DownloadService.sendResumeDownloads(context,
            MusicFlowOfflineDownloadService::class.java, false)
    }
}

/** Foreground worker for Media3 offline streaming; separate from media playback. */
@OptIn(UnstableApi::class)
internal class MusicFlowOfflineDownloadService : DownloadService(3199) {
    override fun getDownloadManager(): DownloadManager = OfflineDownloads.manager(this)
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>, notMetRequirements: Int
    ): Notification {
        val channelId = "musicflow_offline_downloads"
        val notifications =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(
            channelId, "Загрузки MusicFlow", NotificationManager.IMPORTANCE_LOW
        ))
        val completed = downloads.count { it.state == Download.STATE_COMPLETED }
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING }
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MusicFlow • офлайн-загрузки")
            .setContentText("Загружается: $active; готово: $completed")
            .setOngoing(true)
            .build()
    }
}
