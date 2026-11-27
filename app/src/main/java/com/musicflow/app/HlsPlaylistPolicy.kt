package com.musicflow.app

import java.net.URI
import java.util.Locale

/** A deliberately conservative HLS VOD parser. Never decrypts content or joins fMP4 media. */
internal sealed interface HlsPlaylistResult {
    data class Master(val childPlaylist: String) : HlsPlaylistResult
    data class Media(val segments: List<String>, val extension: String) : HlsPlaylistResult
    data class Unsupported(val reason: String) : HlsPlaylistResult
}

internal object HlsPlaylistPolicy {
    private val bandwidth = Regex("""BANDWIDTH=(\d+)""")
    private val bannedTags = listOf(
        "#EXT-X-MAP:", "#EXT-X-BYTERANGE:", "#EXT-X-DISCONTINUITY",
        "#EXT-X-PART:", "#EXT-X-PRELOAD-HINT:", "#EXT-X-I-FRAMES-ONLY",
        "#EXT-X-SESSION-KEY:", "#EXT-X-SKIP:", "#EXT-X-DEFINE:"
    )

    fun parse(playlistUrl: String, playlist: String, kind: DownloadMediaKind): HlsPlaylistResult {
        if (playlist.length > 1_048_576) {
            return HlsPlaylistResult.Unsupported("Плейлист HLS слишком большой")
        }
        val lines = playlist.removePrefix("\uFEFF").lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != "#EXTM3U") {
            return HlsPlaylistResult.Unsupported("Это не плейлист HLS")
        }
        if (lines.any { line -> bannedTags.any { line.startsWith(it) } }) {
            return HlsPlaylistResult.Unsupported(
                "Поток использует DRM, fMP4, байтовые диапазоны или сложные HLS-сегменты"
            )
        }
        if (lines.any { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO") }) {
            return HlsPlaylistResult.Unsupported("HLS с отдельной звуковой дорожкой пока не поддерживается")
        }
        if (lines.any { it.startsWith("#EXT-X-KEY:") && !it.contains("METHOD=NONE") }) {
            return HlsPlaylistResult.Unsupported("Зашифрованный HLS не загружается")
        }
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF:") }) {
            val variants = mutableListOf<Pair<Long, String>>()
            for (i in lines.indices) {
                val line = lines[i]
                if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
                val next = lines.getOrNull(i + 1)
                if (next.isNullOrBlank() || next.startsWith("#")) {
                    return HlsPlaylistResult.Unsupported("В HLS нет адреса варианта качества")
                }
                val resolved = resolve(playlistUrl, next)
                    ?: return HlsPlaylistResult.Unsupported("Вариант HLS должен использовать HTTPS")
                val name = URI(resolved).path.lowercase(Locale.ROOT)
                if (!name.endsWith(".m3u8")) {
                    return HlsPlaylistResult.Unsupported("Ссылка качества не ведёт на .m3u8")
                }
                val rate = bandwidth.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                variants += rate to resolved
            }
            val best = variants.maxByOrNull { it.first }?.second
                ?: return HlsPlaylistResult.Unsupported("Нет подходящего качества HLS")
            return HlsPlaylistResult.Master(best)
        }
        if (lines.none { it == "#EXT-X-ENDLIST" }) {
            return HlsPlaylistResult.Unsupported("Live HLS не сохраняется; нужен завершённый VOD")
        }
        val resources = lines.filter { !it.startsWith("#") }
        if (resources.isEmpty() || resources.size > 10_000) {
            return HlsPlaylistResult.Unsupported("Неверное число сегментов HLS")
        }
        val allowedExtensions = if (kind == DownloadMediaKind.VIDEO)
            setOf("ts") else setOf("ts", "aac", "mp3")
        var format: String? = null
        val result = mutableListOf<String>()
        for (relative in resources) {
            val absolute = resolve(playlistUrl, relative)
                ?: return HlsPlaylistResult.Unsupported("Сегмент HLS доступен только по HTTPS")
            val ext = URI(absolute).path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext !in allowedExtensions) {
                return HlsPlaylistResult.Unsupported(
                    "Найдены fMP4/неподдерживаемые сегменты: нужен TS, AAC или MP3"
                )
            }
            if (format != null && ext != format) {
                return HlsPlaylistResult.Unsupported("В HLS смешаны разные форматы сегментов")
            }
            format = ext
            result += absolute
        }
        return HlsPlaylistResult.Media(result, format ?: "ts")
    }

    private fun resolve(base: String, relative: String): String? = try {
        val uri = URI(base).resolve(URI(relative))
        val absolute = uri.toASCIIString()
        if (MediaDownloadPolicy.isSafeHttps(absolute)) absolute else null
    } catch (_: Exception) {
        null
    }
}
