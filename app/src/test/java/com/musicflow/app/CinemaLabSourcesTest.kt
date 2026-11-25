package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaLabSourcesTest {
    @Test fun jsonCatalogCanBeLoadedWithoutAnyPluginCode() {
        val json = """
          {"items":[
            {"id":"film1","title":"Документальный фильм","type":"movie",
             "stream_url":"https://example.org/video.mp4",
             "description":"Документальный фильм"},
            {"id":"series1","title":"Сериал","type":"series",
             "episodes":[{"id":"ep1","title":"Серия 1",
                          "stream_url":"https://example.org/1.m3u8"}]},
            {"id":"bad","title":"Небезопасное видео","type":"movie",
             "stream_url":"javascript:alert(1)"}
          ]}
        """.trimIndent()
        val items = CinemaLabSources.parseCatalog(json, "my-source")
        assertEquals(3, items.size)
        assertEquals("my-source:film1", items[0].key)
        assertTrue(items[0].hasPlayback)
        assertEquals(1, items[1].episodes.size)
        assertFalse(items[2].hasPlayback)
    }

    @Test fun videoSourcesRequirePublicHttps() {
        assertTrue(CinemaLabSources.isSafeHttps("https://ff.sanfilm2.link/movie.html"))
        assertFalse(CinemaLabSources.isSafeHttps("http://example.org/movie.html"))
        assertFalse(CinemaLabSources.isSafeHttps("https://localhost/movie.html"))
        assertFalse(CinemaLabSources.isSafeHttps("https://127.0.0.1/movie.html"))
        assertFalse(CinemaLabSources.isSafeHttps("https://192.168.1.2/film.mp4"))
        assertFalse(CinemaLabSources.isSafeHttps("https://user:pass@example.org/film.mp4"))
        assertFalse(CinemaLabSources.isSafeHttps("https://example.org:8080/film.mp4"))
    }
}
