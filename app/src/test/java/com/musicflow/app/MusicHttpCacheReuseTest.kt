package com.musicflow.app

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicHttpCacheReuseTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun repeatedPublicAudioUsesCacheButNoStoreNeverDoes() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            Cache(temp.newFolder(), 1024L * 1024).use { cache ->
                val client = OkHttpClient.Builder().cache(cache)
                    .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                    .addNetworkInterceptor(MusicHttpCacheInterceptor)
                    .build()
                fun reply(control: String) = MockResponse().setResponseCode(200)
                    .setHeader("Cache-Control", control).setHeader("Content-Type", "audio/mpeg")
                    .setBody("audio-test-bytes")
                fun read(path: String): String = client.newCall(
                    Request.Builder().url(server.url(path)).build()).execute().use {
                    assertTrue(it.isSuccessful)
                    it.body!!.string()
                }
                server.enqueue(reply("public, max-age=3600"))
                server.enqueue(reply("public, max-age=3600"))
                assertEquals("audio-test-bytes", read("/track.mp3"))
                assertEquals("audio-test-bytes", read("/track.mp3"))
                assertEquals(1, server.requestCount)
                // Clear the unused fallback response with a separate server-independent path:
                // consume it as a unique public request before the private test.
                assertEquals("audio-test-bytes", read("/second.mp3"))
                server.enqueue(reply("no-store"))
                server.enqueue(reply("no-store"))
                assertEquals("audio-test-bytes", read("/private.mp3"))
                assertEquals("audio-test-bytes", read("/private.mp3"))
                assertEquals(4, server.requestCount)
            }
        }
    }
}
