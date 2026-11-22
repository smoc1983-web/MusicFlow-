package com.musicflow.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaLampaBridgeTest {
    @Test fun officialLampaWebOriginAllowedButUntrustedNavigationsBlocked() {
        assertTrue(CinemaLampaBridge.isAllowedNavigation("https://yumata.github.io/lampa/", false))
        assertTrue(CinemaLampaBridge.isAllowedNavigation("https://yumata.github.io/lampa/#settings", false))
        assertFalse(CinemaLampaBridge.isAllowedNavigation("https://evil.example/lampa/", false))
        assertFalse(CinemaLampaBridge.isAllowedNavigation("http://lampa.mx/", false))
        assertTrue(CinemaLampaBridge.isAllowedNavigation("http://lampa.mx/", true))
        assertFalse(CinemaLampaBridge.isAllowedNavigation("file:///android_asset/secret", true))
    }

    @Test fun allowsExplicitLampaJsUrlButBlocksLocalOrCredentialedOnes() {
        assertTrue(CinemaLampaBridge.allowedPluginUrl("http://bwa.ad/rc"))
        assertTrue(CinemaLampaBridge.allowedPluginUrl("https://example.com/official-plugin.js"))
        assertFalse(CinemaLampaBridge.allowedPluginUrl("http://localhost/demo.js"))
        assertFalse(CinemaLampaBridge.allowedPluginUrl("https://192.168.0.1/test.js"))
        assertFalse(CinemaLampaBridge.allowedPluginUrl("https://username:pass@example.com/test.js"))
        assertFalse(CinemaLampaBridge.allowedPluginUrl("javascript:alert('hello')"))
    }

    @Test fun urlInputIsQuotedAsDataForUpstreamLampaPluginsApi() {
        val attacker = "https://example.com/p.js');alert(1);//"
        val javascript = CinemaLampaBridge.installScript(attacker)
        assertTrue(javascript.contains("Lampa.Plugins.add"))
        assertTrue(javascript.contains("Lampa.Plugins.get"))
        assertFalse(javascript.contains("addJavascriptInterface"))
        // The JS injection is a quoted JSON literal, not executable splice.
        assertFalse(javascript.contains("var address = 'https://"))
    }

    @Test fun builtinOfflineClipIsNotPermittedAsExternalPluginUri() {
        val url = CinemaLabSources.LOCAL_TEST_VIDEO
        assertTrue(CinemaLabSources.isPlayableUri(CinemaLabSources.DEMO_SOURCE, url))
        assertFalse(CinemaLabSources.isPlayableUri("untrusted-plugin", url))
    }
}
