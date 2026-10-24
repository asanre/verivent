package io.github.asanre.verivent.core.model

/** No-op mirror of the loaded tracking plan's summary. */
data class PlanSummary(val version: String, val events: List<PlanEvent>, val variants: Map<PlanEvent, List<String>> = emptyMap())

data class PlanEvent(val name: String, val channel: String)
