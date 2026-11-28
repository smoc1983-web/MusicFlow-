package com.musicflow.app

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun diagnosticSourceLabel(source: String): String = when (source) {
    "zaycev" -> "Zaycev.net"
    "muzofond" -> "Muzofond"
    "mailru" -> "Mail.ru"
    else -> source
}

private fun safeDiagnosticError(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    val http = chain.firstNotNullOfOrNull {
        when (it) {
            is ProviderHttpException -> it.status
            is HttpDataSource.InvalidResponseCodeException -> it.responseCode
            else -> null
        }
    }
    return if (http != null) "HTTP $http" else chain.last().javaClass.simpleName
}

/** Silent, real playback progress, no main queue changes or alternate providers. */
@OptIn(UnstableApi::class)
internal suspend fun probeMusicSource(
    context: Context, source: String, query: String,
    onProgress: (String) -> Unit
): String = withContext(Dispatchers.Main) {
    var player: ExoPlayer? = null
    try {
        onProgress("Поиск…")
        val tracks = withTimeoutOrNull(28_000L) {
            withContext(Dispatchers.IO) { searchDiagnosticMusic(source, query) }
        } ?: return@withContext "Поиск: таймаут"
        val candidates = tracks.filter { it.stream.isNotBlank() }
            .distinctBy { it.stream }.take(3)
        if (candidates.isEmpty()) return@withContext "Найдено: ${tracks.size}. Нет аудиопотоков"
        onProgress("Найдено: ${tracks.size}. Проверка аудио…")
        val probe = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(musicPlaybackSourceFactory(context.applicationContext, useMusicCache = false))
            .build()
        player = probe
        probe.volume = 0f
        var last = "Таймаут воспроизведения"
        for ((index, track) in candidates.withIndex()) {
            onProgress("Найдено: ${tracks.size}. Аудио ${index + 1}/${candidates.size}…")
            probe.stop()
            probe.clearMediaItems()
            probe.setMediaItem(MediaItem.fromUri(Uri.parse(track.stream)))
            probe.prepare()
            probe.play()
            val result = withTimeoutOrNull(20_000L) {
                var outcome: String? = null
                while (outcome == null) {
                    val error = probe.playerError
                    if (error != null) outcome = safeDiagnosticError(error)
                    else if (probe.playbackState == Player.STATE_READY &&
                        probe.currentPosition >= 500L) outcome = "READY"
                    else if (probe.playbackState == Player.STATE_ENDED) outcome = "Короткий поток"
                    else delay(200L)
                }
                outcome
            } ?: "Таймаут воспроизведения"
            if (result == "READY") return@withContext "Найдено: ${tracks.size}. Воспроизведение: READY"
            last = result
        }
        "Найдено: ${tracks.size}. Аудио: $last"
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        "Ошибка: " + safeDiagnosticError(error)
    } finally {
        player?.release()
    }
}
