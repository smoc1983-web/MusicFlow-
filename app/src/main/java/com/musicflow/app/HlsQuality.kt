package com.musicflow.app

import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Metadata from a HLS multivariant playlist; index matches Media3 HLS variant stream key. */
internal data class HlsQuality(val index: Int, val label: String, val externalAudio: Boolean)

internal object HlsQualityPolicy {
    private val attribute = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

    private fun attributes(line: String): Map<String, String> = attribute.findAll(line)
        .associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    fun parse(manifest: String): List<HlsQuality> {
        val lines = manifest.lineSequence().map(String::trim).toList()
        if (lines.firstOrNull() != "#EXTM3U") return emptyList()
        val externalGroups = lines.filter { it.startsWith("#EXT-X-MEDIA:") }
            .map { attributes(it.substringAfter(':')) }
            .filter { it["TYPE"] == "AUDIO" && !it["URI"].isNullOrBlank() }
            .mapNotNull { it["GROUP-ID"] }.toSet()
        val result = mutableListOf<HlsQuality>()
        var pending: Map<String, String>? = null
        var variantIndex = 0
        for (line in lines.drop(1)) {
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pending = attributes(line.substringAfter(':'))
            } else if (pending != null && line.isNotBlank() && !line.startsWith("#")) {
                val attrs = pending
                pending = null
                val height = attrs["RESOLUTION"]?.substringAfter('x')?.toIntOrNull()
                val bandwidth = (attrs["AVERAGE-BANDWIDTH"] ?: attrs["BANDWIDTH"])
                    ?.toLongOrNull()?.coerceAtLeast(0L)
                val label = when {
                    height != null && height > 0 -> "${height}p"
                    bandwidth != null && bandwidth > 0 -> "${bandwidth / 1000} кбит/с"
                    else -> "Вариант ${variantIndex + 1}"
                }
                result.add(HlsQuality(variantIndex, label,
                    attrs["AUDIO"] in externalGroups))
                variantIndex++
            }
        }
        return result
    }
}

/** Only the manifest is inspected. The playlist is bounded to 256 KB. */
internal object HlsQualityInspector {
    fun inspect(url: String, headers: Map<String, String> = emptyMap()): List<HlsQuality> {
        require(OfflineDownloadRoute.classify(url) == OfflineTransfer.ADAPTIVE_HLS) {
            "Нужна HTTPS-ссылка на M3U8"
        }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "Сервер вернул HTTP $code" }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    require(result.length + n <= 262_144) { "Плейлист слишком большой" }
                    result.append(buffer, 0, n)
                }
                result.toString()
            }
            HlsQualityPolicy.parse(text)
        } finally {
            connection.disconnect()
        }
    }
}
