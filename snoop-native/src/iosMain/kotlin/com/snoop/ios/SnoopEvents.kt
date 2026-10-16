package com.snoop.ios

import com.snoop.analytics.SnoopAnalytics
import com.snoop.core.SnoopCore

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
     * Current grouping, so the Swift facade's defaults can preserve it across a reconfigure. Held
     * here rather than read back from the logger: the DSL's own defaults aren't exported to Swift,
     * so these have to restate them.
     */
    var groupByEvent: String? = "screen_view"
        private set
    var groupLabelProperty: String? = "screen_name"
        private set
    var logToConsole: Boolean = false
        private set

    /** Overload keeping the pre-grouping selector alive for direct Objective-C/Swift callers. */
    fun configure(
        keepEvent: ((SnoopEvent) -> Boolean)?,
        redactProperty: ((String) -> Boolean)?,
    ) = configure(keepEvent, redactProperty, groupByEvent, groupLabelProperty)

    /** Overload keeping the pre-console selector alive for direct Objective-C/Swift callers. */
    fun configure(
        keepEvent: ((SnoopEvent) -> Boolean)?,
        redactProperty: ((String) -> Boolean)?,
        groupByEvent: String?,
        groupLabelProperty: String?,
    ) = configure(keepEvent, redactProperty, groupByEvent, groupLabelProperty, logToConsole)

    /**
     * Install filtering, redaction and how the timeline is grouped. Both closures are optional —
     * null keeps every event and redacts nothing. Reconfiguring later only affects events captured
     * from then on, grouping included: the label is stamped on the event as it is captured.
     *
     * @param groupByEvent event that opens a new group in the viewer, or null for no grouping.
     * @param groupLabelProperty property of that event whose value labels the group; when null or
     *   absent the group takes the event's own name.
     * @param logToConsole also write each captured event as one JSON line to the unified log.
     */
    fun configure(
        keepEvent: ((SnoopEvent) -> Boolean)?,
        redactProperty: ((String) -> Boolean)?,
        groupByEvent: String?,
        groupLabelProperty: String?,
        logToConsole: Boolean,
    ) {
        this.groupByEvent = groupByEvent
        this.groupLabelProperty = groupLabelProperty
        this.logToConsole = logToConsole
        logger = SnoopAnalytics {
            keepEvent?.let { predicate ->
                filter { predicate(SnoopEvent(it.name, it.channel, it.properties)) }
            }
            redactProperty?.let { predicate -> sanitizeProperty(predicate) }
            groupBy(groupByEvent, groupLabelProperty)
            if (logToConsole) logTo()
        }
    }

    /** Where the loaded tracking plan is kept across launches; null keeps it in memory only. */
    var storageDir: String?
        get() = SnoopCore.storageDir
        set(value) = SnoopCore.configure(storageDir = value)

    /** Version of the loaded tracking plan, or null when none is loaded. */
    val trackingPlanVersion: String? get() = SnoopCore.trackingPlanVersion.value

    /** Validate events captured from now on against [json]; see `SnoopCore.loadTrackingPlan`. */
    @Throws(IllegalArgumentException::class)
    fun loadTrackingPlan(json: String) = SnoopCore.loadTrackingPlan(json)

    fun clearTrackingPlan() = SnoopCore.clearTrackingPlan()

    /** Record one event. Never throws — capture failures can't break the host's analytics call. */
    fun log(name: String, channel: String, properties: Map<String, Any?>) {
        logger.log(name, channel, properties)
    }
}
