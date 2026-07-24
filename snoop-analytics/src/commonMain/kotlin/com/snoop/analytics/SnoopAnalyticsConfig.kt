package com.snoop.analytics

import com.snoop.core.model.Annotation

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
