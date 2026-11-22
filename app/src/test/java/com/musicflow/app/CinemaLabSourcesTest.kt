package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaLabSourcesTest {
    @Test fun safeHttpsUrlsRejectPrivateNetworksAndInlineCredentials() {
        assertTrue(CinemaLabSources.isSafeHttps(
            "https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/main/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("http://example.com/media.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://localhost/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://192.168.1.1/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://10.0.0.4/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://172.20.0.2/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://user:pass@example.com/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://example.com:8080/catalog.json"))
        assertFalse(CinemaLabSources.isSafeHttps("https://example.com/catalog#fragment"))
    }

    @Test fun identifiesForeignLampaJavascriptWithoutExecutingIt() {
        assertTrue(CinemaLabSources.isLampaScriptUrl("http://bwa.ad/rc"))
        assertTrue(CinemaLabSources.isLampaScriptUrl("https://example.com/plugin.js"))
        assertFalse(CinemaLabSources.isLampaScriptUrl(
            "https://example.com/plugins/my-cinema.json"))
    }

    @Test fun manifestRequiresCinemaV1SchemaAndSafeCatalog() {
        val url = "https://example.com/cinema-plugin.json"
        val good = """{"format":"musicflow.cinema.v1","name":"Demo source","catalog_url":"https://example.com/catalog.json"}"""
        val plugin = CinemaLabSources.parseManifest(good, url)
        assertEquals("Demo source", plugin.name)
        assertEquals("https://example.com/catalog.json", plugin.catalogUrl)

        val invalid = """{"format":"lampa.js","name":"Unsafe","catalog_url":"https://example.com/catalog.json"}"""
        assertTrue(runCatching { CinemaLabSources.parseManifest(invalid, url) }.isFailure)
        val local = """{"format":"musicflow.cinema.v1","name":"Local","catalog_url":"https://127.0.0.1/catalog.json"}"""
        assertTrue(runCatching { CinemaLabSources.parseManifest(local, url) }.isFailure)
    }

    @Test fun catalogParsesPlayableMoviesAndSeriesEpisodes() {
        val raw = """
            {"items":[
                {"id":"movie1","title":"Sample film","type":"movie","year":2008,
                 "stream_url":"https://example.com/film.mp4","genre":"Animation"},
                {"id":"series1","title":"Sample collection","type":"series","episodes":[
                  {"id":"ep1","title":"Episode One","stream_url":"https://example.com/ep1.mp4"}
                ]},
                {"id":"bad","title":"Bad scheme","type":"movie",
                 "stream_url":"javascript:alert(1)"}
            ]}
        """.trimIndent()
        val items = CinemaLabSources.parseCatalog(raw, "test")
        assertEquals(3, items.size)
        assertEquals("test:movie1", items[0].key)
        assertTrue(items[0].hasPlayback)
        assertEquals(CinemaKind.SERIES, items[1].kind)
        assertEquals(1, items[1].episodes.size)
        assertTrue(items[1].hasPlayback)
        assertFalse(items[2].hasPlayback)
    }
}
