package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.Annotation

/** DSL for configuring a [AnalyticsSink] logger — filtering, redaction, annotation and grouping. */
class AnalyticsSinkConfig {

    internal val filters = mutableListOf<(AnalyticsEvent) -> Boolean>()
    internal val propertyRedactors = mutableListOf<(String) -> Boolean>()
    internal val annotators = mutableListOf<(AnalyticsEvent) -> List<Annotation>>()

    internal var groupByEvent: String? = "screen_view"
        private set
    internal var groupLabelProperty: String? = "screen_name"
        private set

    internal var lineLogger: ((String) -> Unit)? = null
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

    /**
     * Also write every captured event as one JSON line, so whoever reads the console (a developer,
     * an agent driving `adb logcat -s VeriventEvent`) sees what was sent. Off unless called; with no
     * argument it writes to Logcat on Android and the unified log on iOS. Pass [logger] to route
     * the lines through the host's own logging.
     *
     * The line is the captured entry, the same JSON the web viewer receives, so redacted values stay
     * redacted. One longer than the platform's console limit is split into pieces prefixed with
     * `[<id> <i>/<n>] `, to be joined in order.
     */
    fun logTo(logger: (String) -> Unit = ::platformConsole) {
        lineLogger = logger
    }
}
