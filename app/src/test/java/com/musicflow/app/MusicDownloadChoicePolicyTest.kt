package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class MusicDownloadChoicePolicyTest {
    @Test fun actualPublishedBitrateAppearsInChoice() {
        val item = MusicDownloadChoicePolicy.eligible(
            "https://radio.example.org/song.mp3?bitrate=320", "Source"
        )!!
        assertEquals("320 кбит/с", item.quality)
        assertEquals("Source · MP3 · 320 кбит/с", item.label)
    }

    @Test fun unknownBitrateNeverPresentedAs320() {
        val item = MusicDownloadChoicePolicy.eligible(
            "https://radio.example.org/song.mp3", "Zaycev"
        )!!
        assertNull(item.quality)
        assertTrue(item.label.contains("качество источника"))
    }

    @Test fun separateHlsAndDirectOptionsAndKeepSources() {
        val choices = MusicDownloadChoicePolicy.choices(listOf(
            MusicStream("mailru:1", "https://my.mail.ru/song.mp3"),
            MusicStream("muzofond:1", "https://muzofond.fm/music.mp3"),
            MusicStream("custom:1", "https://media.example.org/master.m3u8"),
            MusicStream("mailru:2", "https://my.mail.ru/song.mp3")
        ))
        assertEquals(3, choices.size)
        assertEquals("Mail.ru", choices.first().source)
        assertEquals("Muzofond", choices[1].source)
        assertTrue(choices[2].isAdaptive)
    }

    @Test fun rejectsWebPagesAndNonHttpsMedia() {
        assertNull(MusicDownloadChoicePolicy.eligible("https://example.org/search", "Source"))
        assertNull(MusicDownloadChoicePolicy.eligible("http://example.org/file.mp3", "Source"))
        assertNull(MusicDownloadChoicePolicy.eligible("zaycev://track/123", "Zaycev"))
    }
}
