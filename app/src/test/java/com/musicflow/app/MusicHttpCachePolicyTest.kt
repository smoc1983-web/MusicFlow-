package com.musicflow.app

import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test

class MusicHttpCachePolicyTest {
    private val url = "https://example.org/track.mp3"
    private fun headers(vararg values: String) = Headers.headersOf(*values)

    @Test fun onlyDirectHttpsAudioOrKnownMusicEndpointsCanUseCache() {
        assertTrue(MusicHttpCachePolicy.eligible(url))
        assertTrue(MusicHttpCachePolicy.eligible("https://muzofond.fm/audio/song"))
        for (uri in listOf("http://example.org/a.mp3", "https://example.org/a.mp3?token=secret",
            "https://user:password@example.org/a.mp3", "https://example.org/a.m3u8",
            "https://example.org/a.mp4", "https://example.org/a.mpd", "zaycev://track/123",
            "chart://track/123", "file:///tmp/a.mp3")) {
            assertFalse(uri, MusicHttpCachePolicy.eligible(uri))
        }
        assertFalse(MusicHttpCachePolicy.eligible(url, setOf("Authorization")))
        assertFalse(MusicHttpCachePolicy.eligible(url, setOf("Cookie")))
    }

    @Test fun onlyExplicitlyPublicFreshFiniteAudioIsStored() {
        val good = headers("Cache-Control","public, max-age=3600",
            "Content-Type","audio/mpeg", "Content-Length","1024")
        assertTrue(MusicHttpCachePolicy.mayStore(url, emptySet(), 200, good))
        assertFalse(MusicHttpCachePolicy.mayStore(url, emptySet(), 206, good))
        assertFalse(MusicHttpCachePolicy.mayStore(url, setOf("Authorization"), 200, good))
        for (control in listOf("no-store", "private, max-age=3600", "public, no-cache",
            "public, max-age=0", "max-age=3600", "public")) {
            assertFalse(control, MusicHttpCachePolicy.mayStore(url, emptySet(), 200,
                good.newBuilder().set("Cache-Control", control).build()))
        }
        assertFalse(MusicHttpCachePolicy.mayStore(url, emptySet(), 200,
            good.newBuilder().removeAll("Content-Length").build()))
        assertFalse(MusicHttpCachePolicy.mayStore(url, emptySet(), 200,
            good.newBuilder().set("Content-Type", "video/mp4").build()))
        assertFalse(MusicHttpCachePolicy.mayStore(url, emptySet(), 200,
            good.newBuilder().set("Set-Cookie", "session=private").build()))
    }

    @Test fun limitsHaveSafeDefaults() {
        assertEquals(2048, MusicHttpCachePolicy.normalizeLimit(9999))
        assertEquals(0, MusicHttpCachePolicy.normalizeLimit(0))
        assertEquals(512, MusicHttpCachePolicy.normalizeLimit(512))
    }
}
