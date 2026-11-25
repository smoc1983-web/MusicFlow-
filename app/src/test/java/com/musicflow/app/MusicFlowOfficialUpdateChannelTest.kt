package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

/** Prevent preview/debug releases from reaching the official updater. */
class MusicFlowOfficialUpdateChannelTest {
    private fun release(
        version: String,
        prerelease: Boolean,
        asset: String,
        size: Int = 16_000_000
    ): String = """{
      "tag_name":"$version",
      "draft":false,
      "prerelease":$prerelease,
      "body":"Release",
      "assets":[{
        "name":"$asset",
        "size":$size,
        "browser_download_url":"https://github.com/smoc1983-web/MusicFlow-/releases/download/$version/$asset"
      }]
    }"""

    @Test fun testBuildIsRejectedEvenWhenGithubLabelsItNotPrerelease() {
        assertFalse(MusicFlowUpdates.isOfficialTag("v0.7.50-radio-artwork-test"))
        assertFalse(MusicFlowUpdates.isOfficialTag("v0.7.50-rc1"))
        assertFalse(MusicFlowUpdates.isOfficialTag("v0.7.50+test"))
        assertTrue(MusicFlowUpdates.isOfficialTag("v0.7.50"))
        assertFalse(MusicFlowUpdates.isOfficialTag("7.50"))
    }

    @Test fun testPackageCannotBeMistakenForProductionApk() {
        assertFalse(MusicFlowUpdates.isOfficialApkName(
            "v0.7.50", "MusicFlow-RadioArtwork-TEST-0.7.50.apk"))
        assertFalse(MusicFlowUpdates.isOfficialApkName(
            "v0.7.50", "MusicFlow_0.7.50-debug.apk"))
        assertTrue(MusicFlowUpdates.isOfficialApkName(
            "v0.7.50", "MusicFlow_0.7.50.apk"))
    }

    @Test fun officialFeedIgnoresHigherNumberedPreviewAndTakesValidOfficialVersion() {
        val raw = "[" +
            release("v0.7.50-radio-artwork-test", true, "MusicFlow-RadioArtwork-TEST-0.7.50.apk") + "," +
            release("v0.7.50", false, "MusicFlow_0.7.50.apk") + "," +
            release("v0.7.49", false, "MusicFlow_0.7.49.apk") + "]"
        val result = MusicFlowUpdates.parseOfficialReleases(raw)
        assertNotNull(result)
        assertEquals("v0.7.50", result?.version)
        assertEquals(750L, result?.versionCode)
    }

    @Test fun onlyPrereleaseResultsInNoUpdate() {
        val raw = "[" + release("v0.7.50", true, "MusicFlow_0.7.50.apk") + "," +
            release("v0.7.50-radio-artwork-test", false,
                "MusicFlow-RadioArtwork-TEST-0.7.50.apk") + "]"
        assertNull(MusicFlowUpdates.parseOfficialReleases(raw))
    }

    @Test fun wrongApkNameIsRejectedEvenOnOfficialTag() {
        val raw = "[" + release("v0.7.50", false,
            "MusicFlow-RadioArtwork-TEST-0.7.50.apk") + "]"
        assertNull(MusicFlowUpdates.parseOfficialReleases(raw))
    }

    @Test fun cachedTestDownloadIsNotAccepted() {
        assertFalse(MusicFlowUpdates.isOfficialSavedRelease(
            "v0.7.50-radio-artwork-test",
            "https://github.com/smoc1983-web/MusicFlow-/releases/download/v0.7.50-radio-artwork-test/MusicFlow-RadioArtwork-TEST-0.7.50.apk"))
        assertTrue(MusicFlowUpdates.isOfficialSavedRelease(
            "v0.7.50",
            "https://github.com/smoc1983-web/MusicFlow-/releases/download/v0.7.50/MusicFlow_0.7.50.apk"))
    }
}
