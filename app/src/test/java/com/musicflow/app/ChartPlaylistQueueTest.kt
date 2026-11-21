package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class ChartPlaylistQueueTest {
    private val songs = listOf(
        TopSong("Первый", "Исполнитель", 100L),
        TopSong("Тест & музыка", "Дуэт / певец", 80L),
        TopSong("Последний", "Другой", 60L)
    )

    @Test fun chartQueueKeepsOrderAndStartsSelectedIndex() {
        val playable = Track(
            "zaycev:456", "Тест & музыка", "Дуэт / певец", null,
            "zaycev://track/456", 150L
        )
        val queue = ChartPlaylistQueue.build(songs[1], songs, playable)!!
        assertEquals(3, queue.tracks.size)
        assertEquals(1, queue.selectedIndex)
        assertEquals("Тест & музыка", queue.tracks[1].title)
        assertEquals("zaycev://track/456", queue.tracks[1].stream)
        assertEquals("chart", java.net.URI(queue.tracks[0].stream).scheme)
        assertEquals("chart", java.net.URI(queue.tracks[2].stream).scheme)
    }

    @Test fun chartPlaceholdersEncodeRussianCharactersAndEscapeUrlParameters() {
        val query = ChartPlaylistQueue.uri(songs[1])
        assertEquals(ChartPlaylistQueue.Lookup("Дуэт / певец", "Тест & музыка"),
            ChartPlaylistQueue.parse(query))
        assertNull(ChartPlaylistQueue.parse("chart://evil?artist=X&title=Y"))
        assertNull(ChartPlaylistQueue.parse("https://track?artist=X&title=Y"))
        assertNull(ChartPlaylistQueue.parse("chart://track?artist=X"))
        assertNull(ChartPlaylistQueue.parse("chart://track?artist=X&title=Y&redirect=Z"))
    }

    @Test fun chartSelectionMustMatchActualTrack() {
        val wrong = Track("zaycev:123", "Посторонняя", "Другой", null,
            "zaycev://track/123", 0L)
        assertNull(ChartPlaylistQueue.build(songs[1], songs, wrong))
        assertNull(ChartPlaylistQueue.build(songs[1], songs, null))
    }

    @Test fun chartDeduplicatesOnlyExactRecordingAndPreservesNeighbors() {
        val dup = songs + songs[1] + songs[0]
        val playable = Track("zaycev:456", "Тест & музыка", "Дуэт / певец",
            null, "zaycev://track/456", 0L)
        val queue = ChartPlaylistQueue.build(songs[1], dup, playable)!!
        assertEquals(3, queue.tracks.size)
        assertEquals(1, queue.selectedIndex)
    }
}
