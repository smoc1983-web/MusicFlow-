package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class WorldChartCoversTest {
    @Test fun listenBrainzTracksWithoutArtworkArePreservedForLookup() {
        val json = """
            {"payload":{"recordings":[
                {"track_name":"Blinding Lights","artist_name":"The Weeknd","listen_count":120},
                {"track_name":"Bad Romance","artist_name":"Lady Gaga","listen_count":95},
                {"track_name":"  ","artist_name":"Unknown","listen_count":19},
                {"track_name":"Missing Artist","artist_name":"  ","listen_count":12}
            ]}}
        """.trimIndent()
        val list = TopSongsDirectory.parseWorld(json)
        assertEquals(2, list.size)
        assertEquals("The Weeknd", list[0].artist)
        assertEquals("Blinding Lights", list[0].title)
        assertEquals(120L, list[0].listenCount)
        assertNull(list[0].artwork)
    }

    @Test fun worldChartUsesResolvedCoverInsteadOfGenericIcon() {
        assertEquals("https://images.test/cover.jpg", ChartArtworkPolicy.displayUrl(
            isRussia = false, provided = null,
            lookup = "https://images.test/cover.jpg"
        ))
    }

    @Test fun worldChartPrefersVerifiedResolverWhenProvidedIsPresent() {
        assertEquals("https://images.test/high.jpg", ChartArtworkPolicy.displayUrl(
            isRussia = false, provided = "https://provider.test/thumb.jpg",
            lookup = "https://images.test/high.jpg"
        ))
    }

    @Test fun worldChartFallsBackToProvidedOrIconWhenResolverMisses() {
        assertEquals("https://provider.test/thumb.jpg", ChartArtworkPolicy.displayUrl(
            isRussia = false, provided = "https://provider.test/thumb.jpg", lookup = null
        ))
        assertNull(ChartArtworkPolicy.displayUrl(
            isRussia = false, provided = null, lookup = null
        ))
    }

    @Test fun russiaKeepsOriginalChartCover() {
        assertEquals("https://provider.test/russia.jpg", ChartArtworkPolicy.displayUrl(
            isRussia = true, provided = "https://provider.test/russia.jpg",
            lookup = "https://other.test/resolved.jpg"
        ))
    }

    @Test fun untrustedNonHttpsUrlsNeverEnterCoverPainter() {
        assertNull(ChartArtworkPolicy.displayUrl(
            isRussia = false, provided = "http://insecure.test/image.jpg",
            lookup = "file:///private.jpg"
        ))
    }
}
