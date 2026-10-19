package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.Annotation

/** No-op mirror of the analytics sink's config DSL: every hook is inert. */
class AnalyticsSinkConfig {
    fun filter(predicate: (AnalyticsEvent) -> Boolean) = Unit
    fun sanitizeProperty(predicate: (String) -> Boolean) = Unit
    fun annotate(block: (AnalyticsEvent) -> List<Annotation>) = Unit
    fun groupBy(event: String?, labelProperty: String? = null) = Unit
    fun logTo(logger: (String) -> Unit = {}) = Unit
}
