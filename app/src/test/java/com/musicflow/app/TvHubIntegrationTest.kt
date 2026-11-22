package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHubIntegrationTest {
    private fun channel(name: String, group: String) =
        TvChannel("test-id", name, group, null, "https://iptv.example.org/live")

    @Test fun threeNewLayoutsAndClassicRemainAvailable() {
        assertEquals(listOf("Обзор", "Эфир", "Моё", "Классика"),
            TvHubMode.entries.map { it.title })
    }

    @Test fun filtersUseRealGroupOrChannelNames() {
        assertTrue(TvHubLibrary.channelMatchesGenre(channel("Новости", "Информация"), "Новости"))
        assertTrue(TvHubLibrary.channelMatchesGenre(channel("Вещание", "Спорт"), "Спорт"))
        assertFalse(TvHubLibrary.channelMatchesGenre(channel("Культура", "Документы"), "Спорт"))
        assertTrue(TvHubLibrary.channelMatchesGenre(channel("Любой", "Каталог"), "Все"))
    }

    @Test fun pluginUrlsRequirePublicHttpsAndNoCredentials() {
        assertTrue(TvPluginManager.isSafePluginUrl(
            "https://raw.githubusercontent.com/example/public/main/tv.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("http://example.org/manifest.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://localhost/manifest.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://192.168.1.1/a.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://10.0.0.1/a.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://172.20.0.1/a.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://127.0.0.1/a.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://user:pass@example.org/a.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://example.org:8080/a.json"))
        assertFalse(TvPluginManager.isSafePluginUrl("https://example.org/a#script"))
    }

    @Test fun epgTimestampAndCurrentWindow() {
        val timestamp = TvEpgParser.parseTime("20261122123000 +0300")
        assertNotNull(timestamp)
        assertEquals(1795339800000L, timestamp!!)
        assertTrue(TvProgramme("one", "Новости", timestamp, timestamp + 1800000)
            .isNow(timestamp + 2000))
        assertFalse(TvProgramme("one", "Новости", timestamp, timestamp + 1800000)
            .isNow(timestamp + 1900000))
        assertEquals(null, TvEpgParser.parseTime("not a date"))
    }

    @Test fun xmltvProgramsParseWithoutFakeSchedule() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv><programme start="20261122120000 +0300" stop="20261122123000 +0300"
                channel="first.tv"><title lang="ru">Новости</title></programme></tv>
        """.trimIndent()
        val list = TvEpgParser.parse(xml.toByteArray(Charsets.UTF_8))
        assertEquals(1, list.size)
        assertEquals("first.tv", list[0].channelId)
        assertEquals("Новости", list[0].title)
        assertEquals(1800000L, list[0].endMillis - list[0].startMillis)
    }
}
