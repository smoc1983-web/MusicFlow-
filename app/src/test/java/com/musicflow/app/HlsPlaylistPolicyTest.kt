package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class HlsPlaylistPolicyTest {
    private val base = "https://cdn.example.org/stream/index.m3u8"
    private val vod = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXTINF:6,
        seg001.ts
        #EXTINF:5,
        seg002.ts?sig=123
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test fun acceptsFiniteUnencryptedTsVodAndResolvesSegments() {
        val plan = HlsPlaylistPolicy.parse(base, vod, DownloadMediaKind.VIDEO)
        assertTrue(plan is HlsPlaylistResult.Media)
        val media = plan as HlsPlaylistResult.Media
        assertEquals(listOf("https://cdn.example.org/stream/seg001.ts",
            "https://cdn.example.org/stream/seg002.ts?sig=123"), media.segments)
        assertEquals("ts", media.extension)
    }

    @Test fun masterPlaylistSelectsHighestBandwidthWithoutExternalRenditions() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000
            hd/index.m3u8
        """.trimIndent()
        val p = HlsPlaylistPolicy.parse(base, text, DownloadMediaKind.VIDEO)
        assertTrue(p is HlsPlaylistResult.Master)
        assertEquals("https://cdn.example.org/stream/hd/index.m3u8",
            (p as HlsPlaylistResult.Master).childPlaylist)
    }

    @Test fun rejectsLiveAndEncryptedVariantsAndFragmentedMp4() {
        val bad = listOf(
            vod.removeSuffix("#EXT-X-ENDLIST"),
            vod.replace("#EXT-X-TARGETDURATION:6", "#EXT-X-KEY:METHOD=AES-128,URI=\"secret\""),
            vod.replace("#EXT-X-TARGETDURATION:6", "#EXT-X-KEY:METHOD=SAMPLE-AES"),
            vod.replace("#EXTINF:5,", "#EXT-X-DISCONTINUITY\n#EXTINF:5,"),
            vod.replace("#EXTINF:5,", "#EXT-X-BYTERANGE:10@5\n#EXTINF:5,"),
            vod.replace("#EXTINF:5,", "#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:5,"),
            vod.replace("seg001.ts", "seg001.m4s"),
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000\nhttp://host.example/stream.m3u8"
        )
        for (playlist in bad) {
            val result = HlsPlaylistPolicy.parse(base, playlist, DownloadMediaKind.VIDEO)
            assertTrue("must reject unsupported playlist: $playlist",
                result is HlsPlaylistResult.Unsupported)
        }
    }

    @Test fun acceptsUnencryptedAacAudioButRejectsVideoAsAudio() {
        val aac = vod.replace(".ts", ".aac")
        val p = HlsPlaylistPolicy.parse(base, aac, DownloadMediaKind.MUSIC)
        assertTrue(p is HlsPlaylistResult.Media)
        assertEquals("aac", (p as HlsPlaylistResult.Media).extension)
        assertTrue(HlsPlaylistPolicy.parse(base, aac, DownloadMediaKind.VIDEO)
            is HlsPlaylistResult.Unsupported)
        val mixed = vod.replace("seg002.ts", "seg002.mp3")
        assertTrue(HlsPlaylistPolicy.parse(base, mixed, DownloadMediaKind.MUSIC)
            is HlsPlaylistResult.Unsupported)
    }

    @Test fun refusesSeparateAudioAndUntrustedSegmentUrls() {
        val externalAudio = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",URI=\"audio.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\nvideo.m3u8"
        assertTrue(HlsPlaylistPolicy.parse(base, externalAudio, DownloadMediaKind.VIDEO)
            is HlsPlaylistResult.Unsupported)
        assertTrue(HlsPlaylistPolicy.parse(base, vod.replace("seg001.ts", "http://insecure.example/one.ts"),
            DownloadMediaKind.VIDEO) is HlsPlaylistResult.Unsupported)
    }
}
