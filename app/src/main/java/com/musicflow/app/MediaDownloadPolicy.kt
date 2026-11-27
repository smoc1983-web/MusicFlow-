package com.musicflow.app

import java.net.URI
import java.util.Locale

/**
 * An explicit, direct HTTPS media file can be saved by Android DownloadManager.
 * Streaming manifests (HLS/DASH), DRM, web pages and source-specific protocols
 * are deliberately NOT represented as downloadable movie/audio files.
 */
internal enum class DownloadMediaKind { MUSIC, VIDEO }

internal data class MediaDownloadPlan(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val relativeDirectory: String
)

internal sealed interface DownloadPlanResult {
    data class Accepted(val value: MediaDownloadPlan) : DownloadPlanResult
    data class Rejected(val message: String) : DownloadPlanResult
}

internal object MediaDownloadPolicy {
    private val audioTypes = mapOf(
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
        "aac" to "audio/aac", "flac" to "audio/flac",
        "ogg" to "audio/ogg", "opus" to "audio/ogg",
        "wav" to "audio/wav"
    )
    private val movieTypes = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/x-m4v",
        "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime"
    )

    fun plan(url: String, title: String, kind: DownloadMediaKind): DownloadPlanResult {
        val cleanUrl = url.trim()
        val uri = try {
            URI(cleanUrl)
        } catch (_: Exception) {
            return DownloadPlanResult.Rejected("Некорректная ссылка на медиафайл")
        }
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
            return DownloadPlanResult.Rejected("Для скачивания нужна прямая HTTPS-ссылка на файл")
        }

        val extension = uri.path.orEmpty().substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        if (extension in setOf("m3u8", "mpd")) {
            return DownloadPlanResult.Rejected(
                "Поток HLS/DASH нельзя сохранить как единый файл. Нужна разрешённая прямая ссылка MP4 или аудиофайл"
            )
        }
        val formats = if (kind == DownloadMediaKind.MUSIC) audioTypes else movieTypes
        val mimeType = formats[extension] ?: return DownloadPlanResult.Rejected(
            if (kind == DownloadMediaKind.MUSIC)
                "Для этой записи нет прямой ссылки MP3, M4A, FLAC, OGG, OPUS, AAC или WAV"
            else "Для этого фильма нужна прямая ссылка MP4, MKV, WEBM или MOV"
        )

        val safeTitle = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '.', '_')
            .take(90)
            .ifBlank { if (kind == DownloadMediaKind.MUSIC) "MusicFlow-Audio" else "MusicFlow-Video" }
        return DownloadPlanResult.Accepted(MediaDownloadPlan(
            url = cleanUrl,
            fileName = "$safeTitle.$extension",
            mimeType = mimeType,
            relativeDirectory = if (kind == DownloadMediaKind.MUSIC)
                "MusicFlow/Music" else "MusicFlow/Movies"
        ))
    }
}
