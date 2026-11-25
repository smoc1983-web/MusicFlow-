package com.musicflow.app

internal data class CinemaSeriesGroup(
    val title: String,
    val items: List<CinemaTitle>
) {
    val representative: CinemaTitle get() = items.first()
    val seasons: List<Int>
        get() = items.map { it.seasonNumber ?: 1 }.distinct().sorted()
    val episodesCount: Int get() = items.size
    fun episodesIn(season: Int): List<CinemaTitle> =
        items.filter { (it.seasonNumber ?: 1) == season }
            .sortedWith(compareBy<CinemaTitle> { it.episodeNumber ?: Int.MAX_VALUE }
                .thenBy { it.title })
}

/** Group only known series episodes, never arbitrarily merge unrelated films. */
internal object CinemaSeriesGrouping {
    private val patterns = listOf(
        Regex("""(?iu)[\s,.:|–—-]*\b\d{1,2}\s*(?:й|я|ый|ая)?\s*сезон[а]?\b.*$"""),
        Regex("""(?iu)[\s,.:|–—-]*\bсезон\s*\d{1,2}\b.*$"""),
        Regex("""(?iu)[\s,.:|–—-]*\b\d{1,3}\s*(?:я|ая)?\s*серия\b.*$"""),
        Regex("""(?iu)[\s,.:|–—-]*\bсерия\s*\d{1,3}\b.*$"""),
        Regex("""(?iu)\s*[|–—-]\s*s\d{1,2}e\d{1,3}\b.*$""")
    )

    fun titleFor(raw: String): String {
        var title = raw.trim().replace(
            Regex("""(?iu)^сериал\s*[:—-]?\s+"""), ""
        )
        for (pattern in patterns) {
            title = title.replace(pattern, "").trimEnd(' ', ',', ':', '.', '-', '—', '|')
        }
        return title.ifBlank { raw.trim() }.take(150)
    }

    fun group(items: List<CinemaTitle>): List<CinemaSeriesGroup> {
        val candidates = items.filter { it.kind == CinemaKind.SERIES }
        // Some public API cards omit tv_show_id even when other cards
        // for the same named series provide it. A unique ID can be inherited
        // only within the same source and exact normalized title.
        val knownIdsByTitle = candidates.filter { it.tvShowId != null }
            .groupBy { it.sourceId + "|" + titleFor(it.title).lowercase() }
            .mapValues { (_, entries) ->
                entries.mapNotNull { it.tvShowId }.distinct()
            }
        return candidates.groupBy { item ->
            val titleKey = item.sourceId + "|" + titleFor(item.title).lowercase()
            val id = item.tvShowId ?: knownIdsByTitle[titleKey]?.singleOrNull()
            val identity = id?.let { "show/$it" }
                ?: "title/" + titleFor(item.title).lowercase()
            item.sourceId + "|" + identity
        }.values.map { bucket ->
            CinemaSeriesGroup(
                title = titleFor(bucket.first().title),
                items = bucket.distinctBy { it.key }
                    .sortedWith(compareBy<CinemaTitle> { it.seasonNumber ?: 1 }
                        .thenBy { it.episodeNumber ?: Int.MAX_VALUE })
            )
        }.sortedBy { it.title.lowercase() }
    }
}
