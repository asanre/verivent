package com.snoop.analytics

import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
