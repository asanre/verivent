package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.core.model.PlanEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

infix fun String.on(channel: String): PlanEvent = PlanEvent(this, channel)

/** Nothing is captured, so there is never an event to check. */
fun lastEvent(
    event: PlanEvent,
    waiter: Waiter = Waiter.RealTime,
    timeout: Duration = 5.seconds,
    variant: String? = null,
    where: Map<String, Any?> = emptyMap(),
): EventCheck =
    throw AssertionError(NOTHING_CAPTURED)

class EventCheck internal constructor() {

    fun shouldMatchPlan(): EventCheck = this

    fun shouldHave(property: String, value: Any?): EventCheck = this
}
