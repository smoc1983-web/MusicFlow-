package com.musicflow.app

import java.net.URI
import java.util.Locale

/** A quality label is only shown when it is published in a link. No fabricated bitrates. */
internal data class MusicDownloadChoice(
    val url: String,
    val source: String,
    val quality: String?,
    val format: String,
    val isAdaptive: Boolean
) {
    val label: String get() = buildString {
        append(source)
        append(" · ")
        append(format)
        append(" · ")
        append(quality ?: "качество источника")
    }
}

internal object MusicDownloadChoicePolicy {
    fun eligible(url: String, source: String): MusicDownloadChoice? {
        val route = OfflineDownloadRoute.classify(url)
        if (route == OfflineTransfer.UNSUPPORTED) return null
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        val format = parsed.path.orEmpty().substringAfterLast('.', "")
            .lowercase(Locale.ROOT).uppercase(Locale.ROOT)
        val bitrate = parsed.rawQuery?.split('&')
            ?.firstNotNullOfOrNull { item ->
                val key = item.substringBefore('=').lowercase(Locale.ROOT)
                if (key !in setOf("bitrate", "br", "kbps")) return@firstNotNullOfOrNull null
                item.substringAfter('=', "").toIntOrNull()?.takeIf { it in 32..1411 }
            }
        return MusicDownloadChoice(
            url, source.trim().ifBlank { "Источник" },
            bitrate?.let { "$it кбит/с" }, format,
            route == OfflineTransfer.ADAPTIVE_HLS || route == OfflineTransfer.ADAPTIVE_DASH
        )
    }

    fun choices(streams: List<MusicStream>): List<MusicDownloadChoice> =
        streams.mapNotNull { entry ->
            val label = when (entry.source) {
                "zaycev" -> "Zaycev"
                "muzofond" -> "Muzofond"
                "mailru" -> "Mail.ru"
                else -> entry.source
            }
            eligible(entry.uri, label)
        }.distinctBy { it.url }.take(12)
}
