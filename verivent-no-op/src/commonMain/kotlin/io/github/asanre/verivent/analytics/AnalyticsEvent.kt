package com.snoop.analytics

/** No-op mirror of the analytics sink's host-side event. */
data class AnalyticsEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?> = emptyMap(),
)
