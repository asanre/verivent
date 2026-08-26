package com.snoop.core.model

import kotlin.time.Instant

data class AnalyticsEntry(
    override val id: String,
    override val timestamp: Instant,
    val name: String,
    /** Free-form vendor/channel label ("firebase", "gtm", ...); the UI groups and colors by it. */
    val channel: String,
    val properties: List<KeyValue> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    /**
     * Non-null marks this event as opening a new timeline group, labelled with this value. Stamped
     * at capture by the analytics logger, so the viewers group without reading any configuration —
     * and a build without `snoop-analytics` can never show grouping it couldn't produce.
     */
    val groupLabel: String? = null,
) : SnoopEntry {

    /** Approximate retained size, cached — see [NetworkTransaction.approxBytes]. */
    internal val approxBytes: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (
            ENTRY_OVERHEAD_BYTES + name.length + channel.length + (groupLabel?.length ?: 0) +
                properties.sumOf { it.name.length + it.value.length }
            ).toLong()
    }
}
