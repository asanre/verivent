package io.github.asanre.verivent.core.model

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
    val sections: Map<String, List<KeyValue>> = emptyMap(),
    val planVersion: String? = null,
    val issues: List<PlanIssue> = emptyList(),
) : TimelineEntry
