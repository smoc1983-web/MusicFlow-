package com.musicflow.app

/**
 * Identity for the cinema catalogue LazyColumn. A new identity recreates the
 * scroll state at item zero, before Compose can retain the previous footer key.
 *
 * Only major view/content phases participate; pagination adds rows without
 * changing this key and does not unexpectedly interrupt user scrolling.
 */
internal data class CinemaCatalogScrollIdentity(
    val section: String,
    val query: String,
    val sourceId: String,
    val shelfUrl: String?,
    val phase: String
)

internal object CinemaCatalogScrollPolicy {
    fun identity(
        query: String,
        section: String,
        sourceId: String,
        shelfUrl: String?,
        hasVisibleContent: Boolean,
        searching: Boolean
    ): CinemaCatalogScrollIdentity {
        val phase = when {
            searching -> "searching"
            hasVisibleContent -> "ready"
            else -> "empty"
        }
        return CinemaCatalogScrollIdentity(
            section = section,
            query = query.trim(),
            sourceId = sourceId,
            shelfUrl = shelfUrl,
            phase = phase
        )
    }
}
