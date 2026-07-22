package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.SnoopEntry

/** A screen section in the timeline: a screen_view event and everything logged until the next one. */
internal data class TimelineGroup(
    val screen: String?,
    val entries: List<SnoopEntry>,
)

/**
 * Split the (chronological) entries into collapsible groups keyed by [groupByEvent] (à la Tag
 * Assistant). Groups and their entries come back newest-first for display. When [groupByEvent] is
 * null, everything lands in a single unnamed group.
 */
internal fun groupEntries(entries: List<SnoopEntry>, groupByEvent: String?): List<TimelineGroup> {
    if (groupByEvent == null) {
        return listOf(TimelineGroup(screen = null, entries = entries.asReversed()))
    }
    val groups = mutableListOf<TimelineGroup>()
    var screen: String? = null
    var bucket = mutableListOf<SnoopEntry>()

    for (entry in entries) {
        if (entry is AnalyticsEntry && entry.name == groupByEvent) {
            if (bucket.isNotEmpty()) groups += TimelineGroup(screen, bucket)
            screen = entry.properties.firstOrNull { it.name == "screen" }?.value ?: entry.name
            bucket = mutableListOf(entry)
        } else {
            bucket += entry
        }
    }
    if (bucket.isNotEmpty()) groups += TimelineGroup(screen, bucket)

    return groups.asReversed().map { it.copy(entries = it.entries.asReversed()) }
}
