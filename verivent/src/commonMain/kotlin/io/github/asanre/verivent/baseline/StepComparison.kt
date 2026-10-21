package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.Schema
import io.github.asanre.verivent.analytics.check
import io.github.asanre.verivent.analytics.exportedValues
import io.github.asanre.verivent.baseline.BaselineDiff.Finding
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.TimelineEntry
import kotlinx.serialization.json.JsonObject

internal val AnalyticsEntry.key get() = PlanEvent(name, channel)

/** Where an entry falls in capture order: ids are a counter, so they only grow. */
internal val TimelineEntry.order: Long get() = id.toLongOrNull() ?: 0

/**
 * Pairs every [expected] event with one [sent] event it matches (same name and channel, no issue)
 * and reports what's left. Sent events whose name and channel nothing expects are ignored: the plan
 * covers those. An expected event left over is explained by the closest sent one nobody took.
 */
internal fun compareStep(
    expected: List<BaselineFile.ExpectedEvent>,
    sequence: Boolean,
    sent: List<AnalyticsEntry>,
    variables: Map<String, Any?>,
): List<Finding> {
    val keys = expected.mapTo(HashSet()) { it.key }
    val candidates = sent.filter { it.key in keys }
    val schemas = expected.map { Schema.parse(it.schema.filledIn(variables) as JsonObject, it.key.name, template = it.schema) }
    val values = candidates.map { exportedValues(it.properties) }
    // What breaks each expected event in each candidate of its name and channel, null for the rest.
    val issues = expected.indices.map { event ->
        candidates.indices.map { at -> values[at].takeIf { candidates[at].key == expected[event].key }?.let(schemas[event]::check) }
    }
    val fits = issues.map { row -> row.map { it?.isEmpty() == true } }
    val pairs = if (sequence) inOrder(fits, candidates.size) else maximumMatching(fits, candidates.size)
    val taken = pairs.filterTo(HashSet()) { it >= 0 }

    val findings = expected.indices.filter { pairs[it] < 0 }.map { event ->
        val key = expected[event].key
        val closest = issues[event].withIndex()
            .mapNotNull { (at, found) -> found?.takeIf { at !in taken }?.let { at to it } }
            .minByOrNull { (_, found) -> found.size }
        if (closest == null) return@map Finding.Missing(key)
        val (at, found) = closest
        taken += at
        // One that fits but wasn't paired can only be out of a sequence's order: it goes before the
        // next expected event that did arrive in order.
        if (found.isEmpty()) Finding.OutOfOrder(key, before = (event + 1 until expected.size).firstOrNull { pairs[it] >= 0 }?.let { expected[it].key })
        else Finding.Mismatch(key, found.map { it.toPlanIssue() })
    }
    return findings + candidates.indices.filterNot { it in taken }.map { Finding.Extra(candidates[it].key) }
}

/**
 * Expected events matched in their order, as many as possible: the longest common subsequence, so
 * one event out of place reports that event and not everything after it.
 */
private fun inOrder(fits: List<List<Boolean>>, sentCount: Int): IntArray {
    val longest = Array(fits.size + 1) { IntArray(sentCount + 1) }
    for (event in fits.indices.reversed()) {
        for (entry in (0 until sentCount).reversed()) {
            longest[event][entry] = if (fits[event][entry]) {
                longest[event + 1][entry + 1] + 1
            } else {
                maxOf(longest[event + 1][entry], longest[event][entry + 1])
            }
        }
    }
    val pairs = IntArray(fits.size) { -1 }
    var event = 0
    var entry = 0
    while (event < fits.size && entry < sentCount) {
        when {
            fits[event][entry] && longest[event][entry] == longest[event + 1][entry + 1] + 1 -> pairs[event++] = entry++
            longest[event + 1][entry] >= longest[event][entry + 1] -> event++
            else -> entry++
        }
    }
    return pairs
}

/** As many expected events matched as possible, each sent event used once (Kuhn's algorithm). */
private fun maximumMatching(fits: List<List<Boolean>>, sentCount: Int): IntArray {
    val owner = IntArray(sentCount) { -1 }

    fun claim(event: Int, seen: BooleanArray): Boolean {
        for (entry in 0 until sentCount) {
            if (!fits[event][entry] || seen[entry]) continue
            seen[entry] = true
            if (owner[entry] < 0 || claim(owner[entry], seen)) {
                owner[entry] = event
                return true
            }
        }
        return false
    }

    fits.indices.forEach { claim(it, BooleanArray(sentCount)) }
    val pairs = IntArray(fits.size) { -1 }
    owner.forEachIndexed { entry, event -> if (event >= 0) pairs[event] = entry }
    return pairs
}
