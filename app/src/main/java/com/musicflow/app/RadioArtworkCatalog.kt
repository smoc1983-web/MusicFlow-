package com.musicflow.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Metadata-only radio cover resolution. No Radio Browser streams or stations are imported.
 * Do not trust a syntactically valid image URL: retired station favicons often serve 404 or HTML.
 */
internal object RadioArtworkCatalog {
    private const val API = "https://de1.api.radio-browser.info/json/stations/"
    private const val MAX_BYTES = 70_000
    private const val POSITIVE_TTL = 7L * 24 * 60 * 60 * 1000
    private const val NEGATIVE_TTL = 6L * 60 * 60 * 1000
    private val permits = Semaphore(3)

    internal fun isSafeLogo(raw: String?): Boolean {
        val value = raw?.trim() ?: return false
        if (value.length !in 12..1000) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) && uri.rawUserInfo == null &&
            RussianRadioDirectory.isPublicStream(value)
    }

    private fun titleKey(title: String): String =
        title.trim().lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(Regex("""\s+"""), " ")

    /** Only matching station names; never display another station's brand. */
    internal fun candidateLogos(response: String, stationName: String): List<String> {
        val expected = titleKey(stationName)
        val json = JSONArray(response)
        val candidates = mutableListOf<String>()
        for (i in 0 until minOf(30, json.length())) {
            val station = json.optJSONObject(i) ?: continue
            if (titleKey(station.optString("name")) != expected) continue
            station.optString("favicon").trim().takeIf(::isSafeLogo)?.let(candidates::add)
        }
        return candidates.distinct()
    }

    internal fun findExactLogo(response: String, stationName: String): String? =
        candidateLogos(response, stationName).firstOrNull()

    /** A UUID response identifies the station independently of display-name variations. */
    private fun candidateLogosByUuid(response: String, uuid: String): List<String> {
        val json = JSONArray(response)
        val candidates = mutableListOf<String>()
        for (i in 0 until minOf(10, json.length())) {
            val station = json.optJSONObject(i) ?: continue
            if (!station.optString("stationuuid").equals(uuid, ignoreCase = true)) continue
            station.optString("favicon").trim().takeIf(::isSafeLogo)?.let(candidates::add)
        }
        return candidates.distinct()
    }

    /** Recognize image headers, not HTML/JSON errors returned with a 200 status. */
    internal fun isSupportedImage(bytes: ByteArray, count: Int): Boolean {
        if (count < 3) return false
        val b0 = bytes[0].toInt() and 255
        val b1 = bytes[1].toInt() and 255
        val b2 = bytes[2].toInt() and 255
        if (count >= 8 && b0 == 137 && b1 == 80 && b2 == 78 &&
            bytes[3] == 71.toByte() && bytes[4] == 13.toByte() &&
            bytes[5] == 10.toByte() && bytes[6] == 26.toByte() &&
            bytes[7] == 10.toByte()) return true
        if (b0 == 255 && b1 == 216 && b2 == 255) return true
        if (count >= 6) {
            val magic = String(bytes, 0, 6, Charsets.US_ASCII)
            if (magic == "GIF87a" || magic == "GIF89a") return true
        }
        return count >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
    }

    /**
     * Small GET probe: HEAD is rejected by many logo CDNs. Read only the file signature
     * and close the connection. Every redirect must stay on public HTTPS.
     */
    private fun imageExists(raw: String): Boolean {
        var current = raw
        repeat(4) {
            if (!isSafeLogo(current)) return false
            val conn = try {
                URL(current).openConnection() as HttpURLConnection
            } catch (_: Exception) {
                return false
            }
            conn.connectTimeout = 3_000
            conn.readTimeout = 4_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/gif,image/*;q=0.7")
            conn.setRequestProperty("User-Agent", "MusicFlow-RadioArtwork/0.7.50")
            try {
                val status = conn.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location") ?: return false
                    current = URI(current).resolve(location).toString()
                } else {
                    if (status != 200 && status != 206) return false
                    if (conn.contentLengthLong > 5_000_000L) return false
                    val header = ByteArray(16)
                    var count = 0
                    conn.inputStream.use { input ->
                        while (count < header.size) {
                            val read = input.read(header, count, header.size - count)
                            if (read <= 0) break
                            count += read
                        }
                    }
                    return isSupportedImage(header, count)
                }
            } catch (_: Exception) {
                return false
            } finally {
                conn.disconnect()
            }
        }
        return false
    }

    private fun fetch(url: String): String {
        require(url.startsWith(API))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 4_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "MusicFlow-RadioArtwork/0.7.50")
        try {
            if (conn.responseCode != 200) return "[]"
            val data = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (data.size() + count > MAX_BYTES) return "[]"
                    data.write(buffer, 0, count)
                }
            }
            return data.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun resolve(context: Context, station: RussianRadioStation): String? {
        val prefs = context.applicationContext.getSharedPreferences(
            "musicflow_radio_artwork_v2", Context.MODE_PRIVATE
        )
        val key = station.uuid.take(80)
        val now = System.currentTimeMillis()
        val last = prefs.getLong("checked:$key", 0L)
        val cached = prefs.getString("logo:$key", null)?.takeIf(::isSafeLogo)
        if (last in 1..now) {
            if (cached != null && now - last < POSITIVE_TTL) return cached
            if (cached == null && now - last < NEGATIVE_TTL) return null
        }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val attempted = mutableSetOf<String>()
                    fun firstWorking(candidates: List<String>): String? =
                        candidates.firstOrNull { url ->
                            isSafeLogo(url) && attempted.add(url) && imageExists(url)
                        }

                    // Verify the playlist's tvg-logo before accepting it.
                    var found = firstWorking(listOfNotNull(station.favicon))
                    if (found == null) {
                        val uuid = station.uuid.takeIf {
                            it.matches(Regex("""[a-fA-F0-9-]{36}"""))
                        }
                        if (uuid != null) {
                            val response = runCatching { fetch(API + "byuuid/" + uuid) }
                                .getOrDefault("[]")
                            found = firstWorking(candidateLogosByUuid(response, uuid))
                        }
                    }
                    if (found == null) {
                        val query = URLEncoder.encode(station.name.take(90), "UTF-8")
                        val response = runCatching {
                            fetch(API + "byname/" + query + "?limit=25")
                        }.getOrDefault("[]")
                        found = firstWorking(candidateLogos(response, station.name))
                    }

                    // Failed/missing images are retried after six hours, not a full week.
                    prefs.edit().putLong("checked:$key", now)
                        .putString("logo:$key", found).apply()
                    found
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (e: Exception) {
                    AppDiagnostics.warning(
                        "RadioArtwork", "metadata lookup failed: " + e.javaClass.simpleName
                    )
                    null
                }
            }
        }
    }
}
