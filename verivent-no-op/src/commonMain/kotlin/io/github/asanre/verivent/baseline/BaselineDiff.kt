package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue

data class BaselineDiff(val flow: String, val step: String?, val findings: List<Finding>) {

    val passed: Boolean get() = findings.isEmpty()

    sealed interface Finding {
        data class Missing(val event: PlanEvent) : Finding

        data class Extra(val event: PlanEvent) : Finding

        data class Mismatch(val event: PlanEvent, val issues: List<PlanIssue>) : Finding

        data class OutOfOrder(val event: PlanEvent, val before: PlanEvent?) : Finding

        data class StepNotRun(val step: String) : Finding
    }

    fun render(): String = step?.let { "$flow › $it" } ?: flow
}

class BaselineMismatch(val diffs: List<BaselineDiff>) : AssertionError(diffs.joinToString("\n\n") { it.render() })
