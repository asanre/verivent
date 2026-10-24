package io.github.asanre.verivent.core.model

/**
 * The loaded tracking plan as the viewers show it: its version, the events it defines and, for an
 * event it tells apart by variants, their ids in the plan's order.
 */
data class PlanSummary(val version: String, val events: List<PlanEvent>, val variants: Map<PlanEvent, List<String>> = emptyMap())

data class PlanEvent(val name: String, val channel: String)
