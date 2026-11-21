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
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(UnstableApi::class)
internal class MusicPlaybackRecovery(
    private val player: ExoPlayer,
    private val scope: CoroutineScope
) : Player.Listener {
    private val attempted = mutableSetOf<String>()
    private val blockedSources = mutableSetOf<String>()
    private var retriedCurrent = false
    private var searchedAgain = false
    // Carry a token in the MediaItem itself. Media3 may recreate item instances
    // while changing the URI, so reference identity is not a reliable guard.
    private val recoveryTokenKey = "com.musicflow.recovery_transition"
    private var nextRecoveryToken = 0
    private var pendingRecoveryToken: Int? = null
    private var playlistGeneration = 0
    private var pending: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val token = mediaItem?.mediaMetadata?.extras?.getInt(recoveryTokenKey, 0) ?: 0
        if (pendingRecoveryToken != null && token == pendingRecoveryToken) {
            // This transition belongs to our own fallback. Keep failed URLs
            // and blocked providers so the next failure cannot loop to them.
            pendingRecoveryToken = null
            return
        }
        pendingRecoveryToken = null
        playlistGeneration++
        pending?.cancel()
        attempted.clear()
        blockedSources.clear()
        retriedCurrent = false
        searchedAgain = false
    }

    override fun onPlayerError(error: PlaybackException) {
        val failed = player.currentMediaItem ?: return
        val extras = failed.mediaMetadata.extras ?: return
        val streams = decodeStreams(extras.getString(MUSIC_STREAMS))
        val uri = failed.localConfiguration?.uri ?: return
        val active = streams.firstOrNull { it.uri == uri.toString() } ?: return
        // Media3 can create a fresh MediaItem wrapper during a retry. Compare
        // the logical queue generation and track identity, not object identity.
        val generation = playlistGeneration
        val wasPlaying = player.playWhenReady
        fun stillCurrent() = playlistGeneration == generation &&
            player.currentMediaItem?.mediaId == failed.mediaId &&
            player.currentMediaItem?.localConfiguration?.uri == uri
        val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
        val status = causes.firstNotNullOfOrNull {
            when (it) {
                is HttpDataSource.InvalidResponseCodeException -> it.responseCode
                is ProviderHttpException -> it.status
                else -> null
            }
        }
        val temporary = status == 408 || status == 429 || (status != null && status in 500..599) ||
            error.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ) || causes.any { it is SocketTimeoutException || it is UnknownHostException }
        val refreshZaycev = uri.scheme == "zaycev" && status in setOf(403, 404, 410) &&
            causes.any { it is HttpDataSource.InvalidResponseCodeException }
        AppDiagnostics.info("Recovery",
            "source=${active.source} code=${error.errorCodeName} http=${status ?: 0}")
        attempted.add(active.uri)
        pending?.cancel()
        pending = scope.launch {
            if (!retriedCurrent && (temporary || refreshZaycev)) {
                retriedCurrent = true
                delay(850)
                if (!stillCurrent()) return@launch
                if (refreshZaycev) ZaycevStreamResolver.invalidate(uri)
                AppDiagnostics.info("Recovery", "retry source=${active.source}")
                player.prepare()
                if (wasPlaying) player.play()
                return@launch
            }
            var available = streams
            val blocked = status in setOf(401, 451) ||
                error.errorCodeName.startsWith("ERROR_CODE_DRM")
            if (blocked) blockedSources.add(active.source)
            var candidate = available.firstOrNull { it.uri !in attempted && it.source !in blockedSources }
            if (candidate == null && !searchedAgain) {
                searchedAgain = true
                val matches = findRecording(
                    failed.mediaMetadata.artist?.toString().orEmpty(),
                    failed.mediaMetadata.title?.toString().orEmpty(),
                    extras.getLong(MUSIC_DURATION_SECONDS)
                )
                val refreshed = matches.flatMap { it.streams() }
                    .filter { it.source !in blockedSources }
                available = (streams + refreshed).distinctBy { it.uri }
                candidate = refreshed.firstOrNull { it.uri !in attempted }
            }
            if (!stillCurrent()) return@launch
            if (candidate == null || attempted.size >= 6) {
                AppDiagnostics.warning("Recovery", "no matching playable stream remains")
                // An unavailable item must not stop the entire Zaycev collection.
                // Keep this limited to an actual next item: never wrap endlessly.
                val nextIndex = player.currentMediaItemIndex + 1
                if (extras.getBoolean(MUSIC_HOME_PLAYLIST, false) &&
                    nextIndex in 1 until player.mediaItemCount) {
                    AppDiagnostics.warning("Recovery", "skipping unavailable collection entry")
                    player.seekTo(nextIndex, 0L)
                    player.prepare()
                    if (wasPlaying) player.play()
                }
                return@launch
            }

            val selected = candidate
            val index = player.currentMediaItemIndex
            val resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            val transitionToken = ++nextRecoveryToken
            val updated = failed.buildUpon().setUri(selected.uri)
                .setMediaMetadata(failed.mediaMetadata.buildUpon().setExtras(Bundle(extras).apply {
                    putString(MUSIC_STREAMS, encodeStreams(
                        (listOf(selected) + available).distinctBy { it.uri }.take(12)
                    ))
                    putString(MUSIC_SOURCE, selected.source)
                    putInt(recoveryTokenKey, transitionToken)
                }).build()).build()
            pendingRecoveryToken = transitionToken
            retriedCurrent = false
            try {
                player.replaceMediaItem(index, updated)
                player.seekTo(index, resumePositionMs)
                player.prepare()
                if (wasPlaying) player.play()
                AppDiagnostics.info("Recovery", "switched source=${selected.source}")
            } catch (failure: Exception) {
                pendingRecoveryToken = null
                AppDiagnostics.failure("Recovery", "fallback playlist update", failure)
            }
        }
    }
}
