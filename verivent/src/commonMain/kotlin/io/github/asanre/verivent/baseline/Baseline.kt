package io.github.asanre.verivent.baseline

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The analytics a flow of the app must send, checked from a test:
 *
 * ```kotlin
 * Baseline.parse(readResource("checkout.json")).flow {
 *     tapAddToCart()
 *     tapPay()
 * }                                              // throws BaselineMismatch when they don't match
 * ```
 *
 * A baseline is recorded by the same test ([record], then [toJson]), polished by hand and approved
 * in review. Each expected event is the tracking plan's JSON Schema for its properties, recorded as
 * a `const` of what was sent: loosen one to a `type` or a `pattern`, or put `"{{name}}"` in it for
 * a value the test [binds][FlowScope.bound]. Events of a name and channel the baseline doesn't list
 * are ignored; the plan covers those. A longer flow splits into [steps][FlowScope.step]: each one a
 * set of events (or a `"sequence"`), in the order the test runs them.
 */
class Baseline internal constructor(
    /** The flow's name, as the file and any failure name it. */
    val name: String,
    private val file: BaselineFile?,
) {

    private var recorded: BaselineFile? = null

    /** Whether [flow] records what's sent rather than checking it. */
    val isRecording: Boolean get() = file == null

    /**
     * Runs [block] and checks, step by step, that the analytics it sends match this baseline,
     * waiting up to [timeout] for each step's on [waiter]; or, recording, records them.
     *
     * @throws BaselineMismatch when a step, or the flow as a whole, doesn't match.
     * @throws IllegalArgumentException when the test's steps aren't the baseline's, or a
     * `{{name}}` has no bound value.
     */
    fun flow(waiter: Waiter = Waiter.RealTime, timeout: Duration = 5.seconds, block: FlowScope.() -> Unit) {
        val run = FlowRun(name, file)
        FlowScope(run, isRecording, waiter, timeout).runFlow(block)
        if (isRecording) recorded = run.recording()
    }

    /** The baseline as its file holds it: what the last [flow] recorded, when [isRecording]. */
    fun toJson(): String = (file ?: checkNotNull(recorded) { "run the flow before asking for its recording" }).toJson()

    companion object {
        /** @throws IllegalArgumentException naming what is wrong with [json]. */
        fun parse(json: String): Baseline = BaselineFile.parse(json).let { Baseline(it.name, it) }

        /** A baseline for the flow [name] that [flow] fills in with what the app sends. */
        fun record(name: String): Baseline = Baseline(name, file = null)
    }
}
