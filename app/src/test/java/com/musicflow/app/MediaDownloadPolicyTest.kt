package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadPolicyTest {
    @Test fun acceptsExplicitDirectMusicFiles() {
        val result = MediaDownloadPolicy.plan(
            "https://media.example.org/music/recording.MP3?token=abc",
            "Исполнитель / Название",
            DownloadMediaKind.MUSIC
        )
        assertTrue(result is DownloadPlanResult.Accepted)
        val plan = (result as DownloadPlanResult.Accepted).value
        assertEquals("audio/mpeg", plan.mimeType)
        assertEquals("Исполнитель _ Название.mp3", plan.fileName)
        assertEquals("MusicFlow/Music", plan.relativeDirectory)
    }

    @Test fun acceptsDirectMovieAndUsesVideoDirectory() {
        val result = MediaDownloadPolicy.plan(
            "https://cdn.example.org/video/film.mp4?quality=1080",
            "Фильм: 1",
            DownloadMediaKind.VIDEO
        )
        assertTrue(result is DownloadPlanResult.Accepted)
        val plan = (result as DownloadPlanResult.Accepted).value
        assertEquals("video/mp4", plan.mimeType)
        assertEquals("Фильм_ 1.mp4", plan.fileName)
        assertEquals("MusicFlow/Movies", plan.relativeDirectory)
    }

    @Test fun rejectsHlsDashAndProtectedOrIndirectLinks() {
        val rejected = listOf(
            "https://cdn.example.org/master.m3u8",
            "https://cdn.example.org/manifest.mpd",
            "zaycev://track/123",
            "https://example.org/watch?id=1",
            "file:///sdcard/private.mp3",
            "https://name:password@example.org/a.mp3",
            "javascript:alert(1)"
        )
        for (uri in rejected) {
            assertTrue("$uri must be rejected", MediaDownloadPolicy.plan(
                uri, "Movie", DownloadMediaKind.VIDEO
            ) is DownloadPlanResult.Rejected)
        }
    }

    @Test fun doesNotPermitCrossMediaOrUnsafePath() {
        assertTrue(MediaDownloadPolicy.plan(
            "https://media.example.org/film.mp4", "Test", DownloadMediaKind.MUSIC
        ) is DownloadPlanResult.Rejected)
        assertTrue(MediaDownloadPolicy.plan(
            "https://media.example.org/song.mp3", "Test", DownloadMediaKind.VIDEO
        ) is DownloadPlanResult.Rejected)
        val valid = MediaDownloadPolicy.plan(
            "https://media.example.org/song.mp3",
            "../\\*?:<>|\"Song",
            DownloadMediaKind.MUSIC
        ) as DownloadPlanResult.Accepted
        assertFalse(valid.value.fileName.contains('/'))
        assertFalse(valid.value.fileName.contains('\\'))
        assertTrue(valid.value.fileName.endsWith(".mp3"))
    }

    @Test fun noInsecureOrBlankRequestsAccepted() {
        assertTrue(MediaDownloadPolicy.plan(
            "http://example.org/song.mp3", "Song", DownloadMediaKind.MUSIC
        ) is DownloadPlanResult.Rejected)
        assertTrue(MediaDownloadPolicy.plan(
            "", "Song", DownloadMediaKind.MUSIC
        ) is DownloadPlanResult.Rejected)
    }
}
