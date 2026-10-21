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
    private val start = lastId()

    /**
     * Runs [action] as the baseline's next step, then waits until the events it sent match the
     * step (or, recording, until they stop arriving). Returns what [action] returns.
     *
     * @throws BaselineMismatch when they still don't match once the timeout runs out.
     */
    fun <T> step(name: String, action: () -> T): T {
        beginStep(name)
        return action().also { endStep() }
    }

    /**
     * A value the test knows only as it runs, named after the property it's delegated to:
     * `val orderId by bound(order.id)` stands for `{{orderId}}` in the baseline. Recording writes
     * `{{orderId}}` wherever the value was sent; comparing fills it in.
     */
    fun <T> bound(value: T): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> = PropertyDelegateProvider { _, property ->
        bind(property.name, value)
        ReadOnlyProperty { _, _ -> value }
    }

    internal fun bind(name: String, value: Any?) {
        variables[name] = value
    }

    /**
     * [step] in two calls, for a driver that runs the app's actions itself in between: an HTTP
     * test. [endStep] waits and checks, or records.
     */
    internal fun beginStep(name: String) {
        run.startStep(name, lastId())
    }

    /** Checks what's left once the flow ends, after the one implicit step of a flow without steps. */
    internal fun finish() {
        if (!run.anyStepStarted) {
            run.startStep(null, start)
            endStep()
        }
        if (recording) return
        val left = waitWhileOnlyMissing { run.close(analytics(), variables) }
        if (left.isNotEmpty()) throw BaselineMismatch(left)
    }

    /** @throws BaselineMismatch as [step] does. */
    internal fun endStep() {
        if (recording) {
            waitForQuiet()
            return run.recordStep(analytics(), variables)
        }
        val diff = waitWhileOnlyMissing { listOf(run.checkStep(analytics(), variables)).filterNot { it.passed } }
        if (diff.isNotEmpty()) throw BaselineMismatch(diff)
    }

    /**
     * The failing diffs [check] returns, checked again until one fails for a reason waiting can't
     * fix: only an event not sent yet can still arrive, while one sent wrong or once too often stays sent.
     */
    private fun waitWhileOnlyMissing(check: () -> List<BaselineDiff>): List<BaselineDiff> {
        var left = check()
        if (left.isNotEmpty() && left.all { it.onlyMissing }) waiter.until(timeout) { check().also { left = it }.none { it.onlyMissing } }
        return left
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

    private fun lastId() = Inspector.entriesSnapshot.maxOfOrNull { it.order } ?: 0
}

private val QUIET = 500.milliseconds

private val BaselineDiff.onlyMissing get() = findings.isNotEmpty() && findings.all { it is BaselineDiff.Finding.Missing }
