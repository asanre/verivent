package com.snoop.core.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * A semantic badge attached to an analytics event by the host's `annotate {}` rules. Open-ended:
 * hosts can mint their own (`Annotation("checkout")`); the well-known ones below get dedicated
 * styling in the UI, everything else renders neutral.
 */
@JvmInline
value class Annotation(val label: String) {
    companion object {
        val Ecommerce = Annotation("ecommerce")
        val Conversion = Annotation("conversion")
        val Consent = Annotation("consent")
        val ScreenView = Annotation("screen")
    }
}

data class AnalyticsEntry(
    override val id: String,
    override val timestamp: Instant,
    val name: String,
    /** Free-form vendor/channel label ("firebase", "gtm", ...); the UI groups and colors by it. */
    val channel: String,
    val properties: List<KeyValue> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    /** Set when this event was sent over a network call marked as analytics transport. */
    val correlatedCallId: String? = null,
) : SnoopEntry
