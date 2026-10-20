package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** The body of [Baseline.flow]: the test's own actions, optionally split into [step]s. */
class FlowScope internal constructor(
    private val run: FlowRun,
    private val recording: Boolean,
    private val waiter: Waiter,
    private val timeout: Duration,
) {
    private val variables = LinkedHashMap<String, Any?>()
    private var steps = 0

    /**
     * Runs [action] as the baseline's next step, then waits until the events it sent match the
     * step (or, recording, until they stop arriving). Returns what [action] returns.
     *
     * @throws BaselineMismatch when they still don't match once the timeout runs out.
     */
    fun <T> step(name: String, action: () -> T): T {
        run.startStep(name, lastId())
        steps++
        return action().also { settle() }
    }

    /**
     * A value the test knows only as it runs, named after the property it's delegated to:
     * `val orderId by bound(order.id)` stands for `{{orderId}}` in the baseline. Recording writes
     * `{{orderId}}` wherever the value was sent; comparing fills it in.
     */
    fun <T> bound(value: T): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> = PropertyDelegateProvider { _, property ->
        variables[property.name] = value
        ReadOnlyProperty { _, _ -> value }
    }

    internal fun runFlow(block: FlowScope.() -> Unit) {
        val start = lastId()
        block()
        if (steps == 0) {
            run.startStep(null, start)
            settle()
        }
        if (recording) return
        var left = run.close(analytics(), variables)
        if (left.isNotEmpty() && left.all { it.onlyMissing }) waiter.until(timeout) { run.close(analytics(), variables).also { left = it }.none { it.onlyMissing } }
        if (left.isNotEmpty()) throw BaselineMismatch(left)
    }

    private fun settle() {
        if (recording) {
            waitForQuiet()
            return run.recordStep(analytics(), variables)
        }
        var diff = run.checkStep(analytics(), variables)
        if (diff.onlyMissing) waiter.until(timeout) { run.checkStep(analytics(), variables).also { diff = it }.let { !it.onlyMissing } }
        if (!diff.passed) throw BaselineMismatch(listOf(diff))
    }

    /** Until nothing new was captured for a while, or the timeout: a recording can't know what to expect. */
    private fun waitForQuiet() {
        var seen = lastId()
        var since = TimeSource.Monotonic.markNow()
        waiter.until(timeout) {
            val now = lastId()
            if (now != seen) {
                seen = now
                since = TimeSource.Monotonic.markNow()
            }
            since.elapsedNow() >= QUIET
        }
    }

    private fun analytics() = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>()

    private fun lastId() = Inspector.entriesSnapshot.maxOfOrNull { it.id.toLongOrNull() ?: 0 } ?: 0
}

private val QUIET = 500.milliseconds

/**
 * Whether waiting could still make this diff pass: only an event not sent yet can still arrive. One
 * sent wrong, or once too often, stays sent.
 */
private val BaselineDiff.onlyMissing get() = findings.isNotEmpty() && findings.all { it is BaselineDiff.Finding.Missing }
