package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.displayText
import io.github.asanre.verivent.analytics.exportedValues
import io.github.asanre.verivent.analytics.joinAsChoice
import io.github.asanre.verivent.analytics.jsonEquals
import io.github.asanre.verivent.analytics.jsonOf
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.PlanEvent
import kotlinx.serialization.json.JsonElement
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
 * For an event sent in several ways (a generic event), only the latest of the loaded plan's
 * [variant], as stamped at capture (so only events captured with that plan loaded), and only one
 * sent with every value in [where], compared as JSON as in [EventCheck.shouldHave]: one sent after
 * it as another variant doesn't count.
 *
 * ```kotlin
 * lastEvent("ui_interaction" on "firebase", variant = "share_playlist").shouldHave("item_id", "pl_42")
 * ```
 *
 * @throws AssertionError when none was sent, or [variant] is given and no tracking plan is loaded.
 * @throws IllegalArgumentException when the loaded plan has no [variant] for [event], or a property
 * in [where] is redacted: only its type is known.
 */
fun lastEvent(
    event: PlanEvent,
    waiter: Waiter = Waiter.RealTime,
    timeout: Duration = 5.seconds,
    variant: String? = null,
    where: Map<String, Any?> = emptyMap(),
): EventCheck {
    if (variant != null) {
        val plan = Inspector.trackingPlan.value ?: throw AssertionError("${event.label}: no tracking plan is loaded to tell its variants apart")
        val planned = plan.variants[event].orEmpty()
        require(variant in planned) {
            "$variant isn't a variant of ${event.label} in tracking plan ${plan.version}" +
                if (planned.isEmpty()) ": it has none" else ": ${planned.joinAsChoice()}"
        }
    }
    val expected = where.mapValues { (_, value) -> jsonOf(value) }
    var found: AnalyticsEntry? = null
    waiter.until(timeout) {
        found = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>()
            .lastOrNull { it.key == event && (variant == null || it.variant == variant) && it.has(expected) }
        found != null
    }
    val filter = if (expected.isEmpty()) "" else " with " + expected.entries.joinToString { (name, value) -> "$name = ${value.displayText()}" }
    return EventCheck(found ?: throw AssertionError("${event.label(variant)}$filter wasn't sent within $timeout"))
}

/** Every property in [expected] was sent with its value. */
private fun AnalyticsEntry.has(expected: Map<String, JsonElement>): Boolean =
    expected.all { (property, value) -> sentValue(property)?.let { jsonEquals(it, value) } == true }

/**
 * [property] as sent, compared as JSON, or null when it wasn't sent.
 *
 * @throws IllegalArgumentException when it is redacted: only its type is known.
 */
private fun AnalyticsEntry.sentValue(property: String): JsonElement? {
    val sent = properties.firstOrNull { it.name == property } ?: return null
    require(!sent.redacted) { "$property is redacted: only its type can be checked" }
    return exportedValues(listOf(sent)).json.getValue(property)
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
        val sent = entry.sentValue(property) ?: fail("$property wasn't sent")
        val expected = jsonOf(value)
        if (!jsonEquals(sent, expected)) {
            fail("$property · Not the expected value · expects ${expected.displayText()}")
        }
    }

    private fun fail(why: String): Nothing = throw AssertionError("${entry.key.label(entry.variant)}: $why")
}
