package com.snoop.analytics

import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnoopAnalyticsTest {

    @BeforeTest fun setup() = SnoopCore.reset()

    @AfterTest fun tearDown() = SnoopCore.reset()

    private fun entries() = SnoopCore.entriesSnapshot.filterIsInstance<AnalyticsEntry>()

    @Test
    fun logsEventWithChannelAndProperties() {
        SnoopAnalytics().log("screen_view", "firebase", mapOf("screen" to "home"))
        val entry = entries().single()
        assertEquals("screen_view", entry.name)
        assertEquals("firebase", entry.channel)
        assertEquals("home", entry.properties.single { it.name == "screen" }.value)
    }

    @Test
    fun filterDropsMatchingEvents() {
        val snoop = SnoopAnalytics { filter { !it.name.startsWith("debug_") } }
        snoop.log("debug_ping", "firebase")
        snoop.log("purchase", "firebase")
        assertEquals(listOf("purchase"), entries().map { it.name })
    }

    @Test
    fun redactsSensitiveProperties() {
        val snoop = SnoopAnalytics { sanitizeProperty { it == "user_email" } }
        snoop.log("login", "firebase", mapOf("user_email" to "a@b.com", "method" to "google"))
        val entry = entries().single()
        assertTrue(entry.properties.single { it.name == "user_email" }.redacted)
        assertEquals("google", entry.properties.single { it.name == "method" }.value)
    }

    @Test
    fun recordsSectionsApartInOrderRedactedAndWithoutEmptyOnes() {
        val snoop = SnoopAnalytics { sanitizeProperty { it == "user_id" } }
        snoop.log(
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
        val snoop = SnoopAnalytics {
            filter { error("broken filter") }
            annotate { error("broken annotator") }
            sanitizeProperty { error("broken sanitizer") }
        }
        // Must not throw; filter fails open (event visible), annotator falls back to no badges,
        // sanitizer fails closed (property redacted).
        snoop.log("purchase", "firebase", mapOf("value" to 9.99))
        val entry = entries().single()
        assertEquals("purchase", entry.name)
        assertTrue(entry.annotations.isEmpty())
        assertTrue(entry.properties.single { it.name == "value" }.redacted)
    }

    @Test
    fun annotatesEvents() {
        val ecommerce = setOf("purchase", "add_to_cart")
        val snoop = SnoopAnalytics {
            annotate { if (it.name in ecommerce) listOf(Annotation.Ecommerce) else emptyList() }
        }
        snoop.log("purchase", "firebase", mapOf("value" to 9.99))
        val entry = entries().single()
        assertEquals(listOf(Annotation.Ecommerce), entry.annotations)
        assertEquals("9.99", entry.properties.single { it.name == "value" }.value)
    }

    @Test
    fun stampsTheGroupLabelOnlyOnTheOpeningEvent() {
        val snoop = SnoopAnalytics()
        snoop.log("screen_view", "firebase", mapOf("screen_name" to "Home"))
        snoop.log("purchase", "firebase", mapOf("screen_name" to "Home"))
        assertEquals(listOf("Home", null), entries().map { it.groupLabel })
    }

    @Test
    fun theLabelFallsBackToTheEventNameWhenThePropertyIsMissing() {
        val snoop = SnoopAnalytics()
        snoop.log("screen_view", "firebase")
        assertEquals("screen_view", entries().single().groupLabel)

        SnoopCore.reset()
        SnoopAnalytics { groupBy("screen_view") }.log("screen_view", "firebase", mapOf("screen_name" to "Home"))
        assertEquals("screen_view", entries().single().groupLabel)
    }

    @Test
    fun theLabelHonoursRedaction() {
        val snoop = SnoopAnalytics { sanitizeProperty { it == "screen_name" } }
        snoop.log("screen_view", "firebase", mapOf("screen_name" to "Checkout"))
        val entry = entries().single()
        assertEquals(entry.properties.single { it.name == "screen_name" }.value, entry.groupLabel)
        assertTrue("Checkout" !in entry.groupLabel.orEmpty())
    }

    @Test
    fun groupByRetargetsTheOpeningEventAndNullTurnsGroupingOff() {
        SnoopAnalytics { groupBy("page_shown", "page") }
            .log("page_shown", "gtm", mapOf("page" to "Checkout"))
        assertEquals("Checkout", entries().single().groupLabel)

        SnoopCore.reset()
        SnoopAnalytics { groupBy(event = null) }.log("screen_view", "firebase", mapOf("screen_name" to "Home"))
        assertNull(entries().single().groupLabel)
    }

    @Test
    fun logToWritesTheCapturedEntryAsOneJsonLineWithRedactedValues() {
        val lines = mutableListOf<String>()
        val snoop = SnoopAnalytics {
            sanitizeProperty { it == "user_id" }
            logTo { lines += it }
        }
        snoop.log("search", "firebase", mapOf("search_term" to "shoes", "user_id" to "42"))

        val line = Json.parseToJsonElement(lines.single()).jsonObject
        assertEquals("search", line["name"]?.jsonPrimitive?.content)
        assertEquals("firebase", line["channel"]?.jsonPrimitive?.content)
        assertTrue("\"42\"" !in lines.single())
        assertTrue("shoes" in lines.single())
    }

    @Test
    fun withoutLogToNothingIsWrittenAndAThrowingLoggerKeepsTheEvent() {
        SnoopAnalytics().log("search", "firebase")
        SnoopAnalytics { logTo { error("boom") } }.log("purchase", "firebase")
        assertEquals(listOf("search", "purchase"), entries().map { it.name })
    }

    @Test
    fun aDisabledCoreWritesNoLine() {
        val lines = mutableListOf<String>()
        SnoopCore.enabled = false
        SnoopAnalytics { logTo { lines += it } }.log("search", "firebase")
        assertTrue(lines.isEmpty())
    }
}
