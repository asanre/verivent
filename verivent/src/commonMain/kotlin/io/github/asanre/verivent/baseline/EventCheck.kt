package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.displayText
import io.github.asanre.verivent.analytics.exportedValues
import io.github.asanre.verivent.analytics.jsonEquals
import io.github.asanre.verivent.analytics.jsonOf
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.PlanEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** An event's name and channel: `"purchase" on "firebase"`. */
infix fun String.on(channel: String): PlanEvent = PlanEvent(this, channel)

/**
 * The latest [event] captured, waiting up to [timeout] for one on [waiter], to check on its own
 * without a baseline:
 *
 * ```kotlin
 * lastEvent("purchase" on "firebase").shouldMatchPlan().shouldHave("currency", "EUR")
 * ```
 *
 * @throws AssertionError when none was sent.
 */
fun lastEvent(event: PlanEvent, waiter: Waiter = Waiter.RealTime, timeout: Duration = 5.seconds): EventCheck {
    var found: AnalyticsEntry? = null
    waiter.until(timeout) {
        found = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>().lastOrNull { it.key == event }
        found != null
    }
    return EventCheck(found ?: throw AssertionError("${event.label} wasn't sent within $timeout"))
}

/** Checks on one captured event, chained; each throws [AssertionError] naming what was expected. */
class EventCheck internal constructor(private val entry: AnalyticsEntry) {

    /** The tracking plan loaded now checked this event and found nothing wrong. */
    fun shouldMatchPlan(): EventCheck = apply {
        val plan = Inspector.trackingPlan.value ?: fail("no tracking plan is loaded")
        if (entry.planVersion != plan.version) fail("not checked by tracking plan ${plan.version}")
        if (entry.issues.isNotEmpty()) fail("breaks the plan" + entry.issues.joinToString("") { "\n  ${it.property} · ${it.message}" })
    }

    /**
     * [property] was sent with [value], compared as JSON (`3` equals `3.0`, not `"3"`).
     *
     * @throws IllegalArgumentException when [property] is redacted: only its type is known.
     */
    fun shouldHave(property: String, value: Any?): EventCheck = apply {
        val sent = entry.properties.firstOrNull { it.name == property } ?: fail("$property wasn't sent")
        require(!sent.redacted) { "$property is redacted: only its type can be checked" }
        val expected = jsonOf(value)
        if (!jsonEquals(exportedValues(listOf(sent)).json.getValue(property), expected)) {
            fail("$property · Not the expected value · expects ${expected.displayText()}")
        }
    }

    private fun fail(why: String): Nothing = throw AssertionError("${entry.key.label}: $why")
}
