package io.github.asanre.verivent.baseline

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val NOTHING_CAPTURED = "Verivent no-op: nothing was captured"

/**
 * No-op mirror of the baseline. Nothing is captured, so [flow] never matches: a test run against
 * the no-op fails loudly instead of passing on an empty session.
 */
class Baseline internal constructor(val name: String) {

    val isRecording: Boolean = false

    fun flow(waiter: Waiter = Waiter.RealTime, timeout: Duration = 5.seconds, block: FlowScope.() -> Unit): Unit =
        throw AssertionError(NOTHING_CAPTURED)

    fun toJson(): String = """{ "flow": "$name", "events": [] }"""

    companion object {
        fun parse(json: String): Baseline = Baseline("")

        fun record(name: String): Baseline = Baseline(name)
    }
}
