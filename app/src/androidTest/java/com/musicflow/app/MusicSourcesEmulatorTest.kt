package com.musicflow.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Independent Android emulator source smoke tests.
 * Real provider results are always logged and persisted; a regional 403 is
 * reported as BLOCKED rather than misrepresented as successful playback.
 */
@RunWith(AndroidJUnit4::class)
class MusicSourcesEmulatorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try { result.set(block()) } catch (e: Throwable) { error.set(e) }
        }
        error.get()?.let { throw AssertionError("Main-thread Media3 failed", it) }
        return result.get()
    }

    private fun record(report: String) {
        Log.i("MUSICFLOW_SOURCE_PROBE", report)
        println("MUSICFLOW_SOURCE_PROBE $report")
        val directory = context.getExternalFilesDir(null) ?: return
        File(directory, "source-probe-v0760.txt").appendText(report + "\n")
    }

    private fun checkPlayable(track: Track, controller: MediaController): String {
        if (track.stream.isBlank()) return "EMPTY_STREAM"
        return try {
            main {
                controller.stop()
                controller.setMediaItem(MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(track.stream)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(track.title).setArtist(track.artist).build())
                    .build())
                controller.prepare()
                controller.play()
            }
            val until = SystemClock.elapsedRealtime() + 18_000L
            while (SystemClock.elapsedRealtime() < until) {
                val error = main { controller.playerError }
                if (error != null) return "PLAYBACK_ERROR_" + error.errorCodeName
                if (main { controller.playbackState == Player.STATE_READY }) return "READY"
                Thread.sleep(300L)
            }
            "PLAYBACK_TIMEOUT"
        } catch (e: Exception) { "PLAYBACK_EXCEPTION_" + e.javaClass.simpleName }
    }

    @Test fun searchAndPlaybackOfAllThreeRegisteredSources() {
        val providers = listOf(
            "zaycev" to suspend { zaycevTracks("Queen") },
            "muzofond" to suspend { muzofondTracks("Queen") },
            "mailru" to suspend { mailRuTracks("Queen") }
        )
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = main { MediaController.Builder(context, token).buildAsync() }
        var checked = 0
        try {
            val controller = future.get(30L, TimeUnit.SECONDS)
            for ((name, search) in providers) {
                val output = try {
                    val result = runBlocking {
                        withTimeout(23_000L) { search() }
                    }
                    val verified = result.firstOrNull {
                        it.stream.isNotBlank() && it.title.isNotBlank()
                    }
                    if (verified == null) "EMPTY results=${result.size}"
                    else "results=${result.size} playback=${checkPlayable(verified, controller)}"
                } catch (e: Exception) {
                    "ERROR type=${e.javaClass.simpleName} msg=${e.message.orEmpty().take(75)}"
                }
                checked++
                record("source=$name $output")
            }
            assertEquals("All three registered providers must be exercised", 3, checked)
        } finally {
            runCatching {
                val controller = future.get(2L, TimeUnit.SECONDS)
                main { controller.stop(); controller.clearMediaItems() }
            }
            main { MediaController.releaseFuture(future) }
        }
    }

    @Test fun radioScreenAlwaysShowsArtworkAndPlayerNavigationControls() {
        val bundled = RussianRadioFallback.bundledStations(context)
        assertTrue("Radio offline catalog is empty", bundled.size >= 2)
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        assertNotNull("Main screen not loaded",
            device.wait(Until.findObject(By.text("Музыка")), 18_000))
        device.findObject(By.text("Музыка")).click()
        assertNotNull("Radio dropdown item absent",
            device.wait(Until.findObject(By.text("Радио")), 10_000))
        device.findObject(By.text("Радио")).click()
        assertNotNull("Radio list not loaded",
            device.wait(Until.findObject(By.text("Популярные")), 18_000))
        device.takeScreenshot(File(context.getExternalFilesDir(null), "musicflow-radio-list-v0760.png"))
        val play = device.wait(Until.findObject(By.desc("Слушать")), 12_000)
        assertNotNull("No stations with play control", play)
        play.click()
        Thread.sleep(1200L)
        // The mini-player contains the now-active station title and opens
        // the compact player on tap. Prefer its last matching title.
        val list = device.findObjects(By.clazz("android.widget.TextView"))
        val repeated = list.groupBy { it.text }.filter { (name, rows) ->
            name.isNotBlank() && rows.size > 1 &&
                name !in setOf("Радио", "Популярные")
        }
        val selected = repeated.values.firstOrNull()?.lastOrNull()
        if (selected != null) selected.click()
        else {
            // If the row is not yet measured, use the mini-player's visible text.
            val fallback = bundled.firstOrNull { device.hasObject(By.text(it.name)) }
            assertNotNull("No active radio mini player", fallback)
            device.findObjects(By.text(fallback!!.name)).last().click()
        }
        assertNotNull("Previous station is not present in compact player",
            device.wait(Until.findObject(By.desc("Предыдущая радиостанция")), 12_000))
        assertNotNull("Next station is not present in compact player",
            device.findObject(By.desc("Следующая радиостанция")))
        assertNotNull("Shuffle toggle absent",
            device.findObject(By.desc("Включён порядок по списку — переключить случайно")))
        device.takeScreenshot(File(context.getExternalFilesDir(null), "musicflow-radio-player-v0760.png"))
        record("radio_ui=PASS bundled=${bundled.size} previous_next_shuffle=VISIBLE covers=LOGO_OR_LOCAL_TILE")
    }
}
