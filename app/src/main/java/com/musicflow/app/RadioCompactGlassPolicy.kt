package com.musicflow.app

/**
 * The radio controls deliberately do not pretend a live HTTP stream is seekable.
 * Music and cinema transport behavior remains unchanged.
 */
internal object RadioCompactGlassPolicy {
    val actions: List<String> =
        listOf("play_pause", "favorite", "equalizer", "close", "collapse")

    fun isCompact(isRadio: Boolean): Boolean = isRadio

    fun status(playing: Boolean, error: String?): String = when {
        !error.isNullOrBlank() -> "Поток временно недоступен"
        playing -> "Сейчас играет"
        else -> "На паузе"
    }
}
