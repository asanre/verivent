package com.snoop.analytics

import com.snoop.core.model.Annotation

/** No-op mirror of the analytics sink's config DSL: every hook is inert. */
class SnoopAnalyticsConfig {
    fun filter(predicate: (AnalyticsEvent) -> Boolean) = Unit
    fun sanitizeProperty(predicate: (String) -> Boolean) = Unit
    fun annotate(block: (AnalyticsEvent) -> List<Annotation>) = Unit
    fun groupBy(event: String?, labelProperty: String? = null) = Unit
}
