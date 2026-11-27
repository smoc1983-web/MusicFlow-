package com.musicflow.app

/** Pure bulk-cleanup policy: select only known, removable MusicFlow entries. */
internal enum class CleanupScope { SELECTED, ALL, STREAM_CACHE }

internal data class CleanupCandidate(
    val key: String,
    val stream: Boolean,
    val removable: Boolean,
    // Actual downloaded bytes when known; never treat remote content length as freed storage.
    val storedBytes: Long?
)

internal data class CleanupEstimate(
    val keys: Set<String>,
    val knownBytes: Long,
    val unknownSizeCount: Int,
    val streamCount: Int
) {
    val count: Int get() = keys.size
}

internal object DownloadCleanupPolicy {
    fun plan(
        candidates: List<CleanupCandidate>,
        scope: CleanupScope,
        selectedKeys: Set<String> = emptySet()
    ): CleanupEstimate {
        val targets = candidates.asSequence()
            .filter { it.removable }
            .distinctBy { it.key }
            .filter { entry ->
                when (scope) {
                    CleanupScope.SELECTED -> entry.key in selectedKeys
                    CleanupScope.ALL -> true
                    CleanupScope.STREAM_CACHE -> entry.stream
                }
            }.toList()
        var bytes = 0L
        var unknown = 0
        for (entry in targets) {
            val size = entry.storedBytes?.takeIf { it >= 0L }
            if (size == null) unknown++
            else bytes = if (size > Long.MAX_VALUE - bytes) Long.MAX_VALUE
                else bytes + size
        }
        return CleanupEstimate(
            targets.map { it.key }.toSet(),
            bytes,
            unknown,
            targets.count { it.stream }
        )
    }
}
