package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class ClipboardDownloadUrlTest {
    @Test fun extractsFirstHttpsMediaLinkFromMultilineClipboard() {
        val text = "Ссылка на фильм:\nhttps://cdn.example.org/movie.mp4?token=1\nСмотреть позже"
        assertEquals("https://cdn.example.org/movie.mp4?token=1",
            ClipboardDownloadUrl.extract(text))
    }

    @Test fun acceptsHlsAndAudioWithPunctuation() {
        assertEquals("https://a.example.org/master.m3u8",
            ClipboardDownloadUrl.extract("(https://a.example.org/master.m3u8)"))
        assertEquals("https://a.example.org/song.mp3",
            ClipboardDownloadUrl.extract("«https://a.example.org/song.mp3»"))
    }

    @Test fun doesNotInventLinksAndPrefersHttps() {
        assertNull(ClipboardDownloadUrl.extract("Привет!"))
        assertNull(ClipboardDownloadUrl.extract("http://a.example.org/a.mp4"))
        assertEquals("https://a.example.org/a.mkv",
            ClipboardDownloadUrl.extract("http://a.example.org/x.ts https://a.example.org/a.mkv"))
    }
}
