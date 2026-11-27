package com.musicflow.app

/** Show a dubbing selector only when Media3 exposes multiple supported real audio options. */
internal object CinemaAudioPickerPolicy {
    fun canOpen(trackCount: Int): Boolean = trackCount > 1
    fun hasAlternates(trackCount: Int): Boolean = trackCount > 1
    fun status(trackCount: Int): String = when {
        trackCount <= 0 -> "Видеопоток пока не сообщил аудиодорожки"
        trackCount == 1 ->
            "Источник предоставляет только одну аудиодорожку. Другую озвучку выбрать нельзя."
        else -> "Доступно $trackCount аудиодорожки"
    }
}
