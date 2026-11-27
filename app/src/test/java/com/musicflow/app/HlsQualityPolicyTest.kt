package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class HlsQualityPolicyTest {
    @Test fun parsesMasterVariantsInMedia3Order() {
        val variants = HlsQualityPolicy.parse(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360\nlow.m3u8\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=3800000,RESOLUTION=1920x1080\nhigh.m3u8"
        )
        assertEquals(listOf(HlsQuality(0, "360p", false),
            HlsQuality(1, "1080p", false)), variants)
    }

    @Test fun externalAudioVariantIsFlaggedToAvoidSilentVideo() {
        val result = HlsQualityPolicy.parse(
            "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",URI=\"audio.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2200000,AUDIO=\"a\"\nvideo.m3u8"
        )
        assertEquals(1, result.size)
        assertTrue(result.single().externalAudio)
    }

    @Test fun mediaPlaylistHasNoQualityChoices() {
        assertTrue(HlsQualityPolicy.parse("#EXTM3U\n#EXTINF:6,\nchunk.ts").isEmpty())
    }

    @Test fun ignoresBadManifestsAndHandlesBandwidthFallback() {
        assertTrue(HlsQualityPolicy.parse("not a manifest").isEmpty())
        assertEquals("1280 кбит/с", HlsQualityPolicy.parse(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nvariant.m3u8"
        ).single().label)
    }
}
