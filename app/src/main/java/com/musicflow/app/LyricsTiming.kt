package com.musicflow.app

/** Shared lyric timing rule. Positive offset shows a line earlier. */
internal object LyricsTiming {
    private const val MAX_OFFSET_MS = 5_000

    fun clamp(offsetMs: Int): Int = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)

    fun activeIndex(timestampsMs: List<Long>, playbackPositionMs: Long, offsetMs: Int): Int {
        if (timestampsMs.isEmpty()) return -1
        val target = (playbackPositionMs + clamp(offsetMs)).coerceAtLeast(0L)
        var lo = 0
        var hi = timestampsMs.size - 1
        var answer = -1
        while (lo <= hi) {
            val middle = lo + (hi - lo) / 2
            if (timestampsMs[middle] <= target) {
                answer = middle
                lo = middle + 1
            } else {
                hi = middle - 1
            }
        }
        return answer
    }
}
