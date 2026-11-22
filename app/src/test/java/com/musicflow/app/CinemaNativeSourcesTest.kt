package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaNativeSourcesTest {
    @Test fun m3uPlaylistLoadsNativeMedia3Videos() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Фильмы" tvg-logo="https://example.org/a.jpg",Тестовый фильм
            https://example.org/video.mp4
            #EXTINF:-1 group-title="Сериалы",Серия № 1
            https://example.org/series/one.m3u8
            #EXTINF:-1,Плохой адрес
            http://localhost/private.mp4
        """.trimIndent()
        val titles = CinemaNativeSources.parseM3u(m3u, "source-1")
        assertEquals(2, titles.size)
        assertEquals("Тестовый фильм", titles[0].title)
        assertEquals("Фильмы", titles[0].genre)
        assertEquals("https://example.org/a.jpg", titles[0].poster)
        assertEquals("source-1", titles[1].sourceId)
        assertEquals("https://example.org/series/one.m3u8", titles[1].streamUrl)
        assertTrue(titles.all { it.hasPlayback })
    }

    @Test fun directMediaFormatsAndExternalHostsAreStrict() {
        assertTrue(CinemaNativeSources.isVideoUrl("https://example.org/video.mp4"))
        assertTrue(CinemaNativeSources.isVideoUrl("https://example.org/live.m3u8?token=123"))
        assertTrue(CinemaNativeSources.isVideoUrl("https://example.org/manifest.mpd"))
        assertFalse(CinemaNativeSources.isVideoUrl("http://example.org/video.mp4"))
        assertFalse(CinemaNativeSources.isVideoUrl("https://127.0.0.1/live.m3u8"))
        assertFalse(CinemaNativeSources.isVideoUrl("https://example.org/catalog.json"))
    }

    @Test fun lampaPluginReferencesAreRecognizedButNotExecuted() {
        assertTrue(CinemaNativeSources.isLampaPluginUrl("http://bwa.ad/rc"))
        assertTrue(CinemaNativeSources.isLampaPluginUrl("https://bwa.ad/rc"))
        assertTrue(CinemaNativeSources.isLampaPluginUrl("https://example.org/online.js"))
        assertTrue(CinemaNativeSources.isValidLampaReference("http://bwa.ad/rc"))
        assertFalse(CinemaNativeSources.isValidLampaReference("http://localhost/rc"))
        assertFalse(CinemaNativeSources.isValidLampaReference(
            "https://user:password@example.org/online.js"))
        assertFalse(CinemaNativeSources.isValidLampaReference("javascript:alert(1)"))
        assertFalse(CinemaSourceFormat.LAMPA_REFERENCE.let {
            CinemaNativeSource("id", "BwaRC", "https://bwa.ad/rc", it).playable
        })
    }

    @Test fun emptyAndUnsafeM3uEntriesDoNotBecomeFilms() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Unsafe private URL
            http://192.168.1.5/movie.mp4
            #EXTINF:-1,Missing URL
        """.trimIndent()
        assertTrue(CinemaNativeSources.parseM3u(playlist, "s").isEmpty())
    }
}
