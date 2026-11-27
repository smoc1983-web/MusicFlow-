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
    fun enqueue(
        context: Context,
        url: String,
        title: String,
        kind: DownloadMediaKind,
        referer: String? = null
    ): DownloadPlanResult {
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
            if (!referer.isNullOrBlank() &&
                referer.startsWith("https://", ignoreCase = true)) {
                request.addRequestHeader("Referer", referer)
            }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
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
                "Добавлено в загрузки • проверьте уведомления Android"
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
            val choice = MediaDownloadPolicy.plan(url, title, kind)
            if (choice is DownloadPlanResult.Accepted) return choice
        }
        return DownloadPlanResult.Rejected(
            "У текущего источника нет прямого разрешённого файла для скачивания. " +
                "Используйте «Загрузить по ссылке» с прямым адресом файла"
        )
    }
}
