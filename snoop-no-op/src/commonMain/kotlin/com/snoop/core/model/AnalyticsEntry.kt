package com.snoop.core.model

import kotlin.time.Instant

/** No-op mirror of the analytics entry model. */
data class AnalyticsEntry(
    override val id: String,
    override val timestamp: Instant,
    val name: String,
    val channel: String,
    val properties: List<KeyValue> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val groupLabel: String? = null,
    val userProperties: List<KeyValue> = emptyList(),
) : SnoopEntry
