package com.musicflow.app

import java.util.Locale

/**
 * A size prediction is never presented as the exact number of downloaded bytes.
 * Adaptive bitrate, container overhead, extra tracks and network changes affect it.
 */
internal object DownloadSizeEstimate {
    private const val MAX_BYTES = 8L * 1024 * 1024 * 1024 * 1024

    fun bytes(durationSeconds: Double?, bitsPerSecond: Long?): Long? {
        if (durationSeconds == null || !durationSeconds.isFinite() ||
            durationSeconds <= 0.0 || durationSeconds > 604_800.0 ||
            bitsPerSecond == null || bitsPerSecond <= 0L) return null
        val prediction = durationSeconds * bitsPerSecond / 8.0
        if (!prediction.isFinite() || prediction < 1.0 || prediction > MAX_BYTES) return null
        return prediction.toLong()
    }

    fun format(bytes: Long?, approximate: Boolean = true): String {
        if (bytes == null || bytes < 0) return "размер неизвестен"
        val amount = when {
            bytes >= 1_000_000_000L ->
                String.format(Locale("ru", "RU"), "%.1f ГБ", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L ->
                String.format(Locale("ru", "RU"), "%.1f МБ", bytes / 1_000_000.0)
            else -> String.format(Locale("ru", "RU"), "%.0f КБ", bytes / 1000.0)
        }
        return (if (approximate) "≈ " else "") + amount
    }

    fun choice(quality: String, estimatedBytes: Long?): String =
        quality + " · " + format(estimatedBytes)
}
