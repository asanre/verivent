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

    private val playlistPlan = plan(
        """{ "required": ["tracks"],
             "properties": { "tracks": { "type": "array", "minItems": 1,
                 "items": { "type": "object", "required": ["id"], "additionalProperties": false,
                            "properties": { "id": { "type": "string" },
                                            "duration_ms": { "type": "integer" },
                                            "artists": { "type": "array", "items": { "type": "string" } } } } } } }""",
    )

    private fun track(duration: Any?, id: String? = "t1") = buildMap {
        id?.let { put("id", it) }
        put("duration_ms", duration)
    }

    @Test
    fun aListOfRealMapsIsCheckedItemByItemAndGroupedByPath() {
        val issues = playlistPlan.issuesFor("search", "firebase", mapOf("tracks" to List(60) { track("${it * 1000}") }))
        assertEquals(listOf(PlanIssue("tracks[*].duration_ms", "Expected integer, got string · 60 of 60")), issues)
        assertTrue(issues!!.none { "1000" in it.message })
    }

    @Test
    fun theCountSaysHowManyItemsBreakTheirSchema() {
        val tracks = List(10) { track(if (it < 3) "slow" else 1200, id = if (it == 9) null else "t$it") }
        assertEquals(
            listOf(
                PlanIssue("tracks[*].duration_ms", "Expected integer, got string · 3 of 10"),
                PlanIssue("tracks[*].id", "Required, but not sent · 1 of 10"),
            ),
            playlistPlan.issuesFor("search", "firebase", mapOf("tracks" to tracks)),
        )
    }

    @Test
    fun listsInsideListsCountTheInnerItems() {
        val tracks = listOf(
            mapOf("id" to "a", "artists" to listOf("x", 1)),
            mapOf("id" to "b", "artists" to arrayOf("y", "z")),
        )
        assertEquals(
            listOf(PlanIssue("tracks[*].artists[*]", "Expected string, got integer · 1 of 4")),
            playlistPlan.issuesFor("search", "firebase", mapOf("tracks" to tracks)),
        )
    }

    @Test
    fun aListSentAsJsonTextIsAString() {
        assertEquals(
            listOf(PlanIssue("tracks", "Expected array, got string")),
            playlistPlan.issuesFor("search", "firebase", mapOf("tracks" to """[{"id":"a"}]""")),
        )
    }

    @Test
    fun nestedObjectsReportUnknownKeysAndMinItems() {
        assertEquals(
            listOf(
                PlanIssue("tracks", "Expected at least 1 items"),
            ),
            playlistPlan.issuesFor("search", "firebase", mapOf("tracks" to emptyList<Any>())),
        )
        assertEquals(
            listOf(PlanIssue("tracks[*].mood", "Not in the plan for this event · 1 of 1")),
            playlistPlan.issuesFor("search", "firebase", mapOf("tracks" to listOf(mapOf("id" to "a", "mood" to "calm")))),
        )
    }

    @Test
    fun nestedPropertiesOfAnObjectHaveDottedPaths() {
        val plan = plan(
            """{ "properties": { "item": { "type": "object", "required": ["price"],
                 "properties": { "price": { "type": "number" }, "currency": { "enum": ["EUR", "USD"] } } } } }""",
        )
        assertEquals(
            listOf(
                PlanIssue("item.price", "Required, but not sent"),
                PlanIssue("item.currency", "Not an allowed value · expects EUR or USD"),
            ),
            plan.issuesFor("search", "firebase", mapOf("item" to mapOf("currency" to "GBP"))),
        )
    }

    @Test
    fun constQuotesThePlanAndComparesNumbersByValue() {
        val plan = plan("""{ "properties": { "screen": { "const": "home" }, "step": { "const": 2 }, "filters": { "const": { "a": [1] } } } }""")
        assertEquals(
            emptyList(),
            plan.issuesFor("search", "firebase", mapOf("screen" to "home", "step" to 2.0, "filters" to mapOf("a" to listOf(1L)))),
        )
        val issues = plan.issuesFor("search", "firebase", mapOf("screen" to "secret-pdp", "step" to "2", "filters" to mapOf("a" to listOf(2))))
        assertEquals(
            listOf(
                PlanIssue("screen", "Not the expected value · expects home"),
                PlanIssue("step", "Not the expected value · expects 2"),
                PlanIssue("filters", "Not the expected value · expects {\"a\":[1]}"),
            ),
            issues,
        )
        assertTrue(issues!!.none { "secret" in it.message })
    }

    @Test
    fun containsLooksForOneMatchingItem() {
        val plan = plan(
            """{ "properties": { "tracks": { "type": "array",
                 "contains": { "properties": { "id": { "const": "t7" } }, "required": ["id"] } } } }""",
        )
        assertEquals(emptyList(), plan.issuesFor("search", "firebase", mapOf("tracks" to listOf(mapOf("id" to "t1"), mapOf("id" to "t7")))))
        assertEquals(
            listOf(PlanIssue("tracks", "No item matches the expected one")),
            plan.issuesFor("search", "firebase", mapOf("tracks" to listOf(mapOf("id" to "t1")))),
        )
    }

    @Test
    fun aHiddenPropertyIsOnlyCheckedForPresenceAndTypeAllTheWayDown() {
        val plan = plan(
            """{ "properties": { "cart": { "type": "array", "minItems": 3, "contains": { "const": "x" },
                 "items": { "type": "object", "required": ["sku"], "properties": { "sku": { "type": "string", "pattern": "^S" } } } } } }""",
        )
        assertEquals(
            listOf(
                PlanIssue("cart[*].sku", "Expected string, got integer · 1 of 2"),
                PlanIssue("cart[*].sku", "Required, but not sent · 1 of 2"),
            ),
            plan.issuesFor("search", "firebase", mapOf("cart" to listOf(mapOf("sku" to 1), emptyMap<String, Any>())), hidden = setOf("cart")),
        )
    }

    @Test
    fun aValueThatIsNotJsonKeepsItsClassName() {
        class Sku
        val plan = plan("""{ "properties": { "sku": { "type": "string" } } }""")
        assertEquals(
            listOf(PlanIssue("sku", "Expected string, got Sku")),
            plan.issuesFor("search", "firebase", mapOf("sku" to Sku())),
        )
    }

    @Test
    fun aMapThatHoldsItselfIsCutAtTheDepthLimit() {
        val plan = plan("""{ "properties": { "node": { "${'$'}ref": "ignored" } } }""")
        val loop = HashMap<String, Any?>()
        loop["self"] = loop
        loop["again"] = loop
        assertEquals(emptyList(), plan.issuesFor("search", "firebase", mapOf("node" to loop)))

        var deep: Any? = "leaf"
        repeat(70) { deep = listOf(deep) }
        var schema = """{ "type": "string" }"""
        repeat(70) { schema = """{ "type": "array", "items": $schema }""" }
        val issue = plan(""" { "properties": { "deep": $schema } }""")
            .issuesFor("search", "firebase", mapOf("deep" to deep))!!.single()
        assertTrue(issue.message.startsWith("Too deeply nested to check · over 64 levels"), issue.message)
    }

    @Test
    fun rejectsMalformedNestedKeywords() {
        fun reason(schema: String) = assertFailsWith<IllegalArgumentException> {
            TrackingPlan.parse("""{ "version": "1", "events": [ { "name": "a", "channel": "c", "schema": $schema } ] }""")
        }.message.orEmpty()
        assertEquals("a.tracks.items must be an object", reason("""{ "properties": { "tracks": { "items": [] } } }"""))
        assertEquals("a.tracks.minItems must be a non-negative integer", reason("""{ "properties": { "tracks": { "minItems": -1 } } }"""))
        assertEquals("a.tracks.contains must be an object", reason("""{ "properties": { "tracks": { "contains": true } } }"""))
        assertEquals(
            "a.tracks.items.id.type 'text' is not one of [string, integer, number, boolean, null, object, array]",
            reason("""{ "properties": { "tracks": { "items": { "properties": { "id": { "type": "text" } } } } } }"""),
        )
    }
}
