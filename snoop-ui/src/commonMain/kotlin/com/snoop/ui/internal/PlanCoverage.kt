package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.PlanEvent
import com.snoop.core.model.PlanSummary
import com.snoop.core.model.SnoopEntry

internal val AnalyticsEntry.breaksPlan: Boolean get() = issues.isNotEmpty()

internal val AnalyticsEntry.matchesPlan: Boolean get() = planVersion != null && issues.isEmpty()

internal val SnoopEntry.breaksPlan: Boolean get() = this is AnalyticsEntry && breaksPlan

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

/**
 * Every event [plan] defines, against the captures checked by this same version: events stamped by
 * an earlier plan say nothing about this one. Ordered breaks, matches, not seen, then plan order.
 */
internal fun planCoverage(entries: List<SnoopEntry>, plan: PlanSummary): List<EventCoverage> {
    val checked = entries.filterIsInstance<AnalyticsEntry>()
        .filter { it.planVersion == plan.version }
        .groupBy { PlanEvent(it.name, it.channel) }
    return plan.events
        .map { event ->
            val captures = checked[event].orEmpty()
            EventCoverage(event, captured = captures.size, breaking = captures.count { it.breaksPlan })
        }
        .sortedBy { it.verdict }
}
