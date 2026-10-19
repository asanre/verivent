package io.github.asanre.verivent.core.model

/**
 * One way an event breaks the loaded tracking plan. [message] states the plan's expectation for
 * [property] but never the value sent, which may be one the host redacts.
 */
data class PlanIssue(val property: String, val message: String)
