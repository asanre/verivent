package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.github.asanre.verivent.core.internal.runSafely
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.redaction.RedactionEngine
import io.github.asanre.verivent.web.encodeEntry

/**
 * Vendor-agnostic analytics sink. The host wires its own analytics facade to [log]; Verivent never
 * depends on any analytics SDK. Create with the [AnalyticsSink] factory.
 */
class AnalyticsSink internal constructor(private val config: AnalyticsSinkConfig) {

    private val redaction = RedactionEngine(propertyPredicates = config.propertyRedactors)

    fun log(
        name: String,
        channel: String,
        properties: Map<String, Any?> = emptyMap(),
        sections: Map<String, Map<String, Any?>> = emptyMap(),
    ) {
        runSafely(TAG) {
            if (!Inspector.enabled) return@runSafely
            val event = AnalyticsEvent(name, channel, properties)
            // A throwing filter keeps the event visible (fail open): missing data is the worse bug.
            val visible = config.filters.all {
                runCatching { it(event) }
                    .onFailure { failure -> DiagnosticLog.warn(TAG, "filter threw; keeping the event", failure) }
                    .getOrDefault(true)
            }
            if (!visible) return@runSafely

            // Labelled off the redacted list on purpose: a group must never surface a value the
            // host asked to hide, and the viewers already only ever read redacted properties.
            val redacted = redact(properties)

            // Checked on the raw values because only they still carry their types, but a hidden
            // value is only type-checked: a verdict on its enum or pattern would reveal it.
            val plan = Inspector.activePlan
            val hidden = redacted.filter { it.redacted }.mapTo(HashSet()) { it.name }
            val issues = plan?.issuesFor(name, channel, properties, hidden)

            val entry = AnalyticsEntry(
                id = Inspector.nextId(),
                timestamp = Inspector.now(),
                name = name,
                channel = channel,
                properties = redacted,
                groupLabel = groupLabel(name, redacted),
                sections = sections.filterValues { it.isNotEmpty() }.mapValues { (_, section) -> redact(section) },
                annotations = config.annotators
                    .flatMap {
                        runCatching { it(event) }
                            .onFailure { failure -> DiagnosticLog.warn(TAG, "annotator threw", failure) }
                            .getOrDefault(emptyList())
                    }
                    .distinct(),
                planVersion = plan?.version?.takeIf { issues != null },
                issues = issues.orEmpty(),
            )
            Inspector.recordAnalytics(entry)
            config.lineLogger?.let { logger -> writeLines(entry, logger) }
        }
    }

    private fun writeLines(entry: AnalyticsEntry, logger: (String) -> Unit) {
        runCatching { consoleLines(entry.id, encodeEntry(entry), consoleMaxBytes).forEach(logger) }
            .onFailure { failure -> DiagnosticLog.warn(TAG, "line logger threw", failure) }
    }

    private fun redact(properties: Map<String, Any?>): List<KeyValue> =
        properties.map { (key, value) -> redaction.redactProperty(key, value?.toString() ?: "null") }

    private fun groupLabel(name: String, properties: List<KeyValue>): String? {
        if (name != config.groupByEvent) return null
        val property = config.groupLabelProperty ?: return name
        return properties.firstOrNull { it.name == property }?.value ?: name
    }

    private companion object {
        const val TAG = "analytics"
    }
}

/** Build a [AnalyticsSink] logger. */
fun AnalyticsSink(configure: AnalyticsSinkConfig.() -> Unit = {}): AnalyticsSink =
    AnalyticsSink(AnalyticsSinkConfig().apply(configure))
