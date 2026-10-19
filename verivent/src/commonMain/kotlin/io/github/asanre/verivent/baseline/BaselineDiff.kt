package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.check
import io.github.asanre.verivent.core.model.AnalyticsEntry

/**
 * Pairs every expected event with one sent event it matches (same name and channel, no issue) and
 * reports what's left: expected events nobody matched, and, unless allowed, sent events no expected
 * one took. Null when nothing is left.
 */
internal fun compare(baseline: BaselineFile, sent: List<AnalyticsEntry>): String? {
    val expected = baseline.events
    val fits = expected.map { event ->
        sent.map { entry -> event.name == entry.name && event.channel == entry.channel && event.schema.check(entry.properties).isEmpty() }
    }
    val pairs = if (baseline.sequence) inOrder(fits, sent.size) else maximumMatching(fits, sent.size)
    val missing = expected.indices.filter { pairs[it] < 0 }
    val taken = pairs.filter { it >= 0 }.toSet()
    val extra = if (baseline.allowExtra) emptyList() else sent.indices.filterNot { it in taken }
    if (missing.isEmpty() && extra.isEmpty()) return null

    return buildString {
        append("Baseline: ${expected.size} expected, ${expected.size - missing.size} matched, ${missing.size} missing")
        if (!baseline.allowExtra) append(", ${extra.size} extra")
        if (baseline.sequence) append(", in order")
        appendLine()
        missing.forEach { index ->
            val event = expected[index]
            appendLine("✗ missing  ${event.name} · ${event.channel}, #${index + 1} in the baseline")
            // The closest same-named event, preferring one nothing else took: its issues say why.
            val closest = sent.indices
                .filter { sent[it].name == event.name && sent[it].channel == event.channel }
                .map { it to event.schema.check(sent[it].properties) }
                .minWithOrNull(compareBy({ it.first in taken }, { it.second.size }))
            if (closest == null) {
                appendLine("    never sent")
            } else {
                val (at, findings) = closest
                val why = when {
                    findings.isNotEmpty() -> ""
                    at in taken -> ", which matches another expected event"
                    else -> ", which matches but is out of order"
                }
                appendLine("    closest: event ${sent[at].id}$why")
                findings.map { it.toPlanIssue() }.forEach { appendLine("      ${it.property} · ${it.message}") }
            }
        }
        extra.forEach { appendLine("+ extra    ${sent[it].name} · ${sent[it].channel}, event ${sent[it].id}") }
    }.trimEnd()
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
