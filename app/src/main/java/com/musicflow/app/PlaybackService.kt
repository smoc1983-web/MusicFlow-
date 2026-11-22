package com.musicflow.app

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null
    private var audioEqualizer: NativeAudioEqualizer? = null
    private var journal: PlaybackJournal? = null

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.initialize(this)
        AppDiagnostics.info("PlaybackService", "service started")
        val upstream = DefaultDataSource.Factory(this)
        val resolvedDataSource = ResolvingDataSource.Factory(upstream) { spec ->
            val host = spec.uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (spec.uri.scheme == "chart") {
                // The playback loader (not Compose UI) requests exactly one
                // deferred chart entry when Next/Previous or autoplay reaches it.
                val request = ChartPlaylistQueue.parse(spec.uri.toString())
                    ?: throw java.io.IOException("Invalid chart track")
                val track = runBlocking(Dispatchers.IO) {
                    findRecording(request.artist, request.title).firstOrNull {
                        recordingMatches(it, request.artist, request.title)
                    }
                } ?: throw java.io.IOException("Unavailable chart audio")
                val chosen = android.net.Uri.parse(track.stream)
                val playbackUri = when (chosen.scheme) {
                    "zaycev" -> {
                        if (!Regex("""zaycev://track/\d{1,16}""").matches(track.stream))
                            throw java.io.IOException("Invalid Zaycev chart stream")
                        ZaycevStreamResolver.resolve(chosen)
                    }
                    "https", "http" -> {
                        if (!RussianRadioDirectory.isPublicStream(track.stream))
                            throw java.io.IOException("Nonpublic chart audio host")
                        chosen
                    }
                    else -> throw java.io.IOException("Unsupported chart stream")
                }
                val headers = when (track.id.substringBefore(':')) {
                    "zaycev" -> mapOf("Referer" to "https://zaycev.net/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/130.0")
                    "muzofond" -> mapOf("Referer" to "https://muzofond.fm/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/130.0",
                        "Accept-Language" to "ru-RU,ru;q=0.9")
                    "mailru" -> mapOf("Referer" to "https://my.mail.ru/music/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/130.0",
                        "Accept-Language" to "ru-RU,ru;q=0.9")
                    else -> emptyMap()
                }
                spec.withUri(playbackUri).withAdditionalHeaders(headers)
            } else if (spec.uri.scheme == "zaycev") {
                val real = ZaycevStreamResolver.resolve(spec.uri)
                spec.withUri(real).withAdditionalHeaders(
                    mapOf(
                        "Referer" to "https://zaycev.net/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
                    )
                )
            } else if (host == "zaycev.net" || host.endsWith(".zaycev.net")) {
                spec.withAdditionalHeaders(
                    mapOf("Referer" to "https://zaycev.net/")
                )
            } else if (host == "muzofond.fm" || host.endsWith(".muzofond.fm")) {
                spec.withAdditionalHeaders(
                    mapOf(
                        "Referer" to "https://muzofond.fm/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36",
                        "Accept-Language" to "ru-RU,ru;q=0.9"
                    )
                )
            } else if (host == "moosic.my.mail.ru" || host.endsWith(".moosic.my.mail.ru")) {
                spec.withAdditionalHeaders(
                    mapOf(
                        "Referer" to "https://my.mail.ru/music/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36",
                        "Accept-Language" to "ru-RU,ru;q=0.9"
                    )
                )
            } else spec
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(resolvedDataSource)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            // External audio effects must work on the decoded PCM path.
            // Hardware offload can bypass Equalizer/LoudnessEnhancer when routing
            // audio to Bluetooth headphones and cause sound to disappear.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences
                                .AUDIO_OFFLOAD_MODE_DISABLED
                        ).build()
                ).build()
            // ExoPlayer retains wake and Wi-Fi locks while background audio is active.
            // Do not hold either lock after pause or playback stops.
            setWakeMode(C.WAKE_MODE_NETWORK)
            setHandleAudioBecomingNoisy(true)
        }
        val eq = NativeAudioEqualizer(this)
        audioEqualizer = eq
        player.addListener(eq)
        val playbackJournal = PlaybackJournal(this, player)
        journal = playbackJournal
        playbackJournal.restore()
        player.addListener(playbackJournal)
        recoveryScope.launch {
            while (isActive) {
                delay(5_000L)
                playbackJournal.persist()
            }
        }
        // Only this playback service acts on the sleep deadline.
        recoveryScope.launch {
            while (isActive) {
                val deadline = MusicFlowSleepTimerStore.deadlineMs(this@PlaybackService)
                if (SleepTimerPolicy.isExpired(deadline, System.currentTimeMillis())) {
                    MusicFlowSleepTimerStore.cancel(this@PlaybackService)
                    player.pause()
                    playbackJournal.persist()
                    AppDiagnostics.info("SleepTimer", "Playback paused at deadline")
                }
                delay(1_000L)
            }
        }
        player.addListener(MusicPlaybackRecovery(player, recoveryScope))
        // Live TV needs its own bounded recovery; audio/music rules are unrelated.
        player.addListener(TvStreamRecovery(player, recoveryScope))
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val kind = player.currentMediaItem?.mediaId?.substringBefore(':')
                    ?: player.currentMediaItem?.mediaMetadata?.extras?.getString(MUSIC_SOURCE)
                    ?: "other"
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.responseCode
                AppDiagnostics.failure(
                    "Playback", "Media3 " + error.errorCodeName +
                        " source=" + kind + (http?.let { " HTTP " + it } ?: ""), error
                )
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val kind = mediaItem?.mediaId.orEmpty().substringBefore(":").take(24)
                AppDiagnostics.info("Playback", "media item changed; source=" + kind +
                    "; reason=" + reason)
            }
            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                AppDiagnostics.info("Playback", "state=" + name)
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        journal?.persist()
        journal = null
        recoveryScope.cancel()
        audioEqualizer?.release()
        audioEqualizer = null
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        AppDiagnostics.info("PlaybackService", "service stopped")
        super.onDestroy()
    }
}
