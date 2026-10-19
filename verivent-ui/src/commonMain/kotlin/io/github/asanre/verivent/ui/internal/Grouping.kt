package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.TimelineEntry

/** A screen section in the timeline: a group-opening event and everything logged until the next one. */
internal data class TimelineGroup(
    val screen: String?,
    val entries: List<TimelineEntry>,
    val openedBy: String? = null,
)

/**
 * Collapse-state key for a group. Entries are newest-first, so `last()` is the opening
 * event — stable while the group grows, unlike `first()` which changes on every new entry.
 */
internal fun TimelineGroup.stableKey(): String = entries.last().id

/**
 * Split the (chronological) entries into collapsible groups (à la Tag Assistant): every analytics
 * entry stamped with a [AnalyticsEntry.groupLabel] at capture opens one, labelled with that stamp.
 * Groups and their entries come back newest-first for display. When [grouped] is false everything
 * lands in a single unnamed group.
 *
 * Repeated stamps are never merged: two visits to the same screen give two separate groups, which is
 * what makes a close-and-reopen visible.
 */
internal fun groupEntries(entries: List<TimelineEntry>, grouped: Boolean): List<TimelineGroup> {
    if (!grouped) {
        return listOf(TimelineGroup(screen = null, entries = entries.asReversed()))
    }
    val groups = mutableListOf<TimelineGroup>()
    var screen: String? = null
    var openedBy: String? = null
    var bucket = mutableListOf<TimelineEntry>()

    for (entry in entries) {
        if (entry is AnalyticsEntry && entry.groupLabel != null) {
            if (bucket.isNotEmpty()) groups += TimelineGroup(screen, bucket, openedBy)
            screen = entry.groupLabel
            openedBy = entry.name
            bucket = mutableListOf(entry)
        } else {
            bucket += entry
        }
    }
    if (bucket.isNotEmpty()) groups += TimelineGroup(screen, bucket, openedBy)

    return groups.asReversed().map { it.copy(entries = it.entries.asReversed()) }
}
