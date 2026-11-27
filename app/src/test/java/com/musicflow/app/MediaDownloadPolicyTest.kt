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

    @Test fun rejectsDashAndProtectedOrIndirectLinks() {
        val rejected = listOf(
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

    @Test fun supportsHlsPlaylistOnlyAsSegmentedVOD() {
        val v = MediaDownloadPolicy.plan(
            "https://cdn.example.org/movie/master.m3u8?token=signed",
            "Фильм", DownloadMediaKind.VIDEO
        )
        assertTrue(v is DownloadPlanResult.Accepted)
        val plan = (v as DownloadPlanResult.Accepted).value
        assertEquals(MediaTransferMode.HLS_VOD, plan.transferMode)
        assertEquals("Фильм.ts", plan.fileName)
        assertEquals("video/mp2t", plan.mimeType)
        val direct = MediaDownloadPolicy.plan(
            "https://cdn.example.org/video/video.ts", "Movie", DownloadMediaKind.VIDEO
        ) as DownloadPlanResult.Accepted
        assertEquals(MediaTransferMode.DIRECT, direct.value.transferMode)
    }

    @Test fun supportsAdditionalProgressiveVideoAndAudioExtensions() {
        for (extension in listOf("mp4","mkv","webm","mov","avi","3gp","ts","mpeg")) {
            assertTrue(".$extension", MediaDownloadPolicy.plan(
                "https://cdn.example.org/media/video.$extension", "Movie",
                DownloadMediaKind.VIDEO
            ) is DownloadPlanResult.Accepted)
        }
        for (extension in listOf("mp3","m4a","flac","aac","ogg","opus","wav","aiff")) {
            assertTrue(".$extension", MediaDownloadPolicy.plan(
                "https://cdn.example.org/media/song.$extension", "Song",
                DownloadMediaKind.MUSIC
            ) is DownloadPlanResult.Accepted)
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
