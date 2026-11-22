package com.musicflow.app

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Bounded recovery of unstable live IPTV streams; never retries permanent 4xx errors. */
internal object TvStreamRetryPolicy {
    const val MAX_RETRIES = 2
    const val BUFFERING_TIMEOUT_MS = 65_000L
    const val STALL_TIMEOUT_MS = 30_000L

    fun isLiveTv(mediaId: String?): Boolean = mediaId?.startsWith("ruvideo:") == true

    fun retryHttpStatus(httpStatus: Int?): Boolean =
        httpStatus == 408 || httpStatus == 429 || (httpStatus != null && httpStatus in 500..599)

    fun retryError(errorCode: Int, httpStatus: Int?): Boolean {
        if (httpStatus != null) return retryHttpStatus(httpStatus)
        return errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    fun canRetry(mediaId: String?, attempts: Int, playRequested: Boolean) =
        isLiveTv(mediaId) && playRequested && attempts < MAX_RETRIES
}

/**
 * Restart a stalled IPTV stream only a bounded number of times.
 * Music streams are deliberately excluded from all TV watchdogs.
 * Keeps the same MediaItem, so fullscreen/mini-player surface handoff is preserved.
 */
@OptIn(UnstableApi::class)
internal class TvStreamRecovery(
    private val player: ExoPlayer,
    private val scope: CoroutineScope
) : Player.Listener {
    private var attempts = 0
    private var generation = 0
    private var bufferWatchdog: Job? = null
    private var retryJob: Job? = null
    private var positionMonitor: Job? = null

    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
        generation++
        attempts = 0
        bufferWatchdog?.cancel()
        retryJob?.cancel()
        positionMonitor?.cancel()
        if (TvStreamRetryPolicy.isLiveTv(item?.mediaId)) {
            armPositionMonitor()
            if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                armBufferWatchdog()
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (!isTv()) {
            cancelWatchdogs()
            return
        }
        if (state == Player.STATE_BUFFERING && player.playWhenReady) armBufferWatchdog()
        else {
            bufferWatchdog?.cancel()
            bufferWatchdog = null
        }
        if (state == Player.STATE_ENDED && player.playWhenReady) {
            requestRetry("live playlist ended")
        }
        if (state == Player.STATE_READY) armPositionMonitor()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            cancelWatchdogs()
        } else if (isTv()) {
            armPositionMonitor()
            if (player.playbackState == Player.STATE_BUFFERING) armBufferWatchdog()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!isTv()) return
        val http = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        if (TvStreamRetryPolicy.retryError(error.errorCode, http)) {
            requestRetry("temporary IO " + error.errorCodeName +
                (http?.let { " HTTP " + it } ?: ""))
        } else {
            AppDiagnostics.warning("TV", "live stream unavailable; " +
                error.errorCodeName + (http?.let { " HTTP " + it } ?: ""))
        }
    }

    private fun isTv() =
        TvStreamRetryPolicy.isLiveTv(player.currentMediaItem?.mediaId)

    private fun cancelWatchdogs() {
        bufferWatchdog?.cancel()
        bufferWatchdog = null
        positionMonitor?.cancel()
        positionMonitor = null
        retryJob?.cancel()
        retryJob = null
    }

    private fun armBufferWatchdog() {
        if (bufferWatchdog?.isActive == true || !isTv()) return
        val token = generation
        bufferWatchdog = scope.launch {
            delay(TvStreamRetryPolicy.BUFFERING_TIMEOUT_MS)
            if (token == generation && player.playbackState == Player.STATE_BUFFERING &&
                player.playWhenReady && player.playerError == null && isTv()) {
                requestRetry("buffering exceeded " +
                    TvStreamRetryPolicy.BUFFERING_TIMEOUT_MS / 1_000 + "s")
            }
        }
    }

    private fun armPositionMonitor() {
        if (positionMonitor?.isActive == true || !isTv()) return
        val token = generation
        positionMonitor = scope.launch {
            var previous = player.currentPosition
            var stalledForMs = 0L
            while (isActive && token == generation && isTv()) {
                delay(2_000L)
                if (token != generation || !isTv()) break
                val now = player.currentPosition
                if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                    stalledForMs = if (now <= previous + 250L) stalledForMs + 2_000L else 0L
                    if (stalledForMs >= TvStreamRetryPolicy.STALL_TIMEOUT_MS) {
                        requestRetry("live playhead stopped advancing")
                        stalledForMs = 0L
                    }
                } else stalledForMs = 0L
                previous = now
            }
        }
    }

    private fun requestRetry(reason: String) {
        if (retryJob?.isActive == true || !TvStreamRetryPolicy.canRetry(
                player.currentMediaItem?.mediaId, attempts, player.playWhenReady)) return
        attempts++
        val attempt = attempts
        val token = generation
        AppDiagnostics.warning("TV", "auto-reconnect " + attempt + "/" +
            TvStreamRetryPolicy.MAX_RETRIES + "; " + reason)
        bufferWatchdog?.cancel()
        bufferWatchdog = null
        retryJob = scope.launch {
            delay(if (attempt == 1) 1_200L else 3_000L)
            if (token != generation || !isTv() || !player.playWhenReady) return@launch
            // Restart the existing live item. No seeks into VOD or provider switching.
            player.seekToDefaultPosition()
            player.prepare()
            player.play()
        }
    }
}
