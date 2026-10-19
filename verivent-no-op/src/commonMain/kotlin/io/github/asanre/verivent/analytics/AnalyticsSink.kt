package com.snoop.analytics

/**
 * No-op mirror of the analytics sink in `snoop` with the identical public API for release builds. Create with
 * the [SnoopAnalytics] factory.
 */
class SnoopAnalytics internal constructor(@Suppress("UNUSED_PARAMETER") config: SnoopAnalyticsConfig) {
    fun log(
        name: String,
        channel: String,
        properties: Map<String, Any?> = emptyMap(),
        sections: Map<String, Map<String, Any?>> = emptyMap(),
    ) = Unit
}

fun SnoopAnalytics(configure: SnoopAnalyticsConfig.() -> Unit = {}): SnoopAnalytics =
    SnoopAnalytics(SnoopAnalyticsConfig().apply(configure))
