package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianRadioFallbackTest {
    @Test fun parsesKnownUuidAndRadioStream() {
        val m3u = """
            #EXTM3U
            # 72FM: https://72fm.com/station/63688c44-e90b-11e8-a471-52543be04c81?source=m3u
            #EXTINF:-1 tvg-logo="https://example.org/cover.png" group-title="Russia" radio="true",РАДИО ВАНЯ
            https://radio.example.org/stream.mp3
        """.trimIndent()
        val stations = RussianRadioFallback.parseM3u(m3u) { it.startsWith("https://") }
        assertEquals(1, stations.size)
        assertEquals("РАДИО ВАНЯ", stations[0].name)
        assertEquals("63688c44-e90b-11e8-a471-52543be04c81", stations[0].uuid)
        assertEquals("https://radio.example.org/stream.mp3", stations[0].stream)
        assertEquals("https://example.org/cover.png", stations[0].favicon)
    }

    @Test fun rejectsUntrustedStreamsAndDeduplicatesIds() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Недопустимая
            http://192.168.1.1/stream
            #EXTINF:-1,Допустимая
            https://example.org/music
            #EXTINF:-1,Повтор
            https://example.org/music
        """.trimIndent()
        val allowed = RussianRadioFallback.parseM3u(m3u) { it == "https://example.org/music" }
        assertEquals(1, allowed.size)
        assertEquals("Допустимая", allowed[0].name)
        assertTrue(allowed[0].mediaId.startsWith("radio:"))
    }

    @Test fun doesNotPairAnExtinfEntryWithAnUnrelatedNextEntry() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Одна
            https://example.org/one
            #EXTINF:-1,Другая
            https://example.org/two
        """.trimIndent()
        val list = RussianRadioFallback.parseM3u(m3u) { true }
        assertEquals(listOf("Одна", "Другая"), list.map { it.name })
    }
}
