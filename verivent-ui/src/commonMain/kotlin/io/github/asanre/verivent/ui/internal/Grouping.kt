package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.TimelineEntry

/**
 * A screen section in the timeline: a group-opening event and everything logged until the next one.
 * [key] holds its collapse state: the id of the event that opened it, kept after that event is gone.
 */
internal data class TimelineGroup(
    val screen: String?,
    val entries: List<TimelineEntry>,
    val openedBy: String? = null,
    val key: String = entries.last().id,
)

/**
 * Split the (chronological) entries into collapsible groups (à la Tag Assistant): every analytics
 * entry stamped with a [AnalyticsEntry.groupLabel] at capture opens one, labelled with that stamp.
 * Groups and their entries come back newest-first for display. When [grouped] is false everything
 * lands in a single unnamed group.
 *
 * Repeated stamps are never merged: two visits to the same screen give two separate groups, which is
 * what makes a close-and-reopen visible.
 *
 * The store evicts oldest first, so only the oldest group can lose its opening event. It then
 * continues the oldest group of [previous], which held its newest entry, with that group's key and label,
 * instead of turning into an unnamed group that reopens.
 */
internal fun groupEntries(entries: List<TimelineEntry>, grouped: Boolean, previous: List<TimelineGroup> = emptyList()): List<TimelineGroup> {
    if (!grouped) {
        return listOf(TimelineGroup(screen = null, entries = entries.asReversed()))
    }
    val groups = mutableListOf<TimelineGroup>()
    var screen: String? = null
    var openedBy: String? = null
    var bucket = mutableListOf<TimelineEntry>()

    for (entry in entries) {
        if (entry is AnalyticsEntry && entry.groupLabel != null) {
            if (bucket.isNotEmpty()) groups += TimelineGroup(screen, bucket.asReversed(), openedBy)
            screen = entry.groupLabel
            openedBy = entry.name
            bucket = mutableListOf(entry)
        } else {
            bucket += entry
        }
    }
    if (bucket.isNotEmpty()) groups += TimelineGroup(screen, bucket.asReversed(), openedBy)

    val oldest = groups.firstOrNull()
    val continued = oldest?.takeIf { it.screen == null }?.let { headless ->
        previous.lastOrNull()?.takeIf { group -> group.entries.any { it.id == headless.entries.first().id } }
    }
    if (oldest != null && continued != null) {
        groups[0] = oldest.copy(screen = continued.screen, openedBy = continued.openedBy, key = continued.key)
    }
    return groups.asReversed()
}

/** [groupEntries] with the groups it returned last time as `previous`. One per timeline. */
internal class GroupTracker {
    private var last = emptyList<TimelineGroup>()

    fun group(entries: List<TimelineEntry>, grouped: Boolean): List<TimelineGroup> =
        groupEntries(entries, grouped, last).also { last = it }
}

/** The collapsed keys of [groups] still on the timeline; the rest were evicted with their groups. */
internal fun Set<String>.keptFor(groups: List<TimelineGroup>): Set<String> = filterTo(HashSet()) { key -> groups.any { it.key == key } }
