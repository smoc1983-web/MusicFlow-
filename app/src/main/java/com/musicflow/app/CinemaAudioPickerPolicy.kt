package com.musicflow.app

/** Real available media track count; a dubbing option is not invented. */
internal object CinemaAudioPickerPolicy {
    fun canOpen(trackCount: Int) = true
    fun hasAlternates(trackCount: Int): Boolean = trackCount > 1
    fun status(trackCount: Int): String = when {
        trackCount <= 0 -> "Видеопоток пока не сообщил аудиодорожки"
        trackCount == 1 ->
            "Источник предоставляет только одну аудиодорожку. Другую озвучку выбрать нельзя."
        else -> "Доступно $trackCount аудиодорожки"
    }
}
