package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TopSongsDirectoryTest {
    @Test fun russianChartParsesArtistTitleCountAndCover() {
        val html = """
            <ol class="rows">
              <li class="row" data-track="153919218">
                <span class="pos">1</span>
                <span class="track">
                  <div class="cover"><a class="art"><img src="/covers/track.webp"></a></div>
                  <span class="who">
                    <span class="title-line"><a class="title">ДИНАСТИЯ</a></span>
                    <span class="artists"><a>VILLIAN</a>, <a>madk1d</a></span>
                  </span>
                </span>
                <span class="num">2 437 551<span class="visually-hidden"> — сумма слушателей</span></span>
              </li>
              <li class="row"><a class="title">Неизвестная</a><span class="num">0</span></li>
            </ol>
        """.trimIndent()
        val actual = TopSongsDirectory.parseRussia(html)
        assertEquals(1, actual.size)
        assertEquals("ДИНАСТИЯ", actual[0].title)
        assertEquals("VILLIAN, madk1d", actual[0].artist)
        assertEquals(2_437_551L, actual[0].listenCount)
        assertEquals("https://blog.upsound.ai/covers/track.webp", actual[0].artwork)
    }

    @Test fun russianChartPeriodUrlsUseRequestedMoscowCalendarYear() {
        val date = LocalDate.of(2026, 11, 21)
        assertEquals("/charts/7d/", TopSongsDirectory.russiaPath(TopSongsPeriod.WEEK, date))
        assertEquals("/charts/2026-11/", TopSongsDirectory.russiaPath(TopSongsPeriod.MONTH, date))
        assertEquals("/charts/2026/", TopSongsDirectory.russiaPath(TopSongsPeriod.YEAR, date))
        val january = LocalDate.of(2027, 1, 1)
        assertEquals("/charts/2027-01/", TopSongsDirectory.russiaPath(TopSongsPeriod.MONTH, january))
    }

    @Test fun noFakeListeningDataWhenChartIsMissing() {
        val html = "<html><body><h1>Сервис недоступен</h1></body></html>"
        assertTrue(TopSongsDirectory.parseRussia(html).isEmpty())
    }
}
