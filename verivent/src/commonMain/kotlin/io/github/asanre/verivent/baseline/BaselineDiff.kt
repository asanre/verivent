package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue

/**
 * What a step of [flow] sent against what its baseline expects; [passed] when nothing is left over.
 * [step] is null for a flow without steps, and for the checks on the whole flow when it closes.
 * Findings name what was expected, never a value sent. A finding on an event the loaded tracking
 * plan tells apart by variants says which one, by its id; null without a plan or when none fits.
 */
data class BaselineDiff(val flow: String, val step: String?, val findings: List<Finding>) {

    val passed: Boolean get() = findings.isEmpty()

    sealed interface Finding {
        /** Expected, and nothing of its name and channel (and [variant]) was sent. */
        data class Missing(val event: PlanEvent, val variant: String? = null) : Finding

        /** Sent more times than the baseline lists it: a second firing, say. */
        data class Extra(val event: PlanEvent, val variant: String? = null) : Finding

        /** Sent, but not as expected: [issues] say where, by property path. */
        data class Mismatch(val event: PlanEvent, val issues: List<PlanIssue>, val variant: String? = null) : Finding

        /**
         * Sent as expected, but not in the step's `sequence` order: it should come before [before]
         * (its [beforeVariant]), or last.
         */
        data class OutOfOrder(
            val event: PlanEvent,
            val before: PlanEvent?,
            val variant: String? = null,
            val beforeVariant: String? = null,
        ) : Finding

        /** A step of the baseline the flow never ran. */
        data class StepNotRun(val step: String) : Finding
    }

    /** As a test failure reads it: the flow and step, then one block per finding. */
    fun render(): String = buildString {
        append(if (step == null) flow else "$flow › $step")
        findings.forEach { finding ->
            appendLine()
            when (finding) {
                is Finding.Missing -> append("  ✕ ${finding.event.label(finding.variant)}  missing")
                is Finding.Extra -> append("  ✕ ${finding.event.label(finding.variant)}  sent more times than expected")
                is Finding.OutOfOrder -> {
                    append("  ✕ ${finding.event.label(finding.variant)}  out of order, expected ")
                    append(finding.before?.let { "before ${it.label(finding.beforeVariant)}" } ?: "last")
                }
                is Finding.StepNotRun -> append("  ✕ step ${finding.step}  never ran")
                is Finding.Mismatch -> {
                    append("  ✕ ${finding.event.label(finding.variant)}")
                    finding.issues.forEach { append("\n      ${it.property} · ${it.message}") }
                }
            }
        }
    }
}

/** A flow that doesn't match its baseline. [diffs] are the failing ones, as [message] renders them. */
class BaselineMismatch(val diffs: List<BaselineDiff>) : AssertionError(diffs.joinToString("\n\n") { it.render() })

internal val PlanEvent.label get() = "$name · $channel"

/** [label], then the [variant] when there is one: `ui_interaction · firebase › share_playlist`. */
internal fun PlanEvent.label(variant: String?) = if (variant == null) label else "$label › $variant"
