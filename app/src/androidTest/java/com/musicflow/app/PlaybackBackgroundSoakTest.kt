package com.musicflow.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real emulator media-service soak, not a mocked player. A locally generated
 * four-minute PCM wave permits deterministic, network-independent playback.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackBackgroundSoakTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app: Context = instrumentation.targetContext
    private val testMediaId = "soak:background-4min"

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try { result.set(block()) } catch (t: Throwable) { error.set(t) }
        }
        error.get()?.let { throw AssertionError("Media3 main-thread access failed", it) }
        return result.get()
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(500L)
        }
        return condition()
    }

    private fun createLocalWave(): File {
        val rate = 8000
        val seconds = 240
        val dataLength = rate * 2 * seconds
        val file = File(app.cacheDir, "musicflow-background-soak.wav")
        FileOutputStream(file).buffered().use { out ->
            fun putInt(n: Int) {
                out.write(n and 255)
                out.write((n ushr 8) and 255)
                out.write((n ushr 16) and 255)
                out.write((n ushr 24) and 255)
            }
            fun putShort(n: Int) {
                out.write(n and 255)
                out.write((n ushr 8) and 255)
            }
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataLength)
            out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(rate)
            putInt(rate * 2)
            putShort(2)
            putShort(16)
            out.write("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLength)
            val chunk = ByteArray(8_000)
            repeat(dataLength / chunk.size) { out.write(chunk) }
        }
        return file
    }

    @Test fun mediaSessionKeepsPlayingForTwoMinutesWithActivityInBackground() {
        MusicFlowSleepTimerStore.cancel(app)
        app.getSharedPreferences("musicflow_player_state_v1", Context.MODE_PRIVATE)
            .edit().remove("resume_" + testMediaId)
            .remove("length_" + testMediaId)
            .remove("seekable_" + testMediaId).commit()
        val wave = createLocalWave()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = main { MediaController.Builder(app, token).buildAsync() }
        try {
            val controller = future.get(30L, TimeUnit.SECONDS)
            instrumentation.startActivitySync(Intent(app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            main {
                controller.setMediaItem(MediaItem.Builder()
                    .setMediaId(testMediaId)
                    .setUri(Uri.fromFile(wave))
                    .build())
                controller.prepare()
                controller.play()
            }
            assertTrue("Media3 player never reached playing state",
                waitFor(30_000L) { main { controller.isPlaying } })
            assertTrue("Failed to move MusicFlow into background",
                UiDevice.getInstance(instrumentation).pressHome())
            val backgroundStartedAt = SystemClock.elapsedRealtime()
            Thread.sleep(130_000L) // real wall-clock time; 6-hour synthetic test is separate
            val elapsed = SystemClock.elapsedRealtime() - backgroundStartedAt
            val isPlaying = main { controller.isPlaying }
            val positionMs = main { controller.currentPosition }
            val error = main { controller.playerError }
            val saved = app.getSharedPreferences(
                "musicflow_player_state_v1", Context.MODE_PRIVATE
            ).getLong("resume_" + testMediaId, 0L)
            assertTrue("Emulator was not in background for the soak interval", elapsed >= 125_000L)
            assertNull("Media3 crashed or failed while app was in background", error)
            assertTrue("Playback stopped when app went to background", isPlaying)
            assertTrue("Audio position did not advance in background: $positionMs", positionMs >= 95_000L)
            assertTrue("Background service did not persist position: $saved", saved >= 90_000L)
            assertTrue("Unknown metadata length did not recover saved position",
                PlaybackJournal.resumePosition(app, testMediaId, 0L) >= 90_000L)
            println("MUSICFLOW_BACKGROUND_SOAK_PASS elapsed=$elapsed mediaPosition=$positionMs saved=$saved")
        } finally {
            runCatching {
                val controller = future.get(2L, TimeUnit.SECONDS)
                main { controller.stop(); controller.clearMediaItems() }
            }
            MediaController.releaseFuture(future)
            wave.delete()
        }
    }
}
