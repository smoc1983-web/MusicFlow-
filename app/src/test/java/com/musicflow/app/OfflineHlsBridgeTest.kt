package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class OfflineHlsBridgeTest {
    private val root = "https://example.test/root.m3u8"
    private val video = "https://example.test/v.m3u8"
    private val audio = "https://example.test/a.m3u8"
    private fun media(segment: String) = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\n$segment\n#EXT-X-ENDLIST\n"
    private fun cache(vararg entries: Pair<String, String>): OfflineHlsCache = object : OfflineHlsCache {
        val values = entries.toMap().mapValues { it.value.toByteArray() }
        override fun size(uri: String): Long = values[uri]?.size?.toLong() ?: -1
        override fun read(uri: String, offset: Long, length: Long, output: java.io.OutputStream) {
            val bytes = values.getValue(uri)
            output.write(bytes, offset.toInt(), length.toInt())
        }
    }

    @Test fun masterKeepsSelectedVariantAndSeparateAudio() {
        val master = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Stereo\",URI=\"a.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=10\nmissing.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=20,AUDIO=\"aud\"\nv.m3u8\n"
        val backend = cache(root to master, video to media("v.ts"), audio to media("a.aac"),
            "https://example.test/v.ts" to "VIDEO", "https://example.test/a.aac" to "AUDIO")
        OfflineHlsBridge.open(root, listOf(1), backend).use { bridge ->
            val playlist = URL(bridge.url).readText()
            assertFalse(playlist.contains("example.test"))
            assertFalse(playlist.contains("missing.m3u8"))
            assertTrue(playlist.contains("AUDIO=\"aud\""))
            val videoUrl = playlist.lineSequence().first { it.startsWith("http://") }
            val videoList = URL(videoUrl).readText()
            val segment = videoList.lineSequence().first { it.startsWith("http://") }
            val connection = URL(segment).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Range", "bytes=1-3")
            assertEquals(206, connection.responseCode)
            assertEquals("IDE", connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
            val unauth = URL(bridge.url.replaceAfterLast('/', "invalid")).openConnection() as java.net.HttpURLConnection
            assertEquals(404, unauth.responseCode)
            unauth.disconnect()
        }
    }

    @Test fun rejectsIncompleteEncryptedAndByteRangePlaylists() {
        val fixtures = listOf(
            media("missing.ts"),
            media("v.ts").replace("#EXTINF", "#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF"),
            media("v.ts").replace("#EXTINF", "#EXT-X-BYTERANGE:4@0\n#EXTINF"),
            media("v.ts").replace("#EXT-X-ENDLIST", "")
        )
        for (fixture in fixtures) {
            val backend = cache(root to fixture, "https://example.test/v.ts" to "VIDEO")
            assertTrue(runCatching { OfflineHlsBridge.open(root, emptyList(), backend).close() }.isFailure)
        }
    }

    @Test fun directMediaAndSuffixRange() {
        val backend = cache(root to media("v.ts"), "https://example.test/v.ts" to "VIDEO")
        OfflineHlsBridge.open(root, emptyList(), backend).use { bridge ->
            val url = URL(bridge.url).readText().lineSequence().first { it.startsWith("http://") }
            val c = URL(url).openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("Range", "bytes=-2")
            assertEquals(206, c.responseCode)
            assertEquals("EO", c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            val bad = URL(url).openConnection() as java.net.HttpURLConnection
            bad.setRequestProperty("Range", "bytes=50-")
            assertEquals(416, bad.responseCode)
            bad.disconnect()
        }
    }
}
