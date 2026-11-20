package com.musicflow.app

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
internal class MusicPlaybackRecovery(
    private val player: ExoPlayer,
    private val scope: CoroutineScope
) : Player.Listener {
    private val attempted = mutableSetOf<String>()
    private var retriedCurrent = false
    private var searchedAgain = false
    private var replacement: MediaItem? = null
    private var pending: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem === replacement && replacement != null) return
        pending?.cancel()
        attempted.clear()
        retriedCurrent = false
        searchedAgain = false
    }

    override fun onPlayerError(error: PlaybackException) {
        val failed = player.currentMediaItem ?: return
        val extras = failed.mediaMetadata.extras ?: return
        val streams = decodeStreams(extras.getString(MUSIC_STREAMS))
        val uri = failed.localConfiguration?.uri ?: return
        val active = streams.firstOrNull { it.uri == uri.toString() } ?: return
        val status = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        val temporary = status == 408 || status == 429 || (status != null && status in 500..599) ||
            error.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            )
        val refreshZaycev = uri.scheme == "zaycev" && status in setOf(403, 404, 410)
        AppDiagnostics.info("Recovery",
            "source=${active.source} code=${error.errorCodeName} http=${status ?: 0}")
        attempted.add(active.uri)
        pending?.cancel()
        pending = scope.launch {
            if (!retriedCurrent && (temporary || refreshZaycev)) {
                retriedCurrent = true
                delay(850)
                if (player.currentMediaItem !== failed || player.playerError !== error) return@launch
                if (refreshZaycev) ZaycevStreamResolver.invalidate(uri)
                AppDiagnostics.info("Recovery", "retry source=${active.source}")
                player.prepare()
                return@launch
            }
            var available = streams
            var candidate = available.firstOrNull { it.uri !in attempted }
            if (candidate == null && !searchedAgain) {
                searchedAgain = true
                val matches = findRecording(
                    failed.mediaMetadata.artist?.toString().orEmpty(),
                    failed.mediaMetadata.title?.toString().orEmpty(),
                    extras.getLong(MUSIC_DURATION_SECONDS)
                )
                val blocked = status in setOf(401, 451) ||
                    error.errorCodeName.startsWith("ERROR_CODE_DRM")
                val refreshed = matches.flatMap { it.streams() }
                    .filter { !blocked || it.source != active.source }
                available = (streams + refreshed).distinctBy { it.uri }
                candidate = refreshed.firstOrNull { it.uri !in attempted }
            }
            if (player.currentMediaItem !== failed || player.playerError !== error) return@launch
            if (candidate == null || attempted.size >= 6) {
                AppDiagnostics.warning("Recovery", "no matching playable stream remains")
                return@launch
            }

            val selected = candidate
            val index = player.currentMediaItemIndex
            val resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            val updated = failed.buildUpon().setUri(selected.uri)
                .setMediaMetadata(failed.mediaMetadata.buildUpon().setExtras(Bundle(extras).apply {
                    putString(MUSIC_STREAMS, encodeStreams(
                        (listOf(selected) + available).distinctBy { it.uri }.take(12)
                    ))
                    putString(MUSIC_SOURCE, selected.source)
                }).build()).build()
            replacement = updated
            retriedCurrent = false
            try {
                player.replaceMediaItem(index, updated)
                player.seekTo(index, resumePositionMs)
                player.prepare()
                AppDiagnostics.info("Recovery", "switched source=${selected.source}")
            } finally {
                replacement = null
            }
        }
    }
}
