package com.musicflow.app

/** A download can only offer qualities advertised by the actual manifest. */
internal object CinemaDownloadQualityPolicy {
    fun hlsIndex(variants: List<HlsQuality>, selected: Int?): Int? {
        if (selected == null) return null
        return variants.firstOrNull { it.index == selected && !it.externalAudio }?.index
    }

    fun notice(transfer: OfflineTransfer): String = when (transfer) {
        OfflineTransfer.ADAPTIVE_HLS -> "HLS: доступные разрешения берутся из M3U8; отдельные аудиодорожки остаются в режиме «Авто»."
        OfflineTransfer.ADAPTIVE_DASH -> "DASH: качество берётся из MPD; Media3 сохранит совместимую аудиодорожку."
        OfflineTransfer.DIRECT_FILE -> "Оригинальное качество файла"
        OfflineTransfer.UNSUPPORTED -> "Источник не предоставляет поддерживаемый файл"
    }
}
