package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.PlanEvent
import com.snoop.core.model.PlanSummary
import com.snoop.core.model.SnoopEntry

/**
 * Whether [plan], the one loaded now, is the one that checked this event. A verdict stamped by a plan
 * since cleared or replaced stays on the entry, for the export, but is no longer shown.
 */
internal fun AnalyticsEntry.checkedBy(plan: PlanSummary?): Boolean = plan != null && planVersion == plan.version

internal fun AnalyticsEntry.breaksPlan(plan: PlanSummary?): Boolean = checkedBy(plan) && issues.isNotEmpty()

internal fun AnalyticsEntry.matchesPlan(plan: PlanSummary?): Boolean = checkedBy(plan) && issues.isEmpty()

internal fun SnoopEntry.breaksPlan(plan: PlanSummary?): Boolean = this is AnalyticsEntry && breaksPlan(plan)

internal fun problemCount(count: Int): String = if (count == 1) "1 problem" else "$count problems"

internal enum class PlanVerdict { Breaks, Matches, NotSeen }

/** How this session's captures of one planned event went: [breaking] of [captured] broke it. */
internal data class EventCoverage(val event: PlanEvent, val captured: Int, val breaking: Int) {
    val verdict: PlanVerdict
        get() = when {
            breaking > 0 -> PlanVerdict.Breaks
            captured > 0 -> PlanVerdict.Matches
            else -> PlanVerdict.NotSeen
        }
}

/** Every event [plan] defines, against the captures it checked. Ordered breaks, matches, not seen, then plan order. */
internal fun planCoverage(entries: List<SnoopEntry>, plan: PlanSummary): List<EventCoverage> {
    val checked = entries.filterIsInstance<AnalyticsEntry>()
        .filter { it.checkedBy(plan) }
        .groupBy { PlanEvent(it.name, it.channel) }
    return plan.events
        .map { event ->
            val captures = checked[event].orEmpty()
            EventCoverage(event, captured = captures.size, breaking = captures.count { it.breaksPlan(plan) })
        }
        .sortedBy { it.verdict }
}
