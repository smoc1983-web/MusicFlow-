package com.musicflow.app

/**
 * Radio controls operate on the exact list that was visible when the station
 * was selected. Never reorder/mix favorites with popular stations.
 */
internal object RadioQueuePolicy {
    fun playlist(station: RussianRadioStation, visible: List<RussianRadioStation>): Pair<List<RussianRadioStation>, Int> {
        val distinct = visible.filter { it.uuid.isNotBlank() &&
            RussianRadioDirectory.isPublicStream(it.stream) }.distinctBy { it.uuid }.take(300)
        val index = distinct.indexOfFirst { it.uuid == station.uuid }
        return if (index >= 0) distinct to index else listOf(station) to 0
    }

    /** New radio session starts ordered; choosing another radio row keeps shuffle. */
    fun preserveShuffle(wasRadio: Boolean, wasShuffled: Boolean): Boolean =
        wasRadio && wasShuffled

    fun canNavigate(size: Int): Boolean = size > 1
}
