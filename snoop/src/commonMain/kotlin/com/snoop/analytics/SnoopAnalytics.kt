package com.snoop.analytics

import com.snoop.core.SnoopCore
import com.snoop.core.internal.SnoopLog
import com.snoop.core.internal.runSafely
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.model.KeyValue
import com.snoop.core.redaction.RedactionEngine
import com.snoop.web.encodeEntry

/**
 * Vendor-agnostic analytics sink. The host wires its own analytics facade to [log]; Snoop never
 * depends on any analytics SDK. Create with the [SnoopAnalytics] factory.
 */
class SnoopAnalytics internal constructor(private val config: SnoopAnalyticsConfig) {

    private val redaction = RedactionEngine(propertyPredicates = config.propertyRedactors)

    fun log(
        name: String,
        channel: String,
        properties: Map<String, Any?> = emptyMap(),
        sections: Map<String, Map<String, Any?>> = emptyMap(),
    ) {
        runSafely(TAG) {
            if (!SnoopCore.enabled) return@runSafely
            val event = AnalyticsEvent(name, channel, properties)
            // A throwing filter keeps the event visible (fail open): missing data is the worse bug.
            val visible = config.filters.all {
                runCatching { it(event) }
                    .onFailure { failure -> SnoopLog.warn(TAG, "filter threw; keeping the event", failure) }
                    .getOrDefault(true)
            }
            if (!visible) return@runSafely

            // Checked before redaction because only the raw values still carry their types.
            val plan = SnoopCore.trackingPlan
            val issues = plan?.issuesFor(name, channel, properties)
            val notInPlan = plan != null && issues == null

            // Labelled off the redacted list on purpose: a group must never surface a value the
            // host asked to hide, and the viewers already only ever read redacted properties.
            val redacted = redact(properties)

            val entry = AnalyticsEntry(
                id = SnoopCore.nextId(),
                timestamp = SnoopCore.now(),
                name = name,
                channel = channel,
                properties = redacted,
                groupLabel = groupLabel(name, redacted),
                sections = sections.filterValues { it.isNotEmpty() }.mapValues { (_, section) -> redact(section) },
                annotations = config.annotators
                    .flatMap {
                        runCatching { it(event) }
                            .onFailure { failure -> SnoopLog.warn(TAG, "annotator threw", failure) }
                            .getOrDefault(emptyList())
                    }
                    .let { if (notInPlan) it + Annotation.NotInPlan else it }
                    .distinct(),
                issues = issues.orEmpty(),
            )
            SnoopCore.recordAnalytics(entry)
            config.lineLogger?.let { logger -> writeLines(entry, logger) }
        }
    }

    private fun writeLines(entry: AnalyticsEntry, logger: (String) -> Unit) {
        runCatching { consoleLines(entry.id, encodeEntry(entry), consoleMaxBytes).forEach(logger) }
            .onFailure { failure -> SnoopLog.warn(TAG, "line logger threw", failure) }
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

/** Build a [SnoopAnalytics] logger. */
fun SnoopAnalytics(configure: SnoopAnalyticsConfig.() -> Unit = {}): SnoopAnalytics =
    SnoopAnalytics(SnoopAnalyticsConfig().apply(configure))
