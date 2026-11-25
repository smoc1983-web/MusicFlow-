package com.musicflow.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Android's own DownloadManager owns the network transfer. Neither the About
 * dialog's Compose scope nor the MainActivity needs to stay alive. Only a
 * verified APK is ever handed to the Android package installer.
 */
internal object MusicFlowBackgroundUpdates {
    private const val PREFS = "musicflow_background_update_v1"
    private const val ID = "download_id"
    private const val CODE = "version_code"
    private const val VERSION = "version"
    private const val BYTES = "bytes"
    private const val URL = "download_url"
    private const val REPO_ASSET_PREFIX =
        "https://github.com/smoc1983-web/MusicFlow-/releases/download/"

    internal enum class Phase { IDLE, WAITING, RUNNING, DOWNLOADED, FAILED }

    internal data class Snapshot(
        val phase: Phase,
        val progress: Int = 0,
        val version: String = "",
        val error: String? = null
    )

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun manager(context: Context): DownloadManager =
        context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: throw IOException("Системная служба загрузок Android недоступна")

    private fun file(context: Context, code: Long): File {
        val dir = context.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("Хранилище загрузок Android недоступно")
        return File(dir, "musicflow-update-$code.apk")
    }

    internal fun savedRelease(context: Context): MusicFlowUpdate? {
        val p = preferences(context)
        val code = p.getLong(CODE, 0L)
        // A completed Android installation preserves app preferences. Purge the
        // previous DownloadManager record so it cannot reopen the same version.
        if (code > 0L && code <= MusicFlowUpdates.currentCode(context)) {
            val previousId = p.getLong(ID, -1L)
            if (previousId >= 0L) runCatching { manager(context).remove(previousId) }
            forget(context)
            return null
        }
        val id = p.getLong(ID, -1L)
        val url = p.getString(URL, "").orEmpty()
        val version = p.getString(VERSION, "").orEmpty()
        val bytes = p.getLong(BYTES, 0L)
        // Older versions may have cached v0.7.50-radio-artwork-test.
        // Never show or install a cached debug APK as an official update.
        if (!MusicFlowUpdates.isOfficialSavedRelease(version, url)) {
            if (id >= 0L) runCatching { manager(context).remove(id) }
            forget(context)
            return null
        }
        if (id < 0L || code <= 0L || bytes !in 1_000_000L..(150L * 1024L * 1024L))
            return null
        return MusicFlowUpdate(version, code, url, bytes, "")
    }

    private fun forget(context: Context) {
        preferences(context).edit().clear().apply()
    }

    suspend fun enqueue(context: Context, update: MusicFlowUpdate): Long =
        withContext(Dispatchers.IO) {
            require(MusicFlowUpdates.supportsOfficialUpdates(context)) {
                "Это неофициальная сборка: обновление поверх неё недоступно"
            }
            require(MusicFlowUpdates.isOfficialSavedRelease(
                update.version, update.downloadUrl) &&
                update.versionCode > MusicFlowUpdates.currentCode(context) &&
                update.bytes in 1_000_000L..(150L * 1024L * 1024L)) {
                "Некорректные данные официального обновления"
            }
            val dm = manager(context)
            val active = savedRelease(context)
            if (active != null) {
                val state = snapshot(context)
                if (active.versionCode == update.versionCode &&
                    state.phase in setOf(Phase.WAITING, Phase.RUNNING, Phase.DOWNLOADED)) {
                    return@withContext preferences(context).getLong(ID, -1L)
                }
                val previousId = preferences(context).getLong(ID, -1L)
                if (previousId >= 0L) dm.remove(previousId)
                runCatching { file(context, active.versionCode).delete() }
                forget(context)
            }

            val target = file(context, update.versionCode)
            // The system refuses a pre-existing destination file.
            if (target.exists() && !target.delete()) {
                throw IOException("Не удалось очистить предыдущий файл обновления")
            }

            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("MusicFlow " + update.version)
                .setDescription("Обновление загружается в фоне. Установка только после проверки подписи.")
                .setMimeType("application/octet-stream")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                    target.name)
            val downloadId = dm.enqueue(request)
            preferences(context).edit()
                .putLong(ID, downloadId)
                .putLong(CODE, update.versionCode)
                .putString(VERSION, update.version)
                .putString(URL, update.downloadUrl)
                .putLong(BYTES, update.bytes)
                .apply()
            downloadId
        }

    suspend fun snapshot(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val release = savedRelease(context) ?: return@withContext Snapshot(Phase.IDLE)
        if (!MusicFlowUpdates.supportsOfficialUpdates(context) ||
            release.versionCode <= MusicFlowUpdates.currentCode(context)) {
            forget(context)
            return@withContext Snapshot(Phase.IDLE)
        }
        val id = preferences(context).getLong(ID, -1L)
        manager(context).query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext Snapshot(
                Phase.FAILED, version = release.version, error = "Загрузка удалена Android"
            )
            fun number(column: String): Long {
                val i = cursor.getColumnIndex(column)
                return if (i >= 0) cursor.getLong(i) else 0L
            }
            val status = number(DownloadManager.COLUMN_STATUS).toInt()
            val loaded = number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                .takeIf { it > 0L } ?: release.bytes
            val progress = ((loaded.coerceAtLeast(0L) * 100L) / total.coerceAtLeast(1L))
                .toInt().coerceIn(0, 99)
            when (status) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED ->
                    Snapshot(Phase.WAITING, progress, release.version)
                DownloadManager.STATUS_RUNNING ->
                    Snapshot(Phase.RUNNING, progress, release.version)
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val target = runCatching { file(context, release.versionCode) }.getOrNull()
                    if (target != null && target.isFile && target.length() == release.bytes) {
                        Snapshot(Phase.DOWNLOADED, 100, release.version)
                    } else Snapshot(Phase.FAILED, version = release.version,
                        error = "Скачанный файл неполный или отсутствует")
                }
                DownloadManager.STATUS_FAILED ->
                    Snapshot(Phase.FAILED, version = release.version,
                        error = "Сбой системной загрузки (код " +
                            number(DownloadManager.COLUMN_REASON) + "). Повторите скачивание.")
                else -> Snapshot(Phase.WAITING, progress, release.version)
            }
        } ?: Snapshot(Phase.FAILED, version = release.version,
            error = "Не удалось проверить состояние загрузки")
    }

    /**
     * Never give DownloadManager's unverified file directly to the installer.
     * The existing importer checks APK format, package, version and signing key
     * before copying it into MusicFlow's private FileProvider-backed cache.
     */
    suspend fun verifiedInstallUri(context: Context): Uri {
        val release = savedRelease(context) ?: throw IOException("Нет загруженного обновления")
        if (snapshot(context).phase != Phase.DOWNLOADED) {
            throw IOException("Обновление ещё не загружено полностью")
        }
        return MusicFlowUpdates.importVerified(
            context.applicationContext, Uri.fromFile(file(context, release.versionCode))
        )
    }
}
