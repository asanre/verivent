package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.core.model.TimelineEntry

/**
 * Whether [plan], the one loaded now, is the one that checked this event. A verdict stamped by a plan
 * since cleared or replaced stays on the entry, for the export, but is no longer shown.
 */
internal fun AnalyticsEntry.checkedBy(plan: PlanSummary?): Boolean = plan != null && planVersion == plan.version

internal fun AnalyticsEntry.breaksPlan(plan: PlanSummary?): Boolean = checkedBy(plan) && issues.isNotEmpty()

internal fun AnalyticsEntry.matchesPlan(plan: PlanSummary?): Boolean = checkedBy(plan) && issues.isEmpty()

internal fun TimelineEntry.breaksPlan(plan: PlanSummary?): Boolean = this is AnalyticsEntry && breaksPlan(plan)

internal fun problemCount(count: Int): String = if (count == 1) "1 problem" else "$count problems"

internal enum class PlanVerdict { Breaks, Matches, NotSeen }

/**
 * How this session's captures of one planned event went: [breaking] of [captured] broke it. An event
 * the plan tells apart by variants has one per variant, [label]led `name › variant`, plus one for
 * captures no variant fits when there are any.
 */
internal data class EventCoverage(val event: PlanEvent, val captured: Int, val breaking: Int, val label: String = event.name) {
    val verdict: PlanVerdict
        get() = when {
            breaking > 0 -> PlanVerdict.Breaks
            captured > 0 -> PlanVerdict.Matches
            else -> PlanVerdict.NotSeen
        }
}

/**
 * Every event [plan] defines, and every variant of one it tells apart by them, against the captures
 * it checked. Ordered breaks, matches, not seen, then plan order.
 */
internal fun planCoverage(entries: List<TimelineEntry>, plan: PlanSummary): List<EventCoverage> {
    val checked = entries.filterIsInstance<AnalyticsEntry>()
        .filter { it.checkedBy(plan) }
        .groupBy { PlanEvent(it.name, it.channel) }
    fun coverage(event: PlanEvent, captures: List<AnalyticsEntry>, label: String = event.name) =
        EventCoverage(event, captured = captures.size, breaking = captures.count { it.breaksPlan(plan) }, label = label)
    return plan.events
        .flatMap { event ->
            val captures = checked[event].orEmpty()
            val variants = plan.variants[event]
            if (variants == null) {
                listOf(coverage(event, captures))
            } else {
                val byVariant = captures.groupBy { it.variant }
                variants.map { coverage(event, byVariant[it].orEmpty(), "${event.name} › $it") } +
                    listOfNotNull(byVariant[null]?.let { coverage(event, it, "${event.name} › no planned variant") })
            }
        }
        .sortedBy { it.verdict }
}
