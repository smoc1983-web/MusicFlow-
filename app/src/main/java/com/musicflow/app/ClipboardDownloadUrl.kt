package com.musicflow.app

/** The clipboard is read only after the user explicitly taps Paste. */
internal object ClipboardDownloadUrl {
    private val urlPattern = Regex("""https://[^\s<>"'«»]+""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? =
        urlPattern.find(text)?.value
            ?.trimEnd('.', ',', ';', '!', '?', ':', ')', ']', '}')
            ?.takeIf { OfflineDownloadRoute.classify(it) != OfflineTransfer.UNSUPPORTED }
}
