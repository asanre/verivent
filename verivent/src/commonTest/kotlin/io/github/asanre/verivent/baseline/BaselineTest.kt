package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineTest {

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() = Inspector.reset()

    private val sink = AnalyticsSink { sanitizeProperty { it == "email" } }

    private fun checkout(itemId: String = "42", quantity: Any = 2) {
        sink.log("screen_view", "firebase", mapOf("screen_name" to "cart"))
        sink.log("add_to_cart", "firebase", mapOf("item_id" to itemId, "quantity" to quantity, "email" to "a@b.c"))
        sink.log("purchase", "firebase", mapOf("value" to 9.5, "items" to listOf(mapOf("id" to itemId, "qty" to quantity))))
    }

    private fun recorded(): String {
        checkout()
        return Baseline.record().also { Inspector.reset() }
    }

    @Test
    fun aSessionMatchesTheBaselineRecordedFromIt() {
        val baseline = recorded()
        checkout()
        assertNull(Baseline.diff(baseline))
    }

    @Test
    fun aParameterBrokenOnPurposeGivesAReadableDiff() {
        val baseline = recorded()
        checkout(quantity = "2")
        assertEquals(
            """
            Baseline: 3 expected, 1 matched, 2 missing, 2 extra
            ✗ missing  add_to_cart · firebase, #2 in the baseline
                closest: event 2
                  quantity · Not the expected value · expects 2
            ✗ missing  purchase · firebase, #3 in the baseline
                closest: event 3
                  items[*].qty · Expected integer, got string · 1 of 1
            + extra    add_to_cart · firebase, event 2
            + extra    purchase · firebase, event 3
            """.trimIndent(),
            Baseline.diff(baseline),
        )
    }

    @Test
    fun variablesAreFilledFromTheTestKeepingTheirType() {
        val baseline = recorded()
            .replace("\"const\": \"42\"", "\"const\": \"{{item}}\"")
            .replace("\"const\": 2", "\"const\": \"{{quantity}}\"")
        checkout(itemId = "77", quantity = 5)
        assertNull(Baseline.diff(baseline, variables = mapOf("item" to "77", "quantity" to 5)))
        assertTrue(Baseline.diff(baseline, variables = mapOf("item" to "77", "quantity" to "5"))!!.contains("expects 5"))
        val error = assertFailsWith<IllegalArgumentException> { Baseline.diff(baseline, variables = mapOf("item" to "77")) }
        assertEquals("{{quantity}} has no value", error.message)
    }

    @Test
    fun anEventSentTwiceIsExtraUnlessTheBaselineAllowsIt() {
        val baseline = recorded()
        checkout()
        sink.log("screen_view", "firebase", mapOf("screen_name" to "cart"))
        assertEquals(
            "Baseline: 3 expected, 3 matched, 0 missing, 1 extra\n+ extra    screen_view · firebase, event 4",
            Baseline.diff(baseline),
        )
        assertNull(Baseline.diff(baseline.replace("\"extra\": \"fail\"", "\"extra\": \"allow\"")))
    }

    @Test
    fun anEventNeverSentIsMissing() {
        val baseline = recorded()
        sink.log("screen_view", "firebase", mapOf("screen_name" to "cart"))
        assertEquals(
            """
            Baseline: 3 expected, 1 matched, 2 missing, 0 extra
            ✗ missing  add_to_cart · firebase, #2 in the baseline
                never sent
            ✗ missing  purchase · firebase, #3 in the baseline
                never sent
            """.trimIndent(),
            Baseline.diff(baseline),
        )
    }

    @Test
    fun aSequenceAlsoWantsTheOrder() {
        val baseline = recorded().replace("\"order\": \"set\"", "\"order\": \"sequence\"")
        sink.log("add_to_cart", "firebase", mapOf("item_id" to "42", "quantity" to 2, "email" to "x"))
        sink.log("screen_view", "firebase", mapOf("screen_name" to "cart"))
        sink.log("purchase", "firebase", mapOf("value" to 9.5, "items" to listOf(mapOf("id" to "42", "qty" to 2))))
        assertNull(Baseline.diff(baseline.replace("\"order\": \"sequence\"", "\"order\": \"set\"")))
        assertEquals(
            """
            Baseline: 3 expected, 2 matched, 1 missing, 1 extra, in order
            ✗ missing  screen_view · firebase, #1 in the baseline
                closest: event 2, which matches but is out of order
            + extra    screen_view · firebase, event 2
            """.trimIndent(),
            Baseline.diff(baseline),
        )
    }

    @Test
    fun aListKeepsItsShapeAndContainsChecksTheItemTheTestKnows() {
        val baseline = recorded()
        assertTrue("\"const\": [" !in baseline, baseline)
        val withItem = baseline.replace(
            "\"minItems\": 1,",
            "\"minItems\": 1, \"contains\": { \"properties\": { \"id\": { \"const\": \"{{item}}\" } }, \"required\": [\"id\"] },",
        )
        checkout(itemId = "42")
        assertNull(Baseline.diff(withItem.replace("\"const\": \"42\"", "\"type\": \"string\""), variables = mapOf("item" to "42")))
        val diff = Baseline.diff(withItem.replace("\"const\": \"42\"", "\"type\": \"string\""), variables = mapOf("item" to "7"))!!
        assertTrue("items · No item matches the expected one" in diff, diff)
    }

    @Test
    fun aRedactedPropertyOnlyKeepsItsType() {
        val baseline = recorded()
        assertTrue("a@b.c" !in baseline && "██" !in baseline, baseline)
        sink.log("screen_view", "firebase", mapOf("screen_name" to "cart"))
        sink.log("add_to_cart", "firebase", mapOf("item_id" to "42", "quantity" to 2, "email" to "other@b.c"))
        sink.log("purchase", "firebase", mapOf("value" to 9.5, "items" to listOf(mapOf("id" to "42", "qty" to 2))))
        assertNull(Baseline.diff(baseline))
    }

    @Test
    fun rejectsAMalformedBaseline() {
        assertEquals("events must be an array", assertFailsWith<IllegalArgumentException> { Baseline.diff("{}") }.message)
        assertEquals(
            "order must be set or sequence",
            assertFailsWith<IllegalArgumentException> { Baseline.diff("""{ "order": "random", "events": [] }""") }.message,
        )
    }
}
