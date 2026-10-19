package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.TimelineEntry

/**
 * Record a session's analytics as a baseline and check later sessions against it, in a test:
 *
 * ```kotlin
 * val baseline = Baseline.record()                     // once, then keep the JSON with the test
 * Baseline.diff(baseline, variables = mapOf("user_id" to user.id))?.let { fail(it) }
 * ```
 *
 * A baseline is the tracking plan's JSON Schema per event, each property recorded as a `const` of
 * the value sent. Edit it like a plan: loosen a `const` to a `type` or a `pattern`, or put
 * `"{{name}}"` in a `const` for a value the test fills in. By default it's compared as a set: every
 * recorded event must be sent and nothing else, in any order. `"order": "sequence"` asks for the
 * recorded order too, and `"extra": "allow"` lets other events through.
 */
object Baseline {

    /** The analytics events among [entries] as a baseline's JSON, in the order they were captured. */
    fun record(entries: List<TimelineEntry> = Inspector.entriesSnapshot): String =
        recordBaseline(entries.filterIsInstance<AnalyticsEntry>())

    /**
     * What tells the analytics events among [entries] apart from [baselineJson], as text to fail a
     * test with; null when they match. Issues state what was expected, never the value sent.
     *
     * @throws IllegalArgumentException when [baselineJson] isn't a valid baseline, or a `{{name}}`
     * it uses has no value in [variables].
     */
    fun diff(
        baselineJson: String,
        entries: List<TimelineEntry> = Inspector.entriesSnapshot,
        variables: Map<String, Any?> = emptyMap(),
    ): String? = compare(BaselineFile.parse(baselineJson, variables), entries.filterIsInstance<AnalyticsEntry>())
}
