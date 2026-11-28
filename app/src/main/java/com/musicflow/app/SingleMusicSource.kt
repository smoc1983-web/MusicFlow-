package com.musicflow.app

/** Diagnostic lookup must never call a different provider as a fallback. */
internal suspend fun <T> searchOneProvider(
    source: String,
    query: String,
    registry: Map<String, suspend (String) -> List<T>>
): List<T> {
    val provider = requireNotNull(registry[source]) { "Unknown music provider" }
    require(query.isNotBlank()) { "Empty music query" }
    return provider(query)
}
