package com.snoop.ios

import com.snoop.analytics.SnoopAnalytics

/**
 * Analytics ingestion for native iOS hosts: wire your own analytics facade to [log], the same way a
 * Compose Multiplatform host wires `SnoopAnalytics`.
 *
 * Exists because the Kotlin DSL doesn't survive the trip to Objective-C: the `SnoopAnalytics {}`
 * factory takes a lambda with receiver, which Swift sees as an opaque block over an exported config
 * class. Here the same options are plain closures.
 */
object SnoopEvents {

    private var logger = SnoopAnalytics()

    /**
     * Install filtering and redaction. Both closures are optional — null keeps every event and
     * redacts nothing. Call once during startup, before the first [log]: reconfiguring later only
     * affects events captured from then on.
     */
    fun configure(keepEvent: ((SnoopEvent) -> Boolean)?, redactProperty: ((String) -> Boolean)?) {
        logger = SnoopAnalytics {
            keepEvent?.let { predicate ->
                filter { predicate(SnoopEvent(it.name, it.channel, it.properties)) }
            }
            redactProperty?.let { predicate -> sanitizeProperty(predicate) }
        }
    }

    /** Record one event. Never throws — capture failures can't break the host's analytics call. */
    fun log(name: String, channel: String, properties: Map<String, Any?>) {
        logger.log(name, channel, properties)
    }
}
