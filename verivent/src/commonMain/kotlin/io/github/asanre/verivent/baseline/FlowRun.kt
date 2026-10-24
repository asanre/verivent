package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.invalid
import io.github.asanre.verivent.baseline.BaselineDiff.Finding
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry

/**
 * One run of a flow, checked against [file] or, when it's null, recorded. Driven step by step and
 * handed the captured analytics each time, with no clock, Inspector or test framework inside, so a
 * driver other than [FlowScope] (an HTTP test) can run it too. A step's events are those captured
 * after the id it started at: ids only grow, so a step's window survives the oldest entries going.
 */
internal class FlowRun(private val name: String, private val file: BaselineFile?) {

    private class Started(val step: BaselineFile.Step?, val name: String?, val afterId: Long)

    private val started = mutableListOf<Started>()
    private val recorded = mutableListOf<BaselineFile.Step>()

    private val current get() = started.lastOrNull() ?: invalid("no step has started in $name")

    val anyStepStarted: Boolean get() = started.isNotEmpty()

    val isRecording: Boolean get() = file == null

    /**
     * Starts the next step, null for a flow without steps.
     *
     * @throws IllegalArgumentException when the baseline has no such step next.
     */
    fun startStep(stepName: String?, afterId: Long) {
        val step = file?.let { file ->
            if (file.implicit != (stepName == null)) {
                invalid(if (file.implicit) "the baseline of $name has no steps: drop step(\"$stepName\")" else "the baseline of $name has steps: run them with step()")
            }
            val next = file.steps.getOrNull(started.size)
            if (next == null || next.name != stepName) {
                invalid(
                    when {
                        file.steps.none { it.name == stepName } -> "the baseline of $name has no step $stepName"
                        next == null -> "step $stepName already ran"
                        else -> "step $stepName runs out of order: the baseline has ${next.name} next"
                    },
                )
            }
            next
        }
        if (file == null && stepName != null && started.any { it.name == stepName }) invalid("step $stepName already ran")
        started += Started(step, stepName, afterId)
    }

    /** The current step's own events against [entries]; deferred ones wait for [close]. */
    fun checkStep(entries: List<AnalyticsEntry>, variables: Map<String, Any?>): BaselineDiff {
        val step = checkNotNull(current.step) { "a recording has nothing to check" }
        val immediate = step.events.filterNot { it.deferred }
        return BaselineDiff(name, step.name, compareStep(immediate, step.sequence, current.window(entries), variables, Inspector.activePlan))
    }

    fun recordStep(entries: List<AnalyticsEntry>, variables: Map<String, Any?>) {
        check(file == null) { "only a recording records" }
        recorded += BaselineFile.Step(current.name, sequence = false, events = recordEvents(current.window(entries), variables))
    }

    /**
     * What's left once the flow ends, failing diffs only: each step's deferred events against
     * everything sent since it started, steps that never ran, and events sent more often over the
     * whole flow than its steps list them, which a step that ignores their name can't see.
     */
    fun close(entries: List<AnalyticsEntry>, variables: Map<String, Any?>): List<BaselineDiff> {
        val file = checkNotNull(file) { "a recording has nothing to check" }
        val deferred = started.mapNotNull { run ->
            val step = run.step ?: return@mapNotNull null
            val events = step.events.filter { it.deferred }.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            BaselineDiff(name, step.name, compareStep(events, sequence = false, run.window(entries), variables, Inspector.activePlan))
        }
        val notRun = file.steps.drop(started.size).mapNotNull { it.name }.map { Finding.StepNotRun(it) }
        val wholeFlow = BaselineDiff(name, null, notRun + extraOverTheFlow(entries))
        return (deferred + wholeFlow).filterNot { it.passed }
    }

    fun recording(): BaselineFile = BaselineFile(name, recorded.toList())

    private fun extraOverTheFlow(entries: List<AnalyticsEntry>): List<Finding> {
        val ran = started.mapNotNull { it.step }.flatMap { it.events }
        val deferredKeys = ran.filter { it.deferred }.mapTo(HashSet()) { it.key }
        val expected = ran.filterNot { it.key in deferredKeys }.groupingBy { it.key }.eachCount()
        val sent = started.firstOrNull()?.window(entries).orEmpty().filter { it.key in expected }.groupingBy { it.key }.eachCount()
        return sent.flatMap { (key, count) -> List((count - expected.getValue(key)).coerceAtLeast(0)) { Finding.Extra(key) } }
    }

    private fun Started.window(entries: List<AnalyticsEntry>) = entries.filter { it.order > afterId }
}
