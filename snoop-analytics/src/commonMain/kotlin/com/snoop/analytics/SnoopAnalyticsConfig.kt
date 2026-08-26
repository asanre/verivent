package com.snoop.analytics

import com.snoop.core.model.Annotation

/** DSL for configuring a [SnoopAnalytics] logger — filtering, redaction, annotation and grouping. */
class SnoopAnalyticsConfig {

    internal val filters = mutableListOf<(AnalyticsEvent) -> Boolean>()
    internal val propertyRedactors = mutableListOf<(String) -> Boolean>()
    internal val annotators = mutableListOf<(AnalyticsEvent) -> List<Annotation>>()

    internal var groupByEvent: String? = "screen_view"
        private set
    internal var groupLabelProperty: String? = "screen_name"
        private set

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

    /**
     * Which event opens a new collapsible group in the viewers' timeline, and which of its
     * properties labels that group. Defaults to Firebase's manual-`screen_view` convention, so
     * grouping works without calling this; pass a null [event] to turn it off. When
     * [labelProperty] is null, or the event doesn't carry it, the group takes the event's own name.
     *
     * The decision is made once, at capture: the matching event is stamped and the viewers group by
     * that stamp. Changing it later only affects events captured from then on.
     */
    fun groupBy(event: String?, labelProperty: String? = null) {
        groupByEvent = event
        groupLabelProperty = labelProperty
    }
}
