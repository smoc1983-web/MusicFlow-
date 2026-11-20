package com.musicflow.app

import androidx.media3.common.AudioAttributes
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
        val upstream = DefaultDataSource.Factory(this)
        val resolvedDataSource = ResolvingDataSource.Factory(upstream) { spec ->
            val host = spec.uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (host == "muzofond.fm" || host.endsWith(".muzofond.fm")) {
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
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
