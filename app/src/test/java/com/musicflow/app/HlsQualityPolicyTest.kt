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
    @Test fun finishedVodUsesDeclaredBitrateAndPlaylistDuration() {
        val master = "#EXTM3U\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nlow.m3u8\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=3200000,AVERAGE-BANDWIDTH=2000000,RESOLUTION=1280x720\nhi.m3u8"
        val child = "#EXTM3U\n#EXTINF:6.0,\npart1.ts\n#EXTINF:6.0,\npart2.ts\n#EXT-X-ENDLIST"
        assertEquals("low.m3u8", HlsQualityPolicy.firstMediaPlaylistUri(master))
        val duration = HlsQualityPolicy.vodDurationSeconds(child)
        assertEquals(12.0, duration!!, 0.01)
        val predicted = HlsQualityPolicy.withEstimates(master, duration)
        assertEquals(1_200_000L, predicted[0].estimatedBytes)
        assertEquals(3_000_000L, predicted[1].estimatedBytes)
    }

    @Test fun liveOrIncompletePlaylistNeverProducesInventedFileSize() {
        val playlist = "#EXTM3U\n#EXTINF:5.1,\npart1.ts"
        assertNull(HlsQualityPolicy.vodDurationSeconds(playlist))
        assertNull(HlsQualityPolicy.vodDurationSeconds("#EXTM3U\n#EXT-X-ENDLIST"))
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nfoo.m3u8"
        assertNull(HlsQualityPolicy.withEstimates(master, null)[0].estimatedBytes)
    }

    @Test fun externalAudioQualityIsNotEstimatedAsCompleteDownload() {
        val master = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",URI=\"sound.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"a\"\nvid.m3u8"
        assertTrue(HlsQualityPolicy.withEstimates(master, 60.0).single().externalAudio)
        assertNull(HlsQualityPolicy.withEstimates(master, 60.0).single().estimatedBytes)
    }

}
