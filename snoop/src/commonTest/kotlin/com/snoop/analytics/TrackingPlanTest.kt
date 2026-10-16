package com.snoop.analytics

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
        assertEquals("1.0.0", plan.version)
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
            listOf("missing required: search_term", "unexpected param: serch_term"),
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
        assertEquals("num_results: expected integer, got string", issue)
        assertTrue("secret-42" !in issue)
    }

    @Test
    fun integersAreNumbersAndWholeDoublesAreIntegers() {
        val number = plan("""{ "properties": { "price": { "type": "number" } } }""")
        assertEquals(emptyList(), number.issuesFor("search", "firebase", mapOf("price" to 3)))
        val integer = plan("""{ "properties": { "count": { "type": ["integer", "null"] } } }""")
        assertEquals(emptyList(), integer.issuesFor("search", "firebase", mapOf("count" to 2.0)))
        assertEquals(emptyList(), integer.issuesFor("search", "firebase", mapOf("count" to null)))
        assertEquals(
            listOf("count: expected integer or null, got number"),
            integer.issuesFor("search", "firebase", mapOf("count" to 2.5)),
        )
    }

    @Test
    fun enumAndPatternReportWithoutQuotingTheValue() {
        val plan = plan(
            """{ "properties": { "list_scope": { "enum": ["search", "catalog"] },
                                 "item_id": { "pattern": "^[0-9]+$" },
                                 "position": { "enum": [1, 2] } } }""",
        )
        assertEquals(
            listOf("list_scope: not one of the allowed values", "item_id: does not match the pattern"),
            plan.issuesFor("search", "firebase", mapOf("list_scope" to "pdp", "item_id" to "AB12", "position" to 2L)),
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
