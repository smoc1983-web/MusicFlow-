package com.musicflow.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

internal data class MusicFlowUpdate(
    val version: String,
    val versionCode: Long,
    val downloadUrl: String,
    val bytes: Long,
    val notes: String
)

internal object MusicFlowUpdates {
    private const val API = "https://api.github.com/repos/smoc1983-web/MusicFlow-/releases?per_page=30"
    internal const val RELEASES_PAGE = "https://github.com/smoc1983-web/MusicFlow-/releases"
    internal const val SUPPORT_NUMBER = "+79128580680"
    internal const val TELEGRAM_URL = "https://t.me/+IryIQ5fkmOszZWRi"
    private const val MAX_SIZE = 150L * 1024L * 1024L

    // Never mix official and TEST feeds. Both channels use the same strict
    // package/version/certificate verification before Android installer launch.
    private const val OFFICIAL_PACKAGE = "com.musicflow.preview"
    private const val STABLE_TEST_PACKAGE = "com.musicflow.preview.dev"

    internal enum class UpdateChannel { OFFICIAL, TEST, UNSUPPORTED }

    internal fun channelForPackage(packageName: String): UpdateChannel = when (packageName) {
        OFFICIAL_PACKAGE -> UpdateChannel.OFFICIAL
        STABLE_TEST_PACKAGE -> UpdateChannel.TEST
        else -> UpdateChannel.UNSUPPORTED
    }

    fun supportsOfficialUpdates(context: Context): Boolean =
        channelForPackage(context.packageName) == UpdateChannel.OFFICIAL

    fun supportsUpdates(context: Context): Boolean =
        channelForPackage(context.packageName) != UpdateChannel.UNSUPPORTED

    fun updateChannelMessage(context: Context): String = when (channelForPackage(context.packageName)) {
        UpdateChannel.OFFICIAL ->
            "Официальный канал MusicFlow · проверка исходной подписи включена."
        UpdateChannel.TEST ->
            "Тестовый канал MusicFlow · проверка обновлений включена. " +
                "Установка возможна только поверх той же тестовой версии с тем же сертификатом. " +
                "Нельзя устанавливать официальный APK или тесты с другим ключом."
        UpdateChannel.UNSUPPORTED ->
            "Эта старая тестовая сборка (${context.packageName}) имеет отдельный пакет. " +
                "Она не совместима с новым тестовым каналом $STABLE_TEST_PACKAGE " +
                "и с официальным $OFFICIAL_PACKAGE. Для будущих обновлений " +
                "установите новую MusicFlow TEST отдельно один раз. " +
                "Не удаляйте старую сборку с важными данными без резервной копии."
    }

    fun currentCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /**
     * Uses public GitHub Releases rather than ephemeral authenticated Actions artifacts.
     * An empty release list is a valid result until the first signed APK is published.
     */
    private const val ASSET_PREFIX =
        "https://github.com/smoc1983-web/MusicFlow-/releases/download/"

    /** Production releases have a strictly numeric tag; previews cannot be installed in-place. */
    internal fun isOfficialTag(tag: String): Boolean =
        Regex("""^v\d+\.\d+\.\d+$""").matches(tag)

    internal fun isOfficialApkName(tag: String, name: String): Boolean =
        isOfficialTag(tag) && name == "MusicFlow_${tag.removePrefix("v")}.apk"

    internal fun isOfficialSavedRelease(tag: String, url: String): Boolean =
        isOfficialTag(tag) && url ==
            ASSET_PREFIX + tag + "/MusicFlow_${tag.removePrefix("v")}.apk"

    /** Versioned TEST prereleases are strictly excluded from official updates. */
    internal fun isTestTag(tag: String): Boolean =
        Regex("""^v\\d+\\.\\d+\\.\\d+-test$""").matches(tag)

    internal fun isTestApkName(tag: String, name: String): Boolean =
        isTestTag(tag) &&
            name == "MusicFlow-TEST_${tag.removePrefix("v").removeSuffix("-test")}.apk"

