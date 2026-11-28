package com.musicflow.app

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.util.Locale
import kotlinx.coroutines.runBlocking

/** One playback resolver for the main service and independent diagnostic player. */
@OptIn(UnstableApi::class)
internal fun musicPlaybackSourceFactory(context: Context): DefaultMediaSourceFactory {
    val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(TvPlaybackTuning.CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(TvPlaybackTuning.READ_TIMEOUT_MS)
    val upstream = DefaultDataSource.Factory(context, http)
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
    return DefaultMediaSourceFactory(resolvedDataSource)
}
