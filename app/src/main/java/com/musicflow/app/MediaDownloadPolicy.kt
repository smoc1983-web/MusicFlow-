package com.musicflow.app

import java.net.URI
import java.util.Locale

/**
 * An explicit, direct HTTPS media file can be saved by Android DownloadManager.
 * Streaming manifests (HLS/DASH), DRM, web pages and source-specific protocols
 * are deliberately NOT represented as downloadable movie/audio files.
 */
internal enum class DownloadMediaKind { MUSIC, VIDEO }
internal enum class MediaTransferMode { DIRECT, HLS_VOD }

internal data class MediaDownloadPlan(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val relativeDirectory: String,
    val transferMode: MediaTransferMode = MediaTransferMode.DIRECT
)

internal sealed interface DownloadPlanResult {
    data class Accepted(val value: MediaDownloadPlan) : DownloadPlanResult
    data class Rejected(val message: String) : DownloadPlanResult
}

internal object MediaDownloadPolicy {
    internal fun isSafeHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    } catch (_: Exception) {
        false
    }

    private val audioTypes = mapOf(
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
        "aac" to "audio/aac", "flac" to "audio/flac",
        "ogg" to "audio/ogg", "opus" to "audio/ogg",
        "wav" to "audio/wav", "aiff" to "audio/aiff",
        "wma" to "audio/x-ms-wma"
    )
    private val movieTypes = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/x-m4v",
        "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "ts" to "video/mp2t",
        "3gp" to "video/3gpp", "avi" to "video/x-msvideo",
        "mpg" to "video/mpeg", "mpeg" to "video/mpeg"
    )

    fun plan(url: String, title: String, kind: DownloadMediaKind): DownloadPlanResult {
        val cleanUrl = url.trim()
        val uri = try {
            URI(cleanUrl)
        } catch (_: Exception) {
            return DownloadPlanResult.Rejected("Некорректная ссылка на медиафайл")
        }
        if (!isSafeHttps(cleanUrl)) {
            return DownloadPlanResult.Rejected("Для скачивания нужна прямая HTTPS-ссылка на файл")
        }

        val extension = uri.path.orEmpty().substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        if (extension == "mpd") {
            return DownloadPlanResult.Rejected(
                "MPEG-DASH (.mpd) пока не поддерживается. Найдите разрешённый MP4-файл"
            )
        }
        val formats = if (kind == DownloadMediaKind.MUSIC) audioTypes else movieTypes
        val isHls = extension == "m3u8"
        val mimeType = if (isHls) {
            if (kind == DownloadMediaKind.VIDEO) "video/mp2t" else "audio/mp2t"
        } else formats[extension] ?: return DownloadPlanResult.Rejected(
            if (kind == DownloadMediaKind.MUSIC)
                "Нет прямой ссылки MP3, M4A, FLAC, AAC, OGG или WAV"
            else "Нет прямой ссылки MP4, MKV, TS, WEBM, MOV или AVI"
        )

        val safeTitle = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '.', '_')
            .take(90)
            .ifBlank { if (kind == DownloadMediaKind.MUSIC) "MusicFlow-Audio" else "MusicFlow-Video" }
        return DownloadPlanResult.Accepted(MediaDownloadPlan(
            url = cleanUrl,
            fileName = "$safeTitle.${if (isHls) "ts" else extension}",
            mimeType = mimeType,
            relativeDirectory = if (kind == DownloadMediaKind.MUSIC)
                "MusicFlow/Music" else "MusicFlow/Movies",
            transferMode = if (isHls) MediaTransferMode.HLS_VOD else MediaTransferMode.DIRECT
        ))
    }
}
