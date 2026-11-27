package com.musicflow.app

/** Only read clipboard when the user explicitly taps "Вставить". */
internal object ClipboardDownloadUrl {
    private val url = Regex("""https://[^\s<>"'«»]+""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? = url.find(text)?.value
        ?.trimEnd('.', ',', ';', '!', '?', ':', ')', ']', '}')
        ?.takeIf { it.startsWith("https://", ignoreCase = true) }
}
