package com.musicflow.app

/** Verified listening time, measured by a monotonic clock rather than by seek position. */
internal data class ListeningTick(
    val id: String,
    val listenedMs: Long,
    val qualifiedStart: Boolean
)

/** One instance per playback service: no global mutable counter and no user tracking network. */
internal class ListeningProgressTracker {
    private data class Sample(val elapsedMs: Long, val positionMs: Long, val playing: Boolean)

    private var currentId: String? = null
    private var last: Sample? = null
    private var sessionMs = 0L
    private var historyRecorded = false

    fun sample(id: String?, elapsedMs: Long, positionMs: Long, isPlaying: Boolean): ListeningTick? {
        if (id.isNullOrBlank() || id.startsWith("ruvideo:") || id.startsWith("vk:")) {
            currentId = null
            last = null
            sessionMs = 0L
            historyRecorded = false
            return null
        }
        if (id != currentId) {
            currentId = id
            last = null
            sessionMs = 0L
            historyRecorded = false
        }
        val old = last
        last = Sample(elapsedMs, positionMs, isPlaying)
        if (!isPlaying || old?.playing != true) return null

        val elapsed = elapsedMs - old.elapsedMs
        if (elapsed !in 1L..10_000L) return null
        if (!id.startsWith("radio:")) {
            val advanced = positionMs - old.positionMs
            // A seek is not listening time; avoid rewarding huge position jumps.
            if (advanced < 0L || advanced > elapsed + 2_000L) return null
        }

        sessionMs += elapsed
        val becameAudible = !historyRecorded && sessionMs >= 3_000L
        if (becameAudible) historyRecorded = true
        return ListeningTick(id, elapsed, becameAudible)
    }
}
