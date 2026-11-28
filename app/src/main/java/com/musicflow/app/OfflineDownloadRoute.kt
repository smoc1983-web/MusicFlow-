package com.musicflow.app

import java.net.URI
import java.util.Locale

/** Media3 caches adaptive media internally; direct files stay shareable in Downloads. */
internal enum class OfflineTransfer { DIRECT_FILE, ADAPTIVE_HLS, ADAPTIVE_DASH, UNSUPPORTED }

internal object OfflineDownloadRoute {
    private val direct = setOf("mp4", "m4v", "mkv", "webm", "mov", "ts", "3gp",
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "avi", "mpg", "mpeg")

    fun classify(url: String): OfflineTransfer {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return OfflineTransfer.UNSUPPORTED
        if (uri.scheme?.lowercase(Locale.ROOT) != "https" ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
            return OfflineTransfer.UNSUPPORTED
        }
        return when (uri.path.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "m3u8" -> OfflineTransfer.ADAPTIVE_HLS
            "mpd" -> OfflineTransfer.ADAPTIVE_DASH
            in direct -> OfflineTransfer.DIRECT_FILE
            else -> OfflineTransfer.UNSUPPORTED
        }
    }

    fun canShareOfflineFile(type: OfflineTransfer): Boolean =
        type == OfflineTransfer.DIRECT_FILE
}
