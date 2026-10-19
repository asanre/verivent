package io.github.asanre.verivent.core.model

/**
 * One way an event breaks the loaded tracking plan. [message] states the plan's expectation for
 * [property] but never the value sent, which may be one the host redacts.
 *
 * [property] is a path when the issue lies inside a value: `.` into a map and `[*]` into every
 * element of a list, as in `tracks[*].duration_ms`. Then [message] ends with how many elements
 * break it, as in `Expected integer, got string · 60 of 60`.
 */
data class PlanIssue(val property: String, val message: String)
