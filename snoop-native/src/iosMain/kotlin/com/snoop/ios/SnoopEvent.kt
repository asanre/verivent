package com.snoop.ios

/**
 * An event as the host describes it, handed to the filter in [SnoopEvents.configure].
 *
 * Mirrors `AnalyticsEvent` rather than reusing it: the bridge owns every type in its public surface
 * so nothing from snoop-core or snoop-analytics reaches the generated Objective-C header.
 */
data class SnoopEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?>,
)
