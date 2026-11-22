package com.musicflow.app

/** Labels only tracks declared by the playing media. Never invent dubbing providers. */
internal object CinemaAudioTrackLabels {
    fun display(label: String?, language: String?, trackNumber: Int): String {
        val published = label.orEmpty().trim().replace(Regex("\\s+"), " ").take(60)
        if (published.isNotEmpty()) return published
        val code = language.orEmpty().lowercase().substringBefore('-')
        return when (code) {
            "ru", "rus" -> "Русский"
            "en", "eng" -> "English"
            "uk", "ukr" -> "Українська"
            "de", "deu", "ger" -> "Deutsch"
            "fr", "fra", "fre" -> "Français"
            "es", "spa" -> "Español"
            else -> "Дорожка ${trackNumber.coerceAtLeast(1)}"
        }
    }
}
