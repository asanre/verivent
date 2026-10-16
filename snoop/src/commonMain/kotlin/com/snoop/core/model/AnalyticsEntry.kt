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
     * and a host that logs no analytics can never show grouping it couldn't produce.
     */
    val groupLabel: String? = null,
    /**
     * Extra parameter blocks the host attaches to the event (default parameters, user properties…),
     * by title in display order. Captured per event so the viewers need no state of their own; empty
     * blocks are never stored.
     */
    val sections: Map<String, List<KeyValue>> = emptyMap(),
    /**
     * Version of the tracking plan this event was checked against, stamped at capture; null when no
     * loaded plan defines it. With empty [issues] it means the event complies.
     */
    val planVersion: String? = null,
    /** What breaks the plan in this event's properties, stamped at capture alongside [planVersion]. */
    val issues: List<PlanIssue> = emptyList(),
) : SnoopEntry {

    /** Approximate retained size, cached — see [NetworkTransaction.approxBytes]. */
    internal val approxBytes: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (
            ENTRY_OVERHEAD_BYTES + name.length + channel.length + (groupLabel?.length ?: 0) +
                properties.sumOf { it.name.length + it.value.length } +
                sections.entries.sumOf { (title, section) ->
                    title.length + section.sumOf { it.name.length + it.value.length }
                } +
                (planVersion?.length ?: 0) + issues.sumOf { it.property.length + it.message.length }
            ).toLong()
    }
}
