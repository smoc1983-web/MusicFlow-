package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class MusicFlowTestUpdateChannelTest {
    private fun release(tag: String, prerelease: Boolean, asset: String): String = """
        {
          "tag_name":"$tag",
          "draft":false,
          "prerelease":$prerelease,
          "body":"TEST channel",
          "assets":[{
            "name":"$asset", "size":22900000,
            "browser_download_url":"https://github.com/smoc1983-web/MusicFlow-/releases/download/$tag/$asset"
          }]
        }
    """.trimIndent()

    @Test fun stableTestChannelRejectsOfficialAndOtherTestVariants() {
        assertTrue(MusicFlowUpdates.isTestTag("v0.7.54-test"))
        assertFalse(MusicFlowUpdates.isTestTag("v0.7.54"))
        assertFalse(MusicFlowUpdates.isTestTag("v0.7.53-downloads-test"))
        assertFalse(MusicFlowUpdates.isTestTag("v0.7.50-radio-artwork-test"))
        assertTrue(MusicFlowUpdates.isTestApkName("v0.7.54-test", "MusicFlow-TEST_0.7.54.apk"))
        assertFalse(MusicFlowUpdates.isTestApkName("v0.7.54-test", "MusicFlow_0.7.54.apk"))
        assertFalse(MusicFlowUpdates.isTestApkName("v0.7.54-test", "MusicFlow-TEST_0.7.53.apk"))
    }

    @Test fun correctPrereleaseIsSelectedWithoutContaminatingOfficialFeed() {
        val data = "[" +
            release("v0.7.54", false, "MusicFlow_0.7.54.apk") + "," +
            release("v0.7.54-test", true, "MusicFlow-TEST_0.7.54.apk") + "," +
            release("v0.7.53-downloads-test", true, "MusicFlow-Downloads-TEST-0.7.53.apk") + "]"
        val test = MusicFlowUpdates.parseTestReleases(data)
        assertEquals("v0.7.54-test", test?.version)
        assertEquals(754L, test?.versionCode)
        assertEquals("v0.7.54", MusicFlowUpdates.parseOfficialReleases(data)?.version)
    }

    @Test fun noOfferWhenTestIsNotMarkedPrereleaseOrHasWrongApk() {
        assertNull(MusicFlowUpdates.parseTestReleases("[" +
            release("v0.7.54-test", false, "MusicFlow-TEST_0.7.54.apk") + "]"))
        assertNull(MusicFlowUpdates.parseTestReleases("[" +
            release("v0.7.54-test", true, "MusicFlow_0.7.54.apk") + "]"))
    }

    @Test fun oldRadioArtworkPackageIsNotAutoUpgradableToDev() {
        assertEquals(MusicFlowUpdates.UpdateChannel.UNSUPPORTED,
            MusicFlowUpdates.channelForPackage("com.musicflow.preview.radioarttest50"))
        assertEquals(MusicFlowUpdates.UpdateChannel.TEST,
            MusicFlowUpdates.channelForPackage("com.musicflow.preview.dev"))
        assertEquals(MusicFlowUpdates.UpdateChannel.OFFICIAL,
            MusicFlowUpdates.channelForPackage("com.musicflow.preview"))
        assertTrue(MusicFlowUpdates.isTestSavedRelease("v0.7.54-test",
            "https://github.com/smoc1983-web/MusicFlow-/releases/download/v0.7.54-test/MusicFlow-TEST_0.7.54.apk"))
        assertFalse(MusicFlowUpdates.isTestSavedRelease("v0.7.54-test",
            "https://github.com/smoc1983-web/MusicFlow-/releases/download/v0.7.54-test/MusicFlow_0.7.54.apk"))
    }

    @Test fun higherVersionCodeRequiredForTestUpdatesToo() {
        assertTrue(MusicFlowUpdatePolicy.isNewer(755L, 754L))
        assertFalse(MusicFlowUpdatePolicy.isNewer(754L, 754L))
        assertFalse(MusicFlowUpdatePolicy.isNewer(753L, 754L))
    }
}
