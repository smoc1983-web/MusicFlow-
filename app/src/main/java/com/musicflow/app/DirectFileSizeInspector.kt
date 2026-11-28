package com.musicflow.app

import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** HEAD only: never download media while calculating its declared file size. */
internal object DirectFileSizeInspector {
    fun contentLength(url: String, headers: Map<String, String> = emptyMap()): Long? {
        require(OfflineDownloadRoute.classify(url) == OfflineTransfer.DIRECT_FILE) {
            "Ожидается прямая HTTPS-ссылка на файл"
        }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 6_000
            readTimeout = 6_000
            instanceFollowRedirects = false
            setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.contentLengthLong.takeIf { it > 0L }
        } finally {
            connection.disconnect()
        }
    }
}
