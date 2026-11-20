package com.musicflow.app

internal suspend fun resolveRecommendedTrack(artist: String, title: String): Track? =
    resolvePlayableAlbumTrack(artist.trim().replace(Regex("(?i)^by\\s+"), ""), title, emptyList())
