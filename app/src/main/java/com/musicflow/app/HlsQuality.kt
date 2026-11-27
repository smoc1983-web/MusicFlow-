package com.musicflow.app

import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Metadata from a HLS multivariant playlist; index matches Media3 HLS variant stream key. */
internal data class HlsQuality(
    val index: Int, val label: String, val externalAudio: Boolean,
    val estimatedBytes: Long? = null
)

internal object HlsQualityPolicy {
    private val attribute = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

    private fun attributes(line: String): Map<String, String> = attribute.findAll(line)
        .associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    /** First media playlist URI from a multivariant master (not an absolute URL). */
    fun firstMediaPlaylistUri(master: String): String? {
        var awaitingVariant = false
        for (line in master.lineSequence().map(String::trim)) {
            if (line.startsWith("#EXT-X-STREAM-INF:")) awaitingVariant = true
            else if (awaitingVariant && line.isNotBlank() && !line.startsWith("#"))
                return line.take(2048)
        }
        return null
    }

    /** A complete on-demand media playlist is needed to estimate its duration. */
    fun vodDurationSeconds(media: String): Double? {
        if (!media.startsWith("#EXTM3U") || !media.contains("#EXT-X-ENDLIST")) return null
        var seconds = 0.0
        var count = 0
        for (line in media.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#EXTINF:")) continue
            val part = trimmed.substringAfter(':').substringBefore(',').toDoubleOrNull()
                ?: return null
            if (!part.isFinite() || part <= 0.0 || part > 86_400.0) return null
            seconds += part
            count++
            if (seconds > 604_800.0 || count > 30_000) return null
        }
        return seconds.takeIf { count > 0 && it > 0.0 }
    }

    fun withEstimates(master: String, durationSeconds: Double?): List<HlsQuality> {
        val qualities = parse(master)
        val rates = master.lineSequence().map(String::trim)
            .filter { it.startsWith("#EXT-X-STREAM-INF:") }
            .map { attributes(it.substringAfter(':')) }
            .map { attrs ->
                (attrs["AVERAGE-BANDWIDTH"] ?: attrs["BANDWIDTH"])
                    ?.toLongOrNull()?.takeIf { it > 0 }
            }.toList()
        return qualities.mapIndexed { index, quality ->
            quality.copy(estimatedBytes =
                if (quality.externalAudio) null
                else DownloadSizeEstimate.bytes(durationSeconds, rates.getOrNull(index)))
        }
    }

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
            val playlistUri = HlsQualityPolicy.firstMediaPlaylistUri(text)
            // One bounded child playlist supplies a duration common to the
            // variants of the same on-demand media. Do not guess for live HLS.
            val durationSeconds = if (playlistUri == null) null else runCatching {
                val relative = URI(playlistUri)
                val child = URI(url).resolve(relative)
                require(child.scheme.equals("https", ignoreCase = true) &&
                    child.rawUserInfo == null && RussianRadioDirectory.isPublicStream(child.toString())) {
                    "Unsafe HLS child URL"
                }
                val part = (child.toURL().openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    instanceFollowRedirects = false
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                }
                try {
                    require(part.responseCode in 200..299)
                    val result = StringBuilder()
                    part.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(4096)
                        while (true) {
                            val n = reader.read(buffer)
                            if (n < 0) break
                            require(result.length + n <= 262_144)
                            result.append(buffer, 0, n)
                        }
                    }
                    HlsQualityPolicy.vodDurationSeconds(result.toString())
                } finally {
                    part.disconnect()
                }
            }.getOrNull()
            HlsQualityPolicy.withEstimates(text, durationSeconds)
        } finally {
            connection.disconnect()
        }
    }
}
