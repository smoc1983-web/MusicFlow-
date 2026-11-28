package com.musicflow.app

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs

/**
 * DASH quality selection uses Media3's parsed AdaptationSet/Representation indices.
 * Each video choice includes a matching audio track for every period, avoiding
 * a silent offline movie. Never invent resolutions for an MP4 with one encoded stream.
 */
internal data class DashVideoQuality(
    val height: Int,
    val bandwidth: Int,
    val label: String,
    val streamKeys: List<StreamKey>,
    val estimatedBytes: Long? = null
)

@OptIn(UnstableApi::class)
internal object DashVideoQualityPolicy {
    /**
     * Some DASH parsers return Format.NO_VALUE for dimensions even when
     * Representation explicitly provides @height. Read that metadata from
     * the already-bounded manifest, never derive pixels from bitrate.
     */
    fun fromMpd(uri: Uri, bytes: ByteArray): List<DashVideoQuality> {
        val manifest = DashManifestParser().parse(uri, bytes.inputStream())
        val heights = runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            val map = mutableMapOf<String, Int>()
            var adaptationHeight = 0
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "AdaptationSet" -> {
                            adaptationHeight = parser.getAttributeValue(null, "height")
                                ?.toIntOrNull()?.takeIf { it in 1..4320 } ?: 0
                        }
                        "Representation" -> {
                            val id = parser.getAttributeValue(null, "id").orEmpty()
                            val declared = parser.getAttributeValue(null, "height")
                                ?.toIntOrNull()?.takeIf { it in 1..4320 }
                                ?: adaptationHeight.takeIf { it > 0 }
                            if (id.isNotBlank() && declared != null) map[id] = declared
                        }
                    }
                }
                parser.next()
            }
            map
        }.getOrDefault(emptyMap())
        return choices(manifest, heights)
    }

    fun choices(manifest: DashManifest, heightHints: Map<String, Int> = emptyMap()): List<DashVideoQuality> {
        if (manifest.periodCount < 1 || manifest.periodCount > 8) return emptyList()
        val first = manifest.getPeriod(0)
        val videoGroups = first.adaptationSets.withIndex()
            .filter { it.value.type == C.TRACK_TYPE_VIDEO }
        val mainVideo = videoGroups.firstOrNull()?.value ?: return emptyList()
        if (mainVideo.representations.isEmpty()) return emptyList()
        val options = mainVideo.representations.take(24)
        return options.mapIndexed { _, representation ->
            val targetHeight = representation.format.height.takeIf { it > 0 }
                ?: heightHints[representation.format.id.orEmpty()] ?: 0
            val bandwidth = representation.format.bitrate.takeIf { it > 0 } ?: 0
            val label = if (targetHeight > 0) "${targetHeight}p" else
                if (bandwidth > 0) "${bandwidth / 1000} кбит/с" else "Видео"
            val keys = mutableListOf<StreamKey>()
            var totalEstimatedBytes = 0L
            var canEstimate = true
            for (periodIndex in 0 until manifest.periodCount) {
                val period = manifest.getPeriod(periodIndex)
                val videos = period.adaptationSets.withIndex().filter {
                    it.value.type == C.TRACK_TYPE_VIDEO
                }
                val selectedVideo = videos.firstOrNull() ?: return@mapIndexed null
                val available = selectedVideo.value.representations
                if (available.isEmpty()) return@mapIndexed null
                val targetIndex = available.indices.minByOrNull { i ->
                    val candidateHeight = available[i].format.height.takeIf { it > 0 }
                        ?: heightHints[available[i].format.id.orEmpty()] ?: 0
                    val candidateBitrate = available[i].format.bitrate.takeIf { it > 0 } ?: 0
                    if (targetHeight > 0 && candidateHeight > 0)
                        abs(candidateHeight - targetHeight).toLong() else
                        abs(candidateBitrate.toLong() - bandwidth.toLong())
                } ?: return@mapIndexed null
                keys += StreamKey(periodIndex, selectedVideo.index, targetIndex)
                val audio = period.adaptationSets.withIndex().firstOrNull {
                    it.value.type == C.TRACK_TYPE_AUDIO &&
                        it.value.representations.isNotEmpty()
                }
                if (audio != null) {
                    // Include the same audio representation that Media3 downloads.
                    keys += StreamKey(periodIndex, audio.index, 0)
                }
                val selectedBitrate = available[targetIndex].format.bitrate.toLong()
                    .takeIf { it > 0L }
                val audioBitrate = audio?.value?.representations?.firstOrNull()
                    ?.format?.bitrate?.toLong()?.takeIf { it > 0L }
                val durationUs = manifest.getPeriodDurationUs(periodIndex)
                val estimate = if (selectedBitrate == null ||
                    (audio != null && audioBitrate == null) ||
                    durationUs == C.TIME_UNSET) null
                else DownloadSizeEstimate.bytes(
                    durationUs.toDouble() / 1_000_000.0,
                    selectedBitrate + (audioBitrate ?: 0L)
                )
                if (estimate == null || totalEstimatedBytes > Long.MAX_VALUE - estimate)
                    canEstimate = false
                else totalEstimatedBytes += estimate
            }
            DashVideoQuality(targetHeight, bandwidth, label, keys,
                totalEstimatedBytes.takeIf { canEstimate && it > 0L })
        }.filterNotNull().distinctBy { it.streamKeys }.take(24)
    }
}

@OptIn(UnstableApi::class)
internal object DashVideoQualityInspector {
    /** A bounded public HTTPS MPD. The native Media3 parser owns representation indices. */
    fun inspect(url: String): List<DashVideoQuality> {
        require(OfflineDownloadRoute.classify(url) == OfflineTransfer.ADAPTIVE_DASH) {
            "Нужна прямая HTTPS-ссылка на MPD"
        }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/dash+xml,application/xml,*/*")
        }
        return try {
            require(connection.responseCode in 200..299) {
                "Сервер DASH вернул HTTP ${connection.responseCode}"
            }
            val maxSize = 524_288
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxSize) { "MPD слишком большой" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            DashVideoQualityPolicy.fromMpd(Uri.parse(url), bytes)
        } finally {
            connection.disconnect()
        }
    }
}
