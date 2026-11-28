package com.musicflow.app

import java.net.URI

/** Explicit non-secret headers for ordinary HTTPS media files and HLS preview. */
internal object DownloadHttpHeaders {
    fun validated(referer: String, userAgent: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val ref = referer.trim()
        if (ref.isNotEmpty()) {
            require(ref.length <= 2048 && ref.none { it.isISOControl() }) {
                "Некорректный Referer"
            }
            val uri = runCatching { URI(ref) }.getOrNull()
            require(uri != null && uri.scheme.equals("https", true) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
                "Referer должен быть HTTPS-ссылкой"
            }
            result["Referer"] = ref
        }
        val agent = userAgent.trim()
        if (agent.isNotEmpty()) {
            require(agent.length <= 200 && agent.none { it.isISOControl() }) {
                "Некорректный User-Agent"
            }
            result["User-Agent"] = agent
        }
        return result
    }
}
