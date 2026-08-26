package com.snoop.analytics

import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
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
}
