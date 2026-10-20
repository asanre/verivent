package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.core.model.TimelineEntry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The verdict a black-box test asks about, against the plan loaded now (events keep the verdict of
 * the plan they were captured with, so one checked by another plan, or by none, is unplanned).
 */
internal enum class Verdict {
    /** Checked by the loaded plan and compliant. */
    Good,

    /** Checked by the loaded plan, with issues. */
    Bad,

    /** Not checked by the loaded plan: it doesn't define the event, or there's no plan. */
    Unplanned,
    ;

    /** The last segment of its `/api/assert/…` route. */
    val route: String get() = name.lowercase()

    companion object {
        fun of(entry: AnalyticsEntry, plan: PlanSummary?): Verdict = when {
            plan == null || entry.planVersion != plan.version -> Unplanned
            entry.issues.isEmpty() -> Good
            else -> Bad
        }
    }
}

/** `{plan, total, good, bad, unplanned}` over the analytics events in [entries]. */
internal fun assertionSummary(entries: List<TimelineEntry>, plan: PlanSummary?): String {
    val counts = entries.filterIsInstance<AnalyticsEntry>().groupingBy { Verdict.of(it, plan) }.eachCount()
    return buildJsonObject {
        put("plan", plan?.version)
        put("total", counts.values.sum())
        Verdict.entries.forEach { put(it.route, counts[it] ?: 0) }
    }.toString()
}

/** The analytics events in [entries] with this verdict, optionally of one event name and channel. */
internal fun assertionEntries(
    entries: List<TimelineEntry>,
    plan: PlanSummary?,
    verdict: Verdict,
    name: String?,
    channel: String?,
): List<AnalyticsEntry> = entries.filterIsInstance<AnalyticsEntry>().filter {
    Verdict.of(it, plan) == verdict && (name == null || it.name == name) && (channel == null || it.channel == channel)
}
