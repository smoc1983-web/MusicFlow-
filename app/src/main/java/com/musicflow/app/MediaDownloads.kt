package com.musicflow.app

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * OS-managed downloads continue with the app in background or screen locked.
 * Only explicit single-file HTTPS URLs accepted by MediaDownloadPolicy can enter
 * the queue. We do not extract or decrypt protected streams or HLS/DASH media.
 */
internal object MediaDownloads {
    private const val FILE_IDS_PREF = "musicflow_download_ids_v1"
    private const val FILE_IDS_KEY = "ids"

    fun isOwnFile(context: Context, id: Long, description: String): Boolean =
        description.startsWith("MusicFlow •") ||
            context.getSharedPreferences(FILE_IDS_PREF, Context.MODE_PRIVATE)
                .getStringSet(FILE_IDS_KEY, emptySet())?.contains(id.toString()) == true

    private fun recordFile(context: Context, id: Long) {
        val pref = context.getSharedPreferences(FILE_IDS_PREF, Context.MODE_PRIVATE)
        pref.edit().putStringSet(FILE_IDS_KEY,
            pref.getStringSet(FILE_IDS_KEY, emptySet()).orEmpty().toMutableSet()
                .apply { add(id.toString()) }).apply()
    }

    private fun forgetFile(context: Context, id: Long) {
        val pref = context.getSharedPreferences(FILE_IDS_PREF, Context.MODE_PRIVATE)
        pref.edit().putStringSet(FILE_IDS_KEY,
            pref.getStringSet(FILE_IDS_KEY, emptySet()).orEmpty().toMutableSet()
                .apply { remove(id.toString()) }).apply()
    }

    fun cancel(context: Context, id: Long): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val removed = manager.remove(id) > 0
        if (removed) forgetFile(context, id)
        return removed
    }

    fun enqueue(
        context: Context,
        url: String,
        title: String,
        kind: DownloadMediaKind,
        referer: String? = null,
        userAgent: String? = null,
        hlsVariantIndex: Int? = null,
        dashStreamKeys: List<androidx.media3.common.StreamKey>? = null
    ): DownloadPlanResult {
        val route = OfflineDownloadRoute.classify(url)
        if (route == OfflineTransfer.ADAPTIVE_HLS ||
            route == OfflineTransfer.ADAPTIVE_DASH) {
            return try {
                OfflineDownloads.enqueue(context, url, title, kind, hlsVariantIndex, dashStreamKeys)
                DownloadPlanResult.Accepted(MediaDownloadPlan(
                    url, "$title (офлайн Media3)", "application/octet-stream",
                    "Media3 • внутренняя библиотека"
                ))
            } catch (error: Exception) {
                AppDiagnostics.failure("Downloads", "Media3 offline queue", error)
                DownloadPlanResult.Rejected("Не удалось добавить поток в офлайн-загрузки Media3")
            }
        }
        val decision = MediaDownloadPolicy.plan(url, title, kind)
        if (decision !is DownloadPlanResult.Accepted) return decision

        // Android 8/9 required storage permission for public Downloads.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return DownloadPlanResult.Rejected(
                "Разрешите MusicFlow доступ к файлам в настройках Android и повторите загрузку"
            )
        }
        val plan = decision.value
        val headers = try {
            DownloadHttpHeaders.validated(referer.orEmpty(), userAgent.orEmpty())
        } catch (error: IllegalArgumentException) {
            return DownloadPlanResult.Rejected(error.message ?: "Некорректные заголовки")
        }
        return try {
            val filename = plan.fileName.substringBeforeLast('.') + "-" +
                System.currentTimeMillis().toString() + "." + plan.fileName.substringAfterLast('.')
            val request = DownloadManager.Request(Uri.parse(plan.url))
                .setTitle(title.take(100).ifBlank { plan.fileName })
                .setDescription("MusicFlow • файл будет доступен в Загрузках")
                .setMimeType(plan.mimeType)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    plan.relativeDirectory + "/" + filename
                )
            headers.forEach { (name, value) ->
                request.addRequestHeader(name, value)
            }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            recordFile(context, id)
            AppDiagnostics.info("Downloads", "queued kind=$kind id=$id")
            DownloadPlanResult.Accepted(plan)
        } catch (error: Exception) {
            AppDiagnostics.failure("Downloads", "enqueue", error)
            DownloadPlanResult.Rejected(
                "Не удалось поставить файл в очередь загрузки. Проверьте свободное место и подключение"
            )
        }
    }

    fun showResult(context: Context, outcome: DownloadPlanResult) {
        val message = when (outcome) {
            is DownloadPlanResult.Accepted ->
                "Добавлено в загрузки • откройте вкладку «Загрузки»"
            is DownloadPlanResult.Rejected -> outcome.message
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun openDownloads(context: Context) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            AppDiagnostics.failure("Downloads", "open system Downloads", error)
            Toast.makeText(context, "Откройте приложение «Файлы» → «Загрузки»", Toast.LENGTH_LONG).show()
        }
    }

    fun firstDownloadable(
        urls: List<String>, title: String, kind: DownloadMediaKind
    ): DownloadPlanResult {
        for (url in urls.distinct()) {
            val transfer = OfflineDownloadRoute.classify(url)
            if (transfer == OfflineTransfer.ADAPTIVE_HLS ||
                transfer == OfflineTransfer.ADAPTIVE_DASH) {
                return DownloadPlanResult.Accepted(MediaDownloadPlan(
                    url, title, "application/octet-stream",
                    "Media3 • внутренняя библиотека"
                ))
            }
            val choice = MediaDownloadPolicy.plan(url, title, kind)
            if (choice is DownloadPlanResult.Accepted) return choice
        }
        return DownloadPlanResult.Rejected(
            "У текущего источника нет прямого разрешённого файла для скачивания. " +
                "Используйте «Загрузить по ссылке» с прямым адресом файла"
        )
    }
}
