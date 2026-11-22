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
 * Radio Browser is metadata-only here: NEVER import its station list or streams.
 * Its open favicon field supplements tvg-logo in the independent radio playlist.
 */
internal object RadioArtworkCatalog {
    private const val API = "https://de1.api.radio-browser.info/json/stations/"
    private const val MAX_BYTES = 70_000
    private const val TTL = 7L * 24 * 60 * 60 * 1000
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

    /** Accept exact normalized identity, never a similarly named station. */
    internal fun findExactLogo(response: String, stationName: String): String? {
        val expected = titleKey(stationName)
        val json = JSONArray(response)
        for (i in 0 until minOf(20, json.length())) {
            val station = json.optJSONObject(i) ?: continue
            if (titleKey(station.optString("name")) != expected) continue
            val logo = station.optString("favicon").trim()
            if (isSafeLogo(logo)) return logo
        }
        return null
    }

    private fun fetch(url: String): String {
        require(url.startsWith(API))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 4_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "MusicFlow-RadioArtwork/0.7.45")
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
        if (isSafeLogo(station.favicon)) return station.favicon
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("musicflow_radio_artwork_v1",
            Context.MODE_PRIVATE)
        val key = station.uuid.take(80)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checked:$key", 0) < TTL) {
            return prefs.getString("logo:$key", null)?.takeIf(::isSafeLogo)
        }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val normalizedId = station.uuid
                        .takeIf { it.matches(Regex("""[a-fA-F0-9-]{36}""")) }
                    val byId = normalizedId?.let {
                        findExactLogo(fetch(API + "byuuid/" + it), station.name)
                    }
                    val byName = if (byId != null) byId else {
                        val query = URLEncoder.encode(station.name.take(90), "UTF-8")
                        findExactLogo(fetch(API + "byname/" + query + "?limit=15"),
                            station.name)
                    }
                    prefs.edit().putLong("checked:$key", now)
                        .putString("logo:$key", byName).apply()
                    byName
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (e: Exception) {
                    AppDiagnostics.warning("RadioArtwork",
                        "metadata lookup failed: " + e.javaClass.simpleName)
                    null
                }
            }
        }
    }
}
