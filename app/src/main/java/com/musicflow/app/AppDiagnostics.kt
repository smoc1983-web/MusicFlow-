package com.musicflow.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object AppDiagnostics {
    private val lock = Any()
    private const val MAX_SIZE = 512_000L
    private var logFile: File? = null
    private var crashHandlerRegistered = false
    private val urlRegex = Regex("(?i)https?://[^\\s<>\"']+")
    private val secretRegex = Regex("(?i)(token|api_key|apikey|password|secret|authorization|sessionid)\\s*[:=]\\s*[^\\s,;]+")

    fun initialize(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                val folder = File(context.applicationContext.filesDir, "logs")
                folder.mkdirs()
                logFile = File(folder, "MusicFlow_diagnostics.log")
            }
            if (!crashHandlerRegistered) {
                crashHandlerRegistered = true
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                    failure("Crash", "uncaught thread=" + thread.name.take(30), error)
                    previous?.uncaughtException(thread, error)
                }
            }
        }
    }

    private fun sanitize(value: String): String =
        value.replace(urlRegex, "[URL]")
            .replace(secretRegex, "$1=[REDACTED]")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(400)

    fun info(area: String, message: String) { append("INFO", area, message) }
    fun warning(area: String, message: String) { append("WARN", area, message) }
    fun failure(area: String, operation: String, error: Throwable) {
        append("ERROR", area, operation + ": " +
            error.javaClass.simpleName + ": " + error.message.orEmpty())
    }

    private fun append(level: String, area: String, message: String) {
        synchronized(lock) {
            val file = logFile ?: return
            runCatching {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
                    .format(Date())
                file.appendText(time + " [" + level + "] [" + sanitize(area) +
                    "] " + sanitize(message) + "\n", Charsets.UTF_8)
                if (file.length() > MAX_SIZE) {
                    val tail = file.readText(Charsets.UTF_8).takeLast(250_000)
                    val firstLine = tail.indexOf('\n')
                    file.writeText("--- Older records truncated ---\n" +
                        if (firstLine >= 0) tail.substring(firstLine + 1) else tail,
                        Charsets.UTF_8)
                }
            }
        }
    }

    fun recentLines(): String = synchronized(lock) {
        runCatching {
            logFile?.readLines(Charsets.UTF_8)
                ?.takeLast(150)?.joinToString("\n").orEmpty()
        }.getOrDefault("Не удалось прочитать журнал")
    }

    fun clear() {
        synchronized(lock) { runCatching { logFile?.writeText("", Charsets.UTF_8) } }
        info("Diagnostics", "Log cleared by user")
    }

    fun share(context: Context): String? {
        return runCatching {
            synchronized(lock) {
                val original = logFile ?: error("Журнал ещё не создан")
                val snapshot = File(original.parentFile, "MusicFlow_diagnostics_export.txt")
                snapshot.writeText(original.readText(Charsets.UTF_8), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".diagnostics", snapshot
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Отправить лог-файл"))
            }
            null
        }.getOrElse {
            failure("Diagnostics", "share log", it)
            "Не удалось отправить файл: " + it.javaClass.simpleName
        }
    }
}
