package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.core.model.TimelineEntry

/**
 * No-op mirror of the baseline. Nothing is captured, so [diff] never reports a match: a test run
 * against the no-op fails loudly instead of passing on an empty session.
 */
object Baseline {

    fun record(entries: List<TimelineEntry> = emptyList()): String = """{ "baseline": 1, "events": [] }"""

    fun diff(
        baselineJson: String,
        entries: List<TimelineEntry> = emptyList(),
        variables: Map<String, Any?> = emptyMap(),
    ): String? = "Verivent no-op: nothing was captured"
}
