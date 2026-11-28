package com.musicflow.app

internal data class DownloadSpeedSample(val bytes: Long, val measuredAtMs: Long, val bytesPerSecond: Long)

internal object DownloadSpeed {
    fun next(previous: DownloadSpeedSample?, nowBytes: Long, nowMs: Long): DownloadSpeedSample {
        if (previous == null || nowMs <= previous.measuredAtMs ||
            nowBytes < previous.bytes) {
            return DownloadSpeedSample(nowBytes.coerceAtLeast(0L), nowMs, 0L)
        }
        val seconds = (nowMs - previous.measuredAtMs).toDouble() / 1000.0
        val speed = ((nowBytes - previous.bytes).toDouble() / seconds)
            .coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
        return DownloadSpeedSample(nowBytes, nowMs, speed)
    }
}
