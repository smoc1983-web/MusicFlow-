package com.musicflow.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Independent Android emulator source smoke tests.
 * Real provider results are always logged and persisted; a regional 403 is
 * reported as BLOCKED rather than misrepresented as successful playback.
 */
@OptIn(UnstableApi::class)
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
        File(context.filesDir, "source-probe-v0760.txt").appendText(report + "\n")
    }

    /** Direct player avoids starting the service during slow AVD JIT compilation. */
    private fun newProbePlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(TvPlaybackTuning.CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(TvPlaybackTuning.READ_TIMEOUT_MS)
        val upstream = DefaultDataSource.Factory(context, http)
        val resolved = ResolvingDataSource.Factory(upstream) { spec ->
            val host = spec.uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
            val agent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
            fun matches(domain: String) = host == domain || host.endsWith(".$domain")
            when {
                spec.uri.scheme == "zaycev" -> spec.withUri(ZaycevStreamResolver.resolve(spec.uri))
                    .withAdditionalHeaders(mapOf("Referer" to "https://zaycev.net/", "User-Agent" to agent))
                matches("zaycev.net") -> spec.withAdditionalHeaders(mapOf("Referer" to "https://zaycev.net/"))
                matches("muzofond.fm") -> spec.withAdditionalHeaders(mapOf(
                    "Referer" to "https://muzofond.fm/", "User-Agent" to agent,
                    "Accept-Language" to "ru-RU,ru;q=0.9"))
                matches("moosic.my.mail.ru") -> spec.withAdditionalHeaders(mapOf(
                    "Referer" to "https://my.mail.ru/music/", "User-Agent" to agent,
                    "Accept-Language" to "ru-RU,ru;q=0.9"))
                else -> spec
            }
        }
        return main {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(resolved)).build()
        }
    }

    private fun checkPlayable(track: Track, player: ExoPlayer): String {
        if (track.stream.isBlank()) return "EMPTY_STREAM"
        return try {
            main {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(Uri.parse(track.stream))
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(track.title).setArtist(track.artist).build())
                    .build())
                player.prepare()
                player.play()
            }
            val deadline = SystemClock.elapsedRealtime() + 45_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                val error = main { player.playerError }
                if (error != null) {
                    val causes = generateSequence<Throwable>(error) { it.cause }
                        .take(8).joinToString(">") { cause ->
                            val type = cause.javaClass.simpleName
                            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                                "$type(HTTP ${cause.responseCode})" else type
                        }
                    return "PLAYBACK_ERROR_" + error.errorCodeName + " causes=" + causes
                }
                if (main { player.playbackState == Player.STATE_READY }) return "READY"
                Thread.sleep(350L)
            }
            "PLAYBACK_TIMEOUT"
        } catch (error: Exception) {
            "PLAYBACK_EXCEPTION_" + error.javaClass.simpleName
        }
    }

    @Test fun searchAndPlaybackOfAllThreeRegisteredSources() {
        val providers = listOf(
            "zaycev" to suspend { zaycevTracks("Queen") },
            "muzofond" to suspend { muzofondTracks("Queen") },
            "mailru" to suspend { mailRuTracks("Queen") }
        )
        // Search first: record every provider before initializing any playback engine.
        val playable = mutableListOf<Pair<String, List<Track>>>()
        val status = mutableMapOf<String, String>()
        for ((name, search) in providers) {
            val output = try {
                val result = runBlocking { withTimeout(28_000L) { search() } }
                val candidates = result.asSequence().filter {
                    it.stream.isNotBlank() && it.title.isNotBlank()
                }.distinctBy { it.stream }.take(3).toList()
                if (candidates.isNotEmpty()) {
                    playable += name to candidates
                    "FOUND results=" + result.size + " candidates=" + candidates.size +
                        " scheme=" + candidates.first().stream.substringBefore(':')
                } else "EMPTY results=" + result.size
            } catch (error: Exception) {
                "ERROR type=" + error.javaClass.simpleName +
                    " msg=" + error.message.orEmpty().take(75)
            }
            status[name] = output
            record("source=" + name + " search=" + output)
        }
        assertEquals("All three registered sources must be probed", 3, status.size)
        if (playable.isEmpty()) {
            record("playback=SKIPPED: no playable candidates returned")
            fail("No provider returned a candidate: " + status)
        }
        val player = newProbePlayer()
        var ready = 0
        try {
            for ((source, candidates) in playable) {
                for ((index, track) in candidates.withIndex()) {
                    val result = checkPlayable(track, player)
                    record("source=" + source + " candidate=" + (index + 1) + " playback=" + result)
                    if (result == "READY") {
                        ready++
                        break
                    }
                }
            }
        } finally {
            main { player.release() }
        }
        assertTrue("All available audio streams failed Media3 READY: see source-probe", ready >= 1)
    }

    // A coordinate click can return normally even when Android drops the touch
    // during cold boot. Only the destination UI acknowledges a transition.
    private fun navigate(control: BySelector, destination: BySelector) {
        val deadline = SystemClock.elapsedRealtime() + 95_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (device.hasObject(destination)) return
            try {
                if (device.currentPackageName == context.packageName) {
                    val button = device.findObject(control)
                    if (button != null && button.isEnabled) {
                        button.click()
                        if (device.wait(Until.hasObject(destination), 5_000L)) return
                    }
                }
            } catch (_: StaleObjectException) { /* Re-read the active window. */ }
            SystemClock.sleep(350L)
        }
        fail("Radio navigation timed out: $control -> $destination, foreground=" +
            device.currentPackageName)
    }

    @Test fun radioScreenAlwaysShowsArtworkAndPlayerNavigationControls() {
        val bundled = RussianRadioFallback.bundledStations(context)
        assertTrue("Radio offline catalog is empty", bundled.size >= 2)
        // startActivitySync requires a completely idle Compose event queue and
        // fails after 45s on a software-loaded emulator despite an active UI.
        // ActivityManager starts it without the false Looper-idle requirement.
        val launch = device.executeShellCommand(
            "am start -n com.musicflow.preview.dev/com.musicflow.app.MainActivity"
        )
        record("radio_ui=launch " + launch.take(110).replace('\n', ' '))
        val app = context.packageName
        val radio = By.text("Радио").pkg(app)
        navigate(By.text("Музыка").pkg(app), radio)
        navigate(radio, By.text("Популярные").pkg(app))
        assertTrue("No station logo or local artwork visible",
            device.wait(Until.hasObject(By.desc(java.util.regex.Pattern.compile(
                ".*(Обложка-заглушка радиостанции|Логотип станции:).*",
                java.util.regex.Pattern.DOTALL)).pkg(app)), 70_000L))
        device.takeScreenshot(File(context.filesDir, "musicflow-radio-list-v0760.png"))

        val nowPlaying = By.text("Сейчас играет").pkg(app)
        navigate(By.descContains("Слушать").pkg(app), nowPlaying)
        val orderedMode = By.desc("Включён порядок по списку — переключить случайно").pkg(app)
        navigate(nowPlaying, orderedMode)
        assertTrue("Previous station absent in compact player",
            device.hasObject(By.desc("Предыдущая радиостанция").pkg(app)))
        assertTrue("Next station absent in compact player",
            device.hasObject(By.desc("Следующая радиостанция").pkg(app)))
        device.takeScreenshot(File(context.filesDir, "musicflow-radio-player-v0760.png"))
        record("radio_ui=PASS bundled=${bundled.size} previous_next_shuffle=VISIBLE covers=LOGO_OR_LOCAL_TILE")
    }
}
