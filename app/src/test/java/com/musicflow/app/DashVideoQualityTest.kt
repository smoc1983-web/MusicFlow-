package com.musicflow.app

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import org.junit.Assert.*
import org.junit.Test

@OptIn(UnstableApi::class)
class DashVideoQualityTest {
    private fun parse(mpd: String) = DashManifestParser().parse(
        Uri.parse("https://video.example.org/test.mpd"),
        mpd.byteInputStream(Charsets.UTF_8)
    )

    @Test fun selects720pAndKeepsAudioAcrossPeriods() {
        val xml = """
          <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
               mediaPresentationDuration="PT12S" minBufferTime="PT1S">
            <Period duration="PT6S">
              <AdaptationSet mimeType="video/mp4" contentType="video">
                <Representation id="v360a" bandwidth="700000" width="640" height="360"><BaseURL>v360.mp4</BaseURL></Representation>
                <Representation id="v720a" bandwidth="2100000" width="1280" height="720"><BaseURL>v720.mp4</BaseURL></Representation>
              </AdaptationSet>
              <AdaptationSet mimeType="audio/mp4" contentType="audio">
                <Representation id="audioa" bandwidth="128000"><BaseURL>audio.mp4</BaseURL></Representation>
              </AdaptationSet>
            </Period>
            <Period duration="PT6S">
              <AdaptationSet mimeType="audio/mp4" contentType="audio">
                <Representation id="audiob" bandwidth="128000"><BaseURL>audiob.mp4</BaseURL></Representation>
              </AdaptationSet>
              <AdaptationSet mimeType="video/mp4" contentType="video">
                <Representation id="v360b" bandwidth="750000" width="640" height="360"><BaseURL>v360b.mp4</BaseURL></Representation>
                <Representation id="v720b" bandwidth="2200000" width="1280" height="720"><BaseURL>v720b.mp4</BaseURL></Representation>
              </AdaptationSet>
            </Period>
          </MPD>
        """.trimIndent()
        val qualities = DashVideoQualityPolicy.choices(parse(xml))
        assertEquals(listOf("360p", "720p"), qualities.map { it.label })
        assertEquals(listOf(
            StreamKey(0, 0, 1), StreamKey(0, 1, 0),
            StreamKey(1, 1, 1), StreamKey(1, 0, 0)
        ), qualities[1].streamKeys)
    }

    @Test fun noVideoRepresentationsReturnsNoQualityChoices() {
        val xml = """
          <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
               mediaPresentationDuration="PT6S" minBufferTime="PT1S">
            <Period>
              <AdaptationSet contentType="audio" mimeType="audio/mp4">
                <Representation id="audio" bandwidth="96000"><BaseURL>audio.mp4</BaseURL></Representation>
              </AdaptationSet>
            </Period>
          </MPD>
        """.trimIndent()
        assertTrue(DashVideoQualityPolicy.choices(parse(xml)).isEmpty())
    }
}
