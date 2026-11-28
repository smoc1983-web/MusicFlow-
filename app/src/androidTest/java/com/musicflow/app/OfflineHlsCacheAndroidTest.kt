package com.musicflow.app

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class OfflineHlsCacheAndroidTest {
    @Test fun servesActualMedia3CacheWithoutNetworkAndRejectsMissingBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "hls-bridge-test-" + System.nanoTime())
        val database = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(directory, NoOpCacheEvictor(), database)
        val root = "https://offline.invalid/movie.m3u8"
        val segment = "https://offline.invalid/part.ts"
        fun seed(uri: String, text: String) {
            val bytes = text.toByteArray()
            val span = cache.startReadWrite(uri, 0, bytes.size.toLong())
            try {
                val file = cache.startFile(uri, 0, bytes.size.toLong())
                file.writeBytes(bytes)
                cache.commitFile(file, bytes.size.toLong())
                val metadata = ContentMetadataMutations()
                ContentMetadataMutations.setContentLength(metadata, bytes.size.toLong())
                cache.applyContentMetadataMutations(uri, metadata)
            } finally { cache.releaseHoleSpan(span) }
        }
        try {
            seed(root, "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\npart.ts\n#EXT-X-ENDLIST\n")
            val backend = Media3HlsCache(cache)
            assertTrue(runCatching { OfflineHlsBridge.open(root, emptyList(), backend).close() }.isFailure)
            seed(segment, "TEST SEGMENT")
            OfflineHlsBridge.open(root, emptyList(), backend).use { bridge ->
                val body = URL(bridge.url).readText()
                assertFalse(body.contains("offline.invalid"))
                val url = body.lineSequence().first { it.startsWith("http://") }
                assertEquals("TEST SEGMENT", URL(url).readText())
            }
        } finally {
            cache.release()
            database.close()
            directory.deleteRecursively()
        }
    }
}
