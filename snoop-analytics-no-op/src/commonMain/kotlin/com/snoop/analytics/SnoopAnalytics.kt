package com.snoop.analytics

import com.snoop.core.model.Annotation

/** No-op mirror of `snoop-analytics` with the identical public API for release builds. */
data class AnalyticsEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?> = emptyMap(),
)

class SnoopAnalyticsConfig {
    fun filter(predicate: (AnalyticsEvent) -> Boolean) = Unit
    fun sanitizeProperty(predicate: (String) -> Boolean) = Unit
    fun annotate(block: (AnalyticsEvent) -> List<Annotation>) = Unit
}

class SnoopAnalytics internal constructor(@Suppress("UNUSED_PARAMETER") config: SnoopAnalyticsConfig) {
    fun log(name: String, channel: String, properties: Map<String, Any?> = emptyMap()) = Unit
}

fun SnoopAnalytics(configure: SnoopAnalyticsConfig.() -> Unit = {}): SnoopAnalytics =
    SnoopAnalytics(SnoopAnalyticsConfig().apply(configure))
