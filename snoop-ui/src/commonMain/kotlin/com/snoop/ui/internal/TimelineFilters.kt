package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.PlanSummary
import com.snoop.core.model.SnoopEntry

internal enum class TypeFilter { All, Network, Analytics }

/**
 * Type, channel and plan selection for the timeline. Channels and plan verdicts only exist on
 * analytics entries, so the selections are kept mutually consistent: picking a channel or "breaks
 * plan" forces the Analytics type, and picking Network drops both.
 */
internal data class TimelineFilters(
    val type: TypeFilter = TypeFilter.All,
    val channel: String? = null,
    val breaksPlan: Boolean = false,
) {
    fun selectType(newType: TypeFilter): TimelineFilters =
        if (newType == TypeFilter.Network) TimelineFilters(newType) else copy(type = newType)

    fun selectChannel(name: String): TimelineFilters =
        if (channel == name) copy(channel = null) else copy(type = TypeFilter.Analytics, channel = name)

    fun toggleBreaksPlan(): TimelineFilters =
        if (breaksPlan) copy(breaksPlan = false) else copy(type = TypeFilter.Analytics, breaksPlan = true)
}

/** Whether [entry] survives the [filters] selection and free-text [query]; [plan] is the one loaded now. */
internal fun matchesFilters(entry: SnoopEntry, filters: TimelineFilters, query: String, plan: PlanSummary?): Boolean {
    val typeOk = when (filters.type) {
        TypeFilter.All -> true
        TypeFilter.Network -> entry is NetworkTransaction
        TypeFilter.Analytics -> entry is AnalyticsEntry
    }
    if (!typeOk) return false
    if (filters.channel != null && !(entry is AnalyticsEntry && entry.channel == filters.channel)) return false
    if (filters.breaksPlan && !entry.breaksPlan(plan)) return false
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
