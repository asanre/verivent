package com.snoop.analytics

/** No-op mirror of `snoop-analytics`'s host-side event. */
data class AnalyticsEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?> = emptyMap(),
)
