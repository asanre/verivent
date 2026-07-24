package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry

internal enum class TypeFilter { All, Network, Analytics }

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

/** Whether [entry] survives the current type/channel selection and free-text [query]. */
internal fun matchesFilters(
    entry: SnoopEntry,
    type: TypeFilter,
    channel: String?,
    query: String,
): Boolean {
    val typeOk = when (type) {
        TypeFilter.All -> true
        TypeFilter.Network -> entry is NetworkTransaction
        TypeFilter.Analytics -> entry is AnalyticsEntry
    }
    if (!typeOk) return false
    if (channel != null && !(entry is AnalyticsEntry && entry.channel == channel)) return false
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return when (entry) {
        is NetworkTransaction ->
            // The url carries the query string (already redacted), which path alone drops.
            entry.url.lowercase().contains(q) ||
                entry.host.lowercase().contains(q) ||
                entry.method.lowercase().contains(q)
        is AnalyticsEntry ->
            entry.name.lowercase().contains(q) || entry.channel.lowercase().contains(q)
    }
}