    internal fun isTestSavedRelease(tag: String, url: String): Boolean =
        isTestTag(tag) && url ==
            ASSET_PREFIX + tag + "/MusicFlow-TEST_${tag.removePrefix("v").removeSuffix("-test")}.apk"

    internal fun isSavedReleaseForChannel(context: Context, tag: String, url: String): Boolean =
        when (channelForPackage(context.packageName)) {
            UpdateChannel.OFFICIAL -> isOfficialSavedRelease(tag, url)
            UpdateChannel.TEST -> isTestSavedRelease(tag, url)
            UpdateChannel.UNSUPPORTED -> false
        }

    /** Side-effect-free selection used by the updater and regression tests. */
    internal fun parseOfficialReleases(response: String): MusicFlowUpdate? {
        val releases = JSONArray(response)
        val options = mutableListOf<MusicFlowUpdate>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", true) ||
                release.optBoolean("prerelease", false)) continue
            val tag = release.optString("tag_name")
            if (!isOfficialTag(tag)) continue
            val match = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")
                .matchEntire(tag) ?: continue
            val major = match.groupValues[1].toLongOrNull() ?: continue
            val minor = match.groupValues[2].toLongOrNull() ?: continue
            val patch = match.groupValues[3].toLongOrNull() ?: continue
            if (minor !in 0L..99L || patch !in 0L..99L || major > 100_000L) continue
            val code = major * 10_000L + minor * 100L + patch
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                val size = asset.optLong("size", 0L)
                val url = asset.optString("browser_download_url")
                if (!isOfficialApkName(tag, name) ||
                    size !in 1_000_000L..MAX_SIZE ||
                    !isOfficialSavedRelease(tag, url)) continue
                options += MusicFlowUpdate(tag, code, url, size,
                    release.optString("body").take(600))
            }
        }
        return options.maxByOrNull { it.versionCode }
    }


    /** Only exact, published TEST prereleases belonging to com.musicflow.preview.dev. */
    internal fun parseTestReleases(response: String): MusicFlowUpdate? {
        val releases = JSONArray(response)
        val options = mutableListOf<MusicFlowUpdate>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", true) ||
                !release.optBoolean("prerelease", false)) continue
            val tag = release.optString("tag_name")
            val match = Regex("""^v(\\d+)\\.(\\d+)\\.(\\d+)-test$""")
                .matchEntire(tag) ?: continue
            val major = match.groupValues[1].toLongOrNull() ?: continue
            val minor = match.groupValues[2].toLongOrNull() ?: continue
            val patch = match.groupValues[3].toLongOrNull() ?: continue
            if (minor !in 0L..99L || patch !in 0L..99L || major > 100_000L) continue
            val code = major * 10_000L + minor * 100L + patch
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                val size = asset.optLong("size", 0L)
                val url = asset.optString("browser_download_url")
                if (!isTestApkName(tag, name) ||
                    size !in 1_000_000L..MAX_SIZE ||
                    !isTestSavedRelease(tag, url)) continue
                options += MusicFlowUpdate(tag, code, url, size,
                    release.optString("body").take(600))
            }
        }
        return options.maxByOrNull { it.versionCode }
    }

    /** The installed package chooses the feed. Never offer another package's APK. */
    suspend fun latestRelease(context: Context): MusicFlowUpdate? = withContext(Dispatchers.IO) {
        if (!supportsUpdates(context)) return@withContext null
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MusicFlow-Android")
        }
        try {
            if (connection.responseCode != 200) {
                throw IOException("Ошибка GitHub: HTTP " + connection.responseCode)
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            when (channelForPackage(context.packageName)) {
                UpdateChannel.OFFICIAL -> parseOfficialReleases(response)
                UpdateChannel.TEST -> parseTestReleases(response)
                UpdateChannel.UNSUPPORTED -> null
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun latestRelease(): MusicFlowUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MusicFlow-Android")
        }
        try {
            if (connection.responseCode != 200) {
                throw IOException("Ошибка GitHub: HTTP " + connection.responseCode)
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseOfficialReleases(response)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadVerified(
        context: Context,
        update: MusicFlowUpdate,
        onProgress: suspend (Int) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        if (!supportsUpdates(context) ||
            !isSavedReleaseForChannel(context, update.version, update.downloadUrl)) {
            throw IOException(updateChannelMessage(context))
        }
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val temp = File(folder, "musicflow-download-tmp.apk")
        val file = File(folder, "musicflow-update.apk")
        temp.delete()
        try {
            val conn = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MusicFlow-Android")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    throw IOException("Ошибка загрузки APK: HTTP " + conn.responseCode)
                }
                val length = conn.contentLengthLong
                if (length > MAX_SIZE) throw IOException("APK слишком большой")
                var downloaded = 0L
                var previous = -1
                conn.inputStream.buffered().use { input ->
                    temp.outputStream().buffered().use { output ->
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            downloaded += n
                            if (downloaded > MAX_SIZE) throw IOException("APK превышает лимит")
                            output.write(chunk, 0, n)
                            if (length > 0) {
                                val pct = (100L * downloaded / length).toInt().coerceIn(0, 100)
                                if (pct != previous) {
                                    previous = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
                if (downloaded != update.bytes) {
                    throw IOException("Размер файла не совпадает с опубликованным выпуском")
                }
            } finally {
                conn.disconnect()
            }
            checkArchive(context, temp, update.versionCode)
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Не удалось сохранить APK")
            FileProvider.getUriForFile(
                context, context.packageName + ".diagnostics", file
            )
        } finally {
            temp.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun checkArchive(context: Context, apk: File, expectedCode: Long) {
        ZipFile(apk).use { zip ->
            if (zip.getEntry("AndroidManifest.xml") == null ||
                zip.getEntry("classes.dex") == null) {
                throw IOException("Некорректный APK")
            }
        }
        val pm = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = pm.getPackageInfo(context.packageName, flag)
        val incoming = pm.getPackageArchiveInfo(apk.absolutePath, flag)
            ?: throw IOException("Не удалось прочитать пакет APK")
        if (incoming.packageName != context.packageName) {
            throw IOException("Несовместимый идентификатор приложения")
        }
        val newCode = PackageInfoCompat.getLongVersionCode(incoming)
        if ((expectedCode > 0 && newCode != expectedCode) ||
            newCode <= PackageInfoCompat.getLongVersionCode(current)) {
            throw IOException("Загружена неподходящая версия APK")
        }
        fun signatures(info: PackageInfo): Set<String> {
            val values = if (Build.VERSION.SDK_INT >= 28) {
                val signing = info.signingInfo ?: return emptySet()
                if (signing.hasMultipleSigners()) signing.apkContentsSigners
                else signing.signingCertificateHistory
            } else info.signatures
            return values.orEmpty().map { value ->
                MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        val currentSigners = signatures(current)
        if (currentSigners.isEmpty() ||
            currentSigners.intersect(signatures(incoming)).isEmpty()) {
            throw IOException(
                "Разные ключи подписи. Обновление поверх текущей версии невозможно; " +
                "не удаляйте приложение без резервной копии данных."
            )
        }
    }

    /**
     * Allow selecting an already downloaded signed APK in MusicFlow.
     * This is useful before the first GitHub Release exists and never bypasses
     * package-name/version/signature verification.
     */
    suspend fun importVerified(context: Context, source: Uri): Uri =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val tmp = File(directory, "musicflow-import-tmp.apk")
            val target = File(directory, "musicflow-import.apk")
            tmp.delete()
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    tmp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var length = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            length += read
                            if (length > MAX_SIZE) throw IOException("APK слишком большой")
                            output.write(buffer, 0, read)
                        }
                        if (length < 1_000_000L) throw IOException("Файл APK неполный")
                    }
                } ?: throw IOException("Не удаётся открыть выбранный файл")
                checkArchive(context, tmp, 0)
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("Не удалось сохранить APK")
                FileProvider.getUriForFile(
                    context, context.packageName + ".diagnostics", target
                )
            } finally {
                tmp.delete()
            }
        }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun allowInstallIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName))

    fun installerIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            clipData = ClipData.newRawUri("MusicFlow update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
