package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class ClipboardDownloadUrlTest {
    @Test fun pasteFindsVideoLinkInSharedMessage() {
        assertEquals("https://cdn.example.org/movie.mp4?token=abc",
            ClipboardDownloadUrl.extract("Скачать: https://cdn.example.org/movie.mp4?token=abc"))
    }

    @Test fun pasteAcceptsHlsAndDash() {
        assertEquals("https://cdn.example.org/master.m3u8",
            ClipboardDownloadUrl.extract("(https://cdn.example.org/master.m3u8)"))
        assertEquals("https://cdn.example.org/movie.mpd",
            ClipboardDownloadUrl.extract("«https://cdn.example.org/movie.mpd»"))
    }

    @Test fun noUnsafeUrlFromClipboard() {
        assertNull(ClipboardDownloadUrl.extract("Обычный текст"))
        assertNull(ClipboardDownloadUrl.extract("http://site.example.org/movie.mp4"))
        assertNull(ClipboardDownloadUrl.extract("https://site.example.org/page?id=123"))
        assertNull(ClipboardDownloadUrl.extract("file:///sdcard/a.mp4"))
    }

    @Test fun trailingPunctuationIsTrimmed() {
        assertEquals("https://cdn.example.org/file.mp3",
            ClipboardDownloadUrl.extract("«https://cdn.example.org/file.mp3»."))
    }
}
