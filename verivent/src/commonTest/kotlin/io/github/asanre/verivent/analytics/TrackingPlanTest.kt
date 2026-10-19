package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackingPlanTest {

    private fun plan(schema: String, version: String = "1.0.0") = TrackingPlan.parse(
        """{ "version": "$version", "events": [ { "name": "search", "channel": "firebase", "schema": $schema } ] }""",
    )

    @Test
    fun aCompliantEventHasNoIssues() {
        val plan = plan("""{ "required": ["search_term"], "properties": { "search_term": { "type": "string" } } }""")
        assertEquals(emptyList(), plan.issuesFor("search", "firebase", mapOf("search_term" to "shoes")))
        assertEquals(PlanSummary("1.0.0", listOf(PlanEvent("search", "firebase"))), plan.summary)
    }

    @Test
    fun anEventThePlanDoesNotDefineIsNullEvenWithTheSameNameOnAnotherChannel() {
        val plan = plan("""{}""")
        assertNull(plan.issuesFor("search", "server-side", emptyMap()))
        assertNull(plan.issuesFor("purchase", "firebase", emptyMap()))
    }

    @Test
    fun reportsMissingRequiredAndUnexpectedParams() {
        val plan = plan("""{ "required": ["search_term"], "properties": { "search_term": {} }, "additionalProperties": false }""")
        assertEquals(
            listOf(
                PlanIssue("search_term", "Required, but not sent"),
                PlanIssue("serch_term", "Not in the plan for this event"),
            ),
            plan.issuesFor("search", "firebase", mapOf("serch_term" to "shoes")),
        )
    }

    @Test
    fun extraParamsAreAllowedWhenAdditionalPropertiesIsAbsent() {
        assertEquals(emptyList(), plan("""{}""").issuesFor("search", "firebase", mapOf("anything" to 1)))
    }

    @Test
    fun typeMismatchNamesTheTypesButNeverTheValue() {
        val plan = plan("""{ "properties": { "num_results": { "type": "integer" } } }""")
        val issue = plan.issuesFor("search", "firebase", mapOf("num_results" to "secret-42"))!!.single()
        assertEquals(PlanIssue("num_results", "Expected integer, got string"), issue)
        assertTrue("secret-42" !in issue.message)
    }

    @Test
    fun integersAreNumbersAndWholeDoublesAreIntegers() {
        val number = plan("""{ "properties": { "price": { "type": "number" } } }""")
        assertEquals(emptyList(), number.issuesFor("search", "firebase", mapOf("price" to 3)))
        val integer = plan("""{ "properties": { "count": { "type": ["integer", "null"] } } }""")
        assertEquals(emptyList(), integer.issuesFor("search", "firebase", mapOf("count" to 2.0)))
        assertEquals(emptyList(), integer.issuesFor("search", "firebase", mapOf("count" to null)))
        assertEquals(
            listOf(PlanIssue("count", "Expected integer or null, got number")),
            integer.issuesFor("search", "firebase", mapOf("count" to 2.5)),
        )
    }

    @Test
    fun enumAndPatternQuoteThePlanButNeverTheValue() {
        val plan = plan(
            """{ "properties": { "list_scope": { "enum": ["search", "catalog", "home"] },
                                 "item_id": { "pattern": "^[0-9]+$" },
                                 "position": { "enum": [1, 2] } } }""",
        )
        assertEquals(
            listOf(
                PlanIssue("list_scope", "Not an allowed value · expects search, catalog or home"),
                PlanIssue("item_id", "Doesn't match the pattern ^[0-9]+$"),
            ),
            plan.issuesFor("search", "firebase", mapOf("list_scope" to "pdp", "item_id" to "AB12", "position" to 2L)),
        )
    }

    @Test
    fun aValueTooLongForThePatternIsFlaggedInsteadOfMatched() {
        val plan = plan("""{ "properties": { "query": { "pattern": "^a+$" } } }""")
        assertEquals(emptyList(), plan.issuesFor("search", "firebase", mapOf("query" to "a".repeat(256))))
        assertEquals(
            listOf(PlanIssue("query", "Too long to check against the pattern · over 256 characters")),
            plan.issuesFor("search", "firebase", mapOf("query" to "a".repeat(257))),
        )
    }

    @Test
    fun patternOnlyAppliesToStrings() {
        val plan = plan("""{ "properties": { "item_id": { "pattern": "^[0-9]+$" } } }""")
        assertEquals(emptyList(), plan.issuesFor("search", "firebase", mapOf("item_id" to 12)))
    }

    @Test
    fun rejectsMalformedPlansNamingTheProblem() {
        fun reason(json: String) = assertFailsWith<IllegalArgumentException> { TrackingPlan.parse(json) }.message.orEmpty()
        assertTrue("not valid JSON" in reason("{"))
        assertTrue("version" in reason("""{ "events": [] }"""))
        assertTrue("events" in reason("""{ "version": "1" }"""))
        assertTrue("twice" in reason(
            """{ "version": "1", "events": [ { "name": "a", "channel": "c", "schema": {} },
                                             { "name": "a", "channel": "c", "schema": {} } ] }""",
        ))
        assertTrue("interger" in reason(
            """{ "version": "1", "events": [ { "name": "a", "channel": "c",
                 "schema": { "properties": { "p": { "type": "interger" } } } } ] }""",
        ))
        assertTrue("additionalProperties" in reason(
            """{ "version": "1", "events": [ { "name": "a", "channel": "c",
                 "schema": { "additionalProperties": "false" } } ] }""",
        ))
        assertTrue("pattern" in reason(
            """{ "version": "1", "events": [ { "name": "a", "channel": "c",
                 "schema": { "properties": { "p": { "pattern": "[" } } } } ] }""",
        ))
    }
}
