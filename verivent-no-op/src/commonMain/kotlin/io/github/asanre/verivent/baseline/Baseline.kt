package io.github.asanre.verivent.baseline

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val NOTHING_CAPTURED = "Verivent no-op: nothing was captured"

/**
 * No-op mirror of the baseline. Nothing is captured, so [flow] never matches: a test run against
 * the no-op fails loudly instead of passing on an empty session.
 */
class Baseline internal constructor(val name: String, private val json: String?) {

    val isRecording: Boolean get() = json == null

    fun flow(waiter: Waiter = Waiter.RealTime, timeout: Duration = 5.seconds, block: FlowScope.() -> Unit): Unit =
        throw AssertionError(NOTHING_CAPTURED)

    /** The file it was parsed from; a recording never has one, since [flow] records nothing. */
    fun toJson(): String = json ?: throw AssertionError(NOTHING_CAPTURED)

    companion object {
        fun parse(json: String): Baseline = Baseline(name = "", json)

        fun record(name: String): Baseline = Baseline(name, json = null)
    }
}
