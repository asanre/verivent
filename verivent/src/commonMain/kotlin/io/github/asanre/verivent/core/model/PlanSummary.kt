package io.github.asanre.verivent.core.model

/** The loaded tracking plan as the viewers show it: its version and the events it defines. */
data class PlanSummary(val version: String, val events: List<PlanEvent>)

data class PlanEvent(val name: String, val channel: String)
