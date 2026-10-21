package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.baseline.BaselineDiff.Finding
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.PlanIssue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaselineTest {

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() = Inspector.reset()

    private val sink = AnalyticsSink { sanitizeProperty { it == "email" } }

    // Logging is synchronous, so one look is enough.
    private val now = Waiter { _, condition -> condition() }

    private fun screen(name: String = "cart") = sink.log("screen_view", "firebase", mapOf("screen_name" to name))

    private fun addToCart(itemId: String = "42", quantity: Any = 2) =
        sink.log("add_to_cart", "firebase", mapOf("item_id" to itemId, "quantity" to quantity, "email" to "a@b.c"))

    private fun purchase(itemId: String = "42", quantity: Any = 2) =
        sink.log("purchase", "firebase", mapOf("value" to 9.5, "items" to listOf(mapOf("id" to itemId, "qty" to quantity))))

    private fun checkout(itemId: String = "42", quantity: Any = 2) {
        screen()
        addToCart(itemId, quantity)
        purchase(itemId, quantity)
    }

    private fun recorded(block: FlowScope.() -> Unit): String =
        Baseline.record("checkout").apply { flow(now, block = block) }.toJson().also { Inspector.reset() }

    private fun Baseline.mismatch(block: FlowScope.() -> Unit): BaselineMismatch = assertFailsWith { flow(now, block = block) }

    @Test
    fun aFlowMatchesTheBaselineRecordedFromIt() {
        val baseline = recorded { checkout() }

        Baseline.parse(baseline).flow(now) { checkout() }
    }

    @Test
    fun aParameterBrokenOnPurposeNamesTheEventAndPath() {
        val baseline = Baseline.parse(recorded { checkout() })

        val failure = baseline.mismatch { checkout(quantity = "2") }

        assertEquals(
            listOf(
                Finding.Mismatch("add_to_cart" on "firebase", listOf(PlanIssue("quantity", "Not the expected value · expects 2"))),
                Finding.Mismatch("purchase" on "firebase", listOf(PlanIssue("items[*].qty", "Expected integer, got string · 1 of 1"))),
            ),
            failure.diffs.single().findings,
        )
        assertEquals(
            """
            checkout
              ✕ add_to_cart · firebase
                  quantity · Not the expected value · expects 2
              ✕ purchase · firebase
                  items[*].qty · Expected integer, got string · 1 of 1
            """.trimIndent(),
            failure.message,
        )
    }

    @Test
    fun noFailureQuotesAValueSent() {
        val baseline = Baseline.parse(recorded { checkout() })

        val failure = baseline.mismatch { checkout(itemId = "secret-99", quantity = 777) }

        assertFalse("secret-99" in failure.message.orEmpty() || "777" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aBoundValueIsRecordedAsItsVariableAndFilledInToCompare() {
        val json = recorded {
            val itemId by bound("42")
            checkout(itemId)
        }
        assertTrue(""""const": "{{itemId}}"""" in json, json)
        assertTrue(""""contains"""" in json, json)

        Baseline.parse(json).flow(now) {
            val itemId by bound("43")
            checkout(itemId)
        }
    }

    @Test
    fun aMismatchOnABoundValueNamesTheVariableNotTheValue() {
        val baseline = Baseline.parse(recorded { val itemId by bound("42"); checkout(itemId) })

        val failure = baseline.mismatch {
            val itemId by bound("43")
            checkout("44")
        }

        assertTrue("item_id · Not the expected value · expects {{itemId}}" in failure.message.orEmpty(), failure.message)
        assertFalse("43" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aVariableWithoutAValueFailsLoudly() {
        val baseline = Baseline.parse(recorded { val itemId by bound("42"); checkout(itemId) })

        val failure = assertFailsWith<IllegalArgumentException> { baseline.flow(now) { checkout() } }
        assertEquals("{{itemId}} has no value: bind it in the flow", failure.message)
    }

    @Test
    fun twoVariablesBoundToTheSameValueCantBeRecorded() {
        val failure = assertFailsWith<IllegalArgumentException> {
            recorded {
                val itemId by bound("42")
                val sku by bound("42")
                checkout(itemId)
            }
        }
        assertEquals("itemId and sku are bound to the same value: the recording can't tell them apart", failure.message)
    }

    @Test
    fun anEventFiredTwiceIsExtra() {
        val baseline = Baseline.parse(recorded { checkout() })

        val failure = baseline.mismatch { checkout(); addToCart() }

        assertEquals(listOf(Finding.Extra("add_to_cart" on "firebase")), failure.diffs.single().findings)
    }

    @Test
    fun anEventNeverSentIsMissing() {
        val baseline = Baseline.parse(recorded { checkout() })

        val failure = baseline.mismatch { screen(); purchase() }

        assertEquals(listOf(Finding.Missing("add_to_cart" on "firebase")), failure.diffs.single().findings)
    }

    @Test
    fun eventsTheBaselineDoesntListAreIgnored() {
        val baseline = Baseline.parse(recorded { addToCart() })

        baseline.flow(now) { screen(); addToCart(); purchase() }
    }

    @Test
    fun aRedactedValueIsOnlyRecordedAndComparedByType() {
        val json = recorded { addToCart() }
        assertTrue(Regex(""""email":\s*\{\s*"type":\s*"string"\s*\}""").containsMatchIn(json), json)

        Baseline.parse(json).flow(now) {
            sink.log("add_to_cart", "firebase", mapOf("item_id" to "42", "quantity" to 2, "email" to "other@b.c"))
        }
    }

    @Test
    fun stepsAreRecordedAndCheckedInTheirOrder() {
        val json = recorded {
            step("browse") { screen() }
            step("buy") { addToCart(); purchase() }
        }
        assertTrue(""""steps"""" in json, json)

        val value = Baseline.parse(json)
        var returned = ""
        value.flow(now) {
            step("browse") { screen() }
            returned = step("buy") { addToCart(); purchase(); "done" }
        }
        assertEquals("done", returned)
    }

    @Test
    fun aFailingStepIsNamed() {
        val baseline = Baseline.parse(recorded { step("browse") { screen() }; step("buy") { addToCart() } })

        val failure = baseline.mismatch {
            step("browse") { screen() }
            step("buy") { addToCart(quantity = 3) }
        }

        assertEquals("buy", failure.diffs.single().step)
        assertTrue(failure.message.orEmpty().startsWith("checkout › buy\n"), failure.message)
    }

    @Test
    fun theTestsStepsMustBeTheBaselines() {
        val baseline = Baseline.parse(recorded { step("browse") { screen() }; step("buy") { addToCart() } })

        assertEquals(
            "the baseline of checkout has no step pay",
            assertFailsWith<IllegalArgumentException> { baseline.flow(now) { step("pay") {} } }.message,
        )
        assertEquals(
            "step buy runs out of order: the baseline has browse next",
            assertFailsWith<IllegalArgumentException> { baseline.flow(now) { step("buy") {} } }.message,
        )
        assertEquals(
            "the baseline of checkout has steps: run them with step()",
            assertFailsWith<IllegalArgumentException> { baseline.flow(now) { screen() } }.message,
        )
        assertEquals(
            listOf(Finding.StepNotRun("buy")),
            baseline.mismatch { step("browse") { screen() } }.diffs.single().findings,
        )
    }

    @Test
    fun aDuplicateSentInALaterStepThatDoesntListItIsCaughtAtTheEnd() {
        val baseline = Baseline.parse(recorded { step("browse") { addToCart() }; step("buy") { purchase() } })

        val failure = baseline.mismatch {
            step("browse") { addToCart() }
            step("buy") { purchase(); addToCart() }
        }

        assertEquals(BaselineDiff("checkout", null, listOf(Finding.Extra("add_to_cart" on "firebase"))), failure.diffs.single())
    }

    @Test
    fun aDeferredEventIsCheckedWhenTheFlowEndsAgainstEverythingSinceItsStep() {
        val baseline = Baseline.parse(
            """
            { "flow": "checkout", "steps": [
                { "name": "browse", "events": [
                    { "name": "screen_view", "channel": "firebase", "schema": {} },
                    { "name": "purchase", "channel": "firebase", "deferred": true, "schema": {} } ] },
                { "name": "buy", "events": [ { "name": "add_to_cart", "channel": "firebase", "schema": {} } ] } ] }
            """,
        )

        baseline.flow(now) {
            step("browse") { screen() }
            step("buy") { addToCart(); purchase() }
        }
        Inspector.reset()
        val failure = baseline.mismatch {
            step("browse") { screen() }
            step("buy") { addToCart() }
        }
        assertEquals(BaselineDiff("checkout", "browse", listOf(Finding.Missing("purchase" on "firebase"))), failure.diffs.single())
    }

    @Test
    fun aSequenceStepReportsTheEventOutOfOrder() {
        val baseline = Baseline.parse(
            """
            { "flow": "checkout", "order": "sequence", "events": [
                { "name": "screen_view", "channel": "firebase", "schema": {} },
                { "name": "add_to_cart", "channel": "firebase", "schema": {} },
                { "name": "purchase", "channel": "firebase", "schema": {} } ] }
            """,
        )

        val failure = baseline.mismatch { screen(); purchase(); addToCart() }

        assertEquals(
            listOf(Finding.OutOfOrder("add_to_cart" on "firebase", before = "purchase" on "firebase")),
            failure.diffs.single().findings,
        )
    }

    @Test
    fun anEventOutOfOrderIsPlacedBeforeTheNextOneThatArrived() {
        val baseline = Baseline.parse(
            """
            { "flow": "checkout", "order": "sequence", "events": [
                { "name": "add_to_cart", "channel": "firebase", "schema": {} },
                { "name": "refund", "channel": "firebase", "schema": {} },
                { "name": "purchase", "channel": "firebase", "schema": {} } ] }
            """,
        )

        val failure = baseline.mismatch { purchase(); addToCart() }

        assertEquals(
            listOf(Finding.OutOfOrder("add_to_cart" on "firebase", before = "purchase" on "firebase"), Finding.Missing("refund" on "firebase")),
            failure.diffs.single().findings,
        )
    }

    @Test
    fun aListIsRecordedByShapeAndContainsChecksTheKnownItem() {
        val baseline = Baseline.parse(recorded { val itemId by bound("42"); purchase(itemId) })

        baseline.flow(now) { val itemId by bound("7"); purchase("7") }
        Inspector.reset()
        val failure = baseline.mismatch { val itemId by bound("7"); purchase("8") }
        assertEquals(
            listOf(Finding.Mismatch("purchase" on "firebase", listOf(PlanIssue("items", "No item matches the expected one")))),
            failure.diffs.single().findings,
        )
    }

    @Test
    fun aFileThatIsntABaselineFailsNamingWhy() {
        fun parseError(json: String) = assertFailsWith<IllegalArgumentException> { Baseline.parse(json) }.message

        assertEquals("recorded with Verivent 0.9: record it again", parseError("""{ "baseline": 1, "events": [] }"""))
        assertEquals(
            "'checkpoints' is not a baseline key: expects flow, order, events, steps",
            parseError("""{ "flow": "f", "checkpoints": [] }"""),
        )
        assertEquals(
            "events and order go inside each step when there are steps",
            parseError("""{ "flow": "f", "events": [], "steps": [] }"""),
        )
        assertEquals("steps[0].order must be set or sequence", parseError("""{ "flow": "f", "steps": [ { "name": "a", "order": "any", "events": [] } ] }"""))
        assertEquals("step a is listed twice", parseError("""{ "flow": "f", "steps": [ { "name": "a", "events": [] }, { "name": "a", "events": [] } ] }"""))
    }

    @Test
    fun aSchemaIsCheckedWhenTheFileIsParsedEvenWithVariablesInIt() {
        val failure = assertFailsWith<IllegalArgumentException> {
            Baseline.parse("""{ "flow": "f", "events": [ { "name": "a", "channel": "c", "schema": { "type": "strng" } } ] }""")
        }
        assertTrue(failure.message.orEmpty().startsWith("events[0].schema.type 'strng' is not one of"), failure.message)

        Baseline.parse("""{ "flow": "f", "events": [ { "name": "a", "channel": "c", "schema": { "properties": { "sku": { "pattern": "^{{sku}}$" } } } } ] }""")
    }

    @Test
    fun aDeferredEventCantBeListedAgainWhereItsWindowReaches() {
        val failure = assertFailsWith<IllegalArgumentException> {
            Baseline.parse(
                """
                { "flow": "f", "steps": [
                    { "name": "browse", "events": [ { "name": "purchase", "channel": "firebase", "deferred": true, "schema": {} } ] },
                    { "name": "buy", "events": [ { "name": "purchase", "channel": "firebase", "schema": {} } ] } ] }
                """,
            )
        }
        assertEquals("purchase · firebase is deferred, so it can't be listed again in step buy", failure.message)
    }

    @Test
    fun aListMixingTypesIsRecordedWithEach() {
        val json = recorded { sink.log("purchase", "firebase", mapOf("items" to listOf(mapOf("qty" to 1), mapOf("qty" to "2")))) }

        assertTrue(Regex(""""qty":\s*\{\s*"type":\s*\[\s*"integer",\s*"string"\s*\]""").containsMatchIn(json), json)
    }

    @Test
    fun aStepRecordedTwiceFailsAtOnce() {
        val failure = assertFailsWith<IllegalArgumentException> {
            recorded { step("buy") { addToCart() }; step("buy") { purchase() } }
        }
        assertEquals("step buy already ran", failure.message)
    }

    @Test
    fun aFailureWaitingCantFixDoesntWait() {
        val baseline = Baseline.parse(recorded { checkout() })
        val mustNotWait = Waiter { _, _ -> error("waited for an event that was already sent wrong") }

        assertFailsWith<BaselineMismatch> { baseline.flow(mustNotWait) { checkout(quantity = 3) } }
    }

    @Test
    fun aRecordingIsTheFileItWasRecordedAs() {
        val json = recorded { checkout() }

        assertEquals(json, Baseline.parse(json).toJson())
    }

    @Test
    fun lastEventChecksOneEventOnItsOwn() {
        checkout()

        lastEvent("purchase" on "firebase", now).shouldHave("value", 9.5)
        assertFailsWith<AssertionError> { lastEvent("add_to_cart" on "firebase", now).shouldHave("quantity", "2") }
        assertFailsWith<IllegalArgumentException> { lastEvent("add_to_cart" on "firebase", now).shouldHave("email", "a@b.c") }
        assertEquals(
            "refund · firebase wasn't sent within 5s",
            assertFailsWith<AssertionError> { lastEvent("refund" on "firebase", now) }.message,
        )
    }

    @Test
    fun shouldMatchPlanAsksTheLoadedPlan() {
        assertEquals(
            "screen_view · firebase: no tracking plan is loaded",
            assertFailsWith<AssertionError> { screen(); lastEvent("screen_view" on "firebase", now).shouldMatchPlan() }.message,
        )
        Inspector.loadTrackingPlan(
            """{ "version": "1", "events": [ { "name": "screen_view", "channel": "firebase",
                 "schema": { "properties": { "screen_name": { "enum": ["cart"] } } } } ] }""",
        )
        screen()
        lastEvent("screen_view" on "firebase", now).shouldMatchPlan()
        screen("home")
        assertEquals(
            "screen_view · firebase: breaks the plan\n  screen_name · Not an allowed value · expects cart",
            assertFailsWith<AssertionError> { lastEvent("screen_view" on "firebase", now).shouldMatchPlan() }.message,
        )
    }
}
