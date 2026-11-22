package com.musicflow.app

import android.content.Context

/** Shared state for three new TV layouts; never changes the old IPTV player. */
internal enum class TvHubMode(val title: String) {
    OVERVIEW("Обзор"),
    LIVE("Эфир"),
    PERSONAL("Моё"),
    CLASSIC("Классика")
}

internal object TvHubLibrary {
    private const val PREFS = "musicflow_tv_hub_v1"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): TvHubMode =
        TvHubMode.entries.firstOrNull {
            it.name == prefs(context).getString("layout_mode", TvHubMode.LIVE.name)
        } ?: TvHubMode.LIVE

    fun saveMode(context: Context, mode: TvHubMode) {
        prefs(context).edit().putString("layout_mode", mode.name).apply()
    }

    fun getKeys(context: Context, key: String): Set<String> =
        prefs(context).getStringSet(key, emptySet())?.toSet().orEmpty()

    fun setKeys(context: Context, key: String, values: Set<String>) {
        prefs(context).edit().putStringSet(key, values.toSet()).apply()
    }

    fun recent(context: Context): List<String> =
        prefs(context).getString("recent", "").orEmpty()
            .split(';').filter { it.isNotBlank() }.take(20)

    fun addRecent(context: Context, key: String): List<String> {
        val updated = (listOf(key) + recent(context)).distinct().take(20)
        prefs(context).edit().putString("recent", updated.joinToString(";")).apply()
        return updated
    }

    fun channelKey(sourceId: String, channel: TvChannel): String =
        sourceId + "|" + channel.id

    fun channelMatchesGenre(channel: TvChannel, genre: String): Boolean =
        genre == "Все" || (genre == "Музыка" && channel.isMusic) ||
            channel.group.contains(genre, ignoreCase = true) ||
            channel.title.contains(genre, ignoreCase = true)
}
