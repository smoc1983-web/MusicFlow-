package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class DownloadCleanupPolicyTest {
    private val entries = listOf(
        CleanupCandidate("cache:hls", true, true, 5_242_880L),
        CleanupCandidate("direct:10", false, true, 1_048_576L),
        CleanupCandidate("cache:dash", true, true, null),
        CleanupCandidate("direct:11", false, false, 500L)
    )

    @Test fun selectedOnlyDeletesExplicitKnownKeys() {
        val result = DownloadCleanupPolicy.plan(
            entries, CleanupScope.SELECTED, setOf("direct:10", "missing", "direct:11")
        )
        assertEquals(setOf("direct:10"), result.keys)
        assertEquals(1_048_576L, result.knownBytes)
        assertEquals(0, result.streamCount)
    }

    @Test fun deleteAllIncludesStreamsAndDirectFilesExceptAlreadyRemoving() {
        val result = DownloadCleanupPolicy.plan(entries, CleanupScope.ALL)
        assertEquals(setOf("cache:hls", "cache:dash", "direct:10"), result.keys)
        assertEquals(3, result.count)
        assertEquals(2, result.streamCount)
        assertEquals(1, result.unknownSizeCount)
    }

    @Test fun clearStreamCacheLeavesDirectDownloadsUntouched() {
        val result = DownloadCleanupPolicy.plan(entries, CleanupScope.STREAM_CACHE)
        assertEquals(setOf("cache:hls", "cache:dash"), result.keys)
        assertEquals(5_242_880L, result.knownBytes)
    }

    @Test fun duplicateKeysNeverDoubleCountSpace() {
        val result = DownloadCleanupPolicy.plan(
            entries + entries.first(), CleanupScope.ALL
        )
        assertEquals(6_291_456L, result.knownBytes)
        assertEquals(3, result.count)
    }

    @Test fun unknownAndNegativeBytesAreNotShownAsFreedStorage() {
        val result = DownloadCleanupPolicy.plan(
            listOf(CleanupCandidate("a", false, true, -1L),
                CleanupCandidate("b", true, true, null)),
            CleanupScope.ALL
        )
        assertEquals(0L, result.knownBytes)
        assertEquals(2, result.unknownSizeCount)
    }

    @Test fun emptySelectionDoesNotDeleteAnything() {
        assertEquals(0, DownloadCleanupPolicy.plan(
            entries, CleanupScope.SELECTED, emptySet()).count)
    }

    @Test fun byteEstimateSaturatesInsteadOfOverflowing() {
        val result = DownloadCleanupPolicy.plan(
            listOf(CleanupCandidate("a", true, true, Long.MAX_VALUE),
                CleanupCandidate("b", true, true, 1024L)), CleanupScope.ALL)
        assertEquals(Long.MAX_VALUE, result.knownBytes)
    }
}
