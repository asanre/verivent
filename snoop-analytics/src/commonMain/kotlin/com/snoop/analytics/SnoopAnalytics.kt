package com.snoop.analytics

import com.snoop.core.SnoopCore
import com.snoop.core.internal.runSafely
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.redaction.RedactionEngine

/** The event as the host describes it at the call site, before capture/redaction. */
data class AnalyticsEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?> = emptyMap(),
)

/** DSL for configuring a [SnoopAnalytics] logger — filtering, redaction and semantic annotation. */
class SnoopAnalyticsConfig {

    internal val filters = mutableListOf<(AnalyticsEvent) -> Boolean>()
    internal val propertyRedactors = mutableListOf<(String) -> Boolean>()
    internal val annotators = mutableListOf<(AnalyticsEvent) -> List<Annotation>>()

    /** Keep only events for which every filter returns true. */
    fun filter(predicate: (AnalyticsEvent) -> Boolean) {
        filters += predicate
    }

    /** Redact matching property values with a visible placeholder. Matches on the property key. */
    fun sanitizeProperty(predicate: (String) -> Boolean) {
        propertyRedactors += predicate
    }

    /** Attach semantic badges (ecommerce, conversion, ...) shown on the event, à la Analytics Debugger. */
    fun annotate(block: (AnalyticsEvent) -> List<Annotation>) {
        annotators += block
    }
}

/**
 * Vendor-agnostic analytics sink. The host wires its own analytics facade to [log]; Snoop never
 * depends on any analytics SDK. Create with the [SnoopAnalytics] factory.
 */
class SnoopAnalytics internal constructor(private val config: SnoopAnalyticsConfig) {

    private val redaction = RedactionEngine(propertyPredicates = config.propertyRedactors)

    fun log(name: String, channel: String, properties: Map<String, Any?> = emptyMap()) {
        runSafely {
            if (!SnoopCore.enabled) return@runSafely
            val event = AnalyticsEvent(name, channel, properties)
            // A throwing filter keeps the event visible (fail open): missing data is the worse bug.
            if (config.filters.any { !runCatching { it(event) }.getOrDefault(true) }) return@runSafely

            SnoopCore.recordAnalytics(
                AnalyticsEntry(
                    id = SnoopCore.nextId(),
                    timestamp = SnoopCore.now(),
                    name = name,
                    channel = channel,
                    properties = properties.map { (key, value) ->
                        redaction.redactProperty(key, value?.toString() ?: "null")
                    },
                    annotations = config.annotators
                        .flatMap { runCatching { it(event) }.getOrDefault(emptyList()) }
                        .distinct(),
                ),
            )
        }
    }
}

/** Build a [SnoopAnalytics] logger. */
fun SnoopAnalytics(configure: SnoopAnalyticsConfig.() -> Unit = {}): SnoopAnalytics =
    SnoopAnalytics(SnoopAnalyticsConfig().apply(configure))
