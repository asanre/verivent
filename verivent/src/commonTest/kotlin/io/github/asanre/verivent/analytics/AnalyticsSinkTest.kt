package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.Annotation
import io.github.asanre.verivent.core.model.PlanIssue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsSinkTest {

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() = Inspector.reset()

    private fun entries() = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>()

    @Test
    fun logsEventWithChannelAndProperties() {
        AnalyticsSink().log("screen_view", "firebase", mapOf("screen" to "home"))
        val entry = entries().single()
        assertEquals("screen_view", entry.name)
        assertEquals("firebase", entry.channel)
        assertEquals("home", entry.properties.single { it.name == "screen" }.value)
    }

    @Test
    fun filterDropsMatchingEvents() {
        val verivent = AnalyticsSink { filter { !it.name.startsWith("debug_") } }
        verivent.log("debug_ping", "firebase")
        verivent.log("purchase", "firebase")
        assertEquals(listOf("purchase"), entries().map { it.name })
    }

    @Test
    fun redactsSensitiveProperties() {
        val verivent = AnalyticsSink { sanitizeProperty { it == "user_email" } }
        verivent.log("login", "firebase", mapOf("user_email" to "a@b.com", "method" to "google"))
        val entry = entries().single()
        assertTrue(entry.properties.single { it.name == "user_email" }.redacted)
        assertEquals("google", entry.properties.single { it.name == "method" }.value)
    }

    @Test
    fun recordsSectionsApartInOrderRedactedAndWithoutEmptyOnes() {
        val verivent = AnalyticsSink { sanitizeProperty { it == "user_id" } }
        verivent.log(
            "search",
            "firebase",
            properties = mapOf("search_term" to "shoes"),
            sections = mapOf(
                "User properties" to mapOf("user_id" to "42", "user_logged" to true),
                "Empty" to emptyMap(),
                "Default parameters" to mapOf("environment" to "pre"),
            ),
        )
        val entry = entries().single()
        assertEquals(listOf("search_term"), entry.properties.map { it.name })
        assertEquals(listOf("User properties", "Default parameters"), entry.sections.keys.toList())
        val userProperties = entry.sections.getValue("User properties")
        assertTrue(userProperties.single { it.name == "user_id" }.redacted)
        assertEquals("true", userProperties.single { it.name == "user_logged" }.value)
    }

    @Test
    fun throwingLambdasNeverReachTheCaller() {
        val verivent = AnalyticsSink {
            filter { error("broken filter") }
            annotate { error("broken annotator") }
            sanitizeProperty { error("broken sanitizer") }
        }
        // Must not throw; filter fails open (event visible), annotator falls back to no badges,
        // sanitizer fails closed (property redacted).
        verivent.log("purchase", "firebase", mapOf("value" to 9.99))
        val entry = entries().single()
        assertEquals("purchase", entry.name)
        assertTrue(entry.annotations.isEmpty())
        assertTrue(entry.properties.single { it.name == "value" }.redacted)
    }

    @Test
    fun annotatesEvents() {
        val ecommerce = setOf("purchase", "add_to_cart")
        val verivent = AnalyticsSink {
            annotate { if (it.name in ecommerce) listOf(Annotation.Ecommerce) else emptyList() }
        }
        verivent.log("purchase", "firebase", mapOf("value" to 9.99))
        val entry = entries().single()
        assertEquals(listOf(Annotation.Ecommerce), entry.annotations)
        assertEquals("9.99", entry.properties.single { it.name == "value" }.value)
    }

    @Test
    fun stampsTheGroupLabelOnlyOnTheOpeningEvent() {
        val verivent = AnalyticsSink()
        verivent.log("screen_view", "firebase", mapOf("screen_name" to "Home"))
        verivent.log("purchase", "firebase", mapOf("screen_name" to "Home"))
        assertEquals(listOf("Home", null), entries().map { it.groupLabel })
    }

    @Test
    fun theLabelFallsBackToTheEventNameWhenThePropertyIsMissing() {
        val verivent = AnalyticsSink()
        verivent.log("screen_view", "firebase")
        assertEquals("screen_view", entries().single().groupLabel)

        Inspector.reset()
        AnalyticsSink { groupBy("screen_view") }.log("screen_view", "firebase", mapOf("screen_name" to "Home"))
        assertEquals("screen_view", entries().single().groupLabel)
    }

    @Test
    fun theLabelHonoursRedaction() {
        val verivent = AnalyticsSink { sanitizeProperty { it == "screen_name" } }
        verivent.log("screen_view", "firebase", mapOf("screen_name" to "Checkout"))
        val entry = entries().single()
        assertEquals(entry.properties.single { it.name == "screen_name" }.value, entry.groupLabel)
        assertTrue("Checkout" !in entry.groupLabel.orEmpty())
    }

    @Test
    fun groupByRetargetsTheOpeningEventAndNullTurnsGroupingOff() {
        AnalyticsSink { groupBy("page_shown", "page") }
            .log("page_shown", "gtm", mapOf("page" to "Checkout"))
        assertEquals("Checkout", entries().single().groupLabel)

        Inspector.reset()
        AnalyticsSink { groupBy(event = null) }.log("screen_view", "firebase", mapOf("screen_name" to "Home"))
        assertNull(entries().single().groupLabel)
    }

    @Test
    fun logToWritesTheCapturedEntryAsOneJsonLineWithRedactedValues() {
        val lines = mutableListOf<String>()
        val verivent = AnalyticsSink {
            sanitizeProperty { it == "user_id" }
            logTo { lines += it }
        }
        verivent.log("search", "firebase", mapOf("search_term" to "shoes", "user_id" to "42"))

        val line = Json.parseToJsonElement(lines.single()).jsonObject
        assertEquals("search", line["name"]?.jsonPrimitive?.content)
        assertEquals("firebase", line["channel"]?.jsonPrimitive?.content)
        assertTrue("\"42\"" !in lines.single())
        assertTrue("shoes" in lines.single())
    }

    @Test
    fun aThrowingLoggerKeepsTheEvent() {
        AnalyticsSink { logTo { error("boom") } }.log("purchase", "firebase")
        assertEquals(listOf("purchase"), entries().map { it.name })
    }

    @Test
    fun aDisabledCoreWritesNoLine() {
        val lines = mutableListOf<String>()
        Inspector.enabled = false
        AnalyticsSink { logTo { lines += it } }.log("search", "firebase")
        assertTrue(lines.isEmpty())
    }

    private val searchPlan = """
        { "version": "2.1.0", "events": [ { "name": "search", "channel": "firebase",
          "schema": { "required": ["search_term"], "properties": { "user_id": { "type": "integer" } } } } ] }
    """

    @Test
    fun aLoadedPlanStampsItsVersionAndIssuesBeforeRedactionOnlyOnEventsItDefines() {
        Inspector.loadTrackingPlan(searchPlan)
        val verivent = AnalyticsSink { sanitizeProperty { it == "user_id" } }
        verivent.log("search", "firebase", mapOf("user_id" to 42))
        verivent.log("purchase", "firebase")

        val (search, purchase) = entries()
        assertEquals("2.1.0", search.planVersion)
        assertEquals(listOf(PlanIssue("search_term", "Required, but not sent")), search.issues)
        assertNull(purchase.planVersion)
        assertEquals(emptyList(), purchase.issues)
    }

    @Test
    fun aRedactedPropertyIsCheckedForTypeButNeverAgainstItsAllowedValues() {
        Inspector.loadTrackingPlan(
            """{ "version": "1", "events": [ { "name": "login", "channel": "firebase",
                 "schema": { "properties": { "token": { "type": "string", "enum": ["a"], "pattern": "^a$" } } } } ] }""",
        )
        val verivent = AnalyticsSink { sanitizeProperty { it == "token" } }
        verivent.log("login", "firebase", mapOf("token" to "b"))
        verivent.log("login", "firebase", mapOf("token" to 7))

        val (otherValue, wrongType) = entries()
        assertEquals(emptyList(), otherValue.issues)
        assertEquals(listOf(PlanIssue("token", "Expected string, got integer")), wrongType.issues)
    }

    @Test
    fun withoutAPlanNothingIsStamped() {
        AnalyticsSink().log("search", "firebase")
        val entry = entries().single()
        assertNull(entry.planVersion)
        assertEquals(emptyList(), entry.issues)
    }

    @Test
    fun clearingThePlanStopsValidationButKeepsEarlierVerdicts() {
        Inspector.loadTrackingPlan(searchPlan)
        assertEquals("2.1.0", Inspector.trackingPlan.value?.version)
        val verivent = AnalyticsSink()
        verivent.log("search", "firebase")
        Inspector.clearTrackingPlan()
        verivent.log("search", "firebase")

        assertNull(Inspector.trackingPlan.value)
        assertEquals(listOf(1, 0), entries().map { it.issues.size })
    }

    @Test
    fun anInvalidPlanThrowsAndLeavesTheCurrentOneInPlace() {
        Inspector.loadTrackingPlan(searchPlan)
        assertFailsWith<IllegalArgumentException> { Inspector.loadTrackingPlan("{}") }
        assertEquals("2.1.0", Inspector.trackingPlan.value?.version)
    }

    @Test
    fun nestedValuesAreShownAsJsonAndCheckedItemByItem() {
        Inspector.loadTrackingPlan(
            """{ "version": "1", "events": [ { "name": "play", "channel": "firebase",
                 "schema": { "properties": { "tracks": { "items": { "properties": { "ms": { "type": "integer" } } } } } } } ] }""",
        )
        AnalyticsSink().log("play", "firebase", mapOf("tracks" to listOf(mapOf("ms" to "1"), mapOf("ms" to 2)), "rate" to 1.5f))

        val entry = entries().single()
        assertEquals(listOf("""[{"ms":"1"},{"ms":2}]""", "1.5"), entry.properties.map { it.value })
        assertEquals(listOf(PlanIssue("tracks[*].ms", "Expected integer, got string · 1 of 2")), entry.issues)
    }

    @Test
    fun containersPastTheBudgetAreReportedAsTooManyNotAsTheWrongType() {
        Inspector.loadTrackingPlan(
            """{ "version": "1", "events": [ { "name": "list", "channel": "firebase",
                 "schema": { "properties": { "items": { "type": "array", "items": { "type": "object" } } } } } ] }""",
        )
        // The list itself and its first 9,999 maps fit the 10,000 containers an event converts.
        AnalyticsSink().log("list", "firebase", mapOf("items" to List(10_001) { mapOf("i" to it) }))

        assertEquals(
            listOf(PlanIssue("items[*]", "Too many lists and maps to check · over 10,000 per event · 2 of 10001")),
            entries().single().issues,
        )
    }
}
