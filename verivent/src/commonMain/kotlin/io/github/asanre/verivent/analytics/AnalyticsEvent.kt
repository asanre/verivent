package io.github.asanre.verivent.analytics

/** The event as the host describes it at the call site, before capture/redaction. */
data class AnalyticsEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?> = emptyMap(),
)
