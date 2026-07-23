package com.snoop.core.model

import kotlin.time.Instant

/** A semantic badge attached to an analytics event by the host's `annotate {}` rules. */
enum class Annotation { Ecommerce, Conversion, Consent, ScreenView }

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
