package com.snoop.ui.internal

import com.snoop.ui.TypeFilter

/**
 * Type and channel selection for the timeline. Channels only exist on analytics entries, so the
 * two selections are kept mutually consistent: picking a channel forces the Analytics type, and
 * picking Network drops any channel.
 */
internal data class TimelineFilters(
    val type: TypeFilter = TypeFilter.All,
    val channel: String? = null,
) {
    fun selectType(newType: TypeFilter): TimelineFilters =
        copy(type = newType, channel = if (newType == TypeFilter.Network) null else channel)

    fun selectChannel(name: String): TimelineFilters =
        if (channel == name) copy(channel = null) else TimelineFilters(TypeFilter.Analytics, name)
}
