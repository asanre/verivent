package com.snoop.analytics

import com.snoop.core.SnoopCore
import com.snoop.core.internal.SnoopLog
import com.snoop.core.internal.runSafely
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.redaction.RedactionEngine

/**
 * Vendor-agnostic analytics sink. The host wires its own analytics facade to [log]; Snoop never
 * depends on any analytics SDK. Create with the [SnoopAnalytics] factory.
 */
class SnoopAnalytics internal constructor(private val config: SnoopAnalyticsConfig) {

    private val redaction = RedactionEngine(propertyPredicates = config.propertyRedactors)

    fun log(name: String, channel: String, properties: Map<String, Any?> = emptyMap()) {
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
                        .flatMap {
                            runCatching { it(event) }
                                .onFailure { failure -> SnoopLog.warn(TAG, "annotator threw", failure) }
                                .getOrDefault(emptyList())
                        }
                        .distinct(),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "analytics"
    }
}

/** Build a [SnoopAnalytics] logger. */
fun SnoopAnalytics(configure: SnoopAnalyticsConfig.() -> Unit = {}): SnoopAnalytics =
    SnoopAnalytics(SnoopAnalyticsConfig().apply(configure))
