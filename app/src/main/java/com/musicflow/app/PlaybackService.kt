package com.musicflow.app

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import java.util.Locale
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.initialize(this)
        AppDiagnostics.info("PlaybackService", "service started")
        val upstream = DefaultDataSource.Factory(this)
        val resolvedDataSource = ResolvingDataSource.Factory(upstream) { spec ->
            val host = spec.uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (spec.uri.scheme == "zaycev") {
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
            setHandleAudioBecomingNoisy(true)
        }
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val kind = player.currentMediaItem?.mediaId.orEmpty()
                    .substringBefore(":").take(24)
                AppDiagnostics.failure(
                    "Playback", "Media3 " + error.errorCodeName + " source=" + kind, error
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
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        AppDiagnostics.info("PlaybackService", "service stopped")
        super.onDestroy()
    }
}
