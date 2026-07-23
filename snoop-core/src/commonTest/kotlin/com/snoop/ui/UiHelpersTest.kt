package com.snoop.ui

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.internal.buildCurl
import com.snoop.ui.internal.entriesToJson
import com.snoop.ui.internal.entriesToJsonCapped
import com.snoop.ui.internal.groupEntries
import com.snoop.ui.internal.stableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

class CurlTest {
    @Test
    fun buildsPostCurlWithRedactedHeaderAndBody() {
        val txn = NetworkTransaction(
            id = "1",
            timestamp = Instant.fromEpochMilliseconds(0),
            method = "POST",
            url = "https://api.test/orders",
            host = "api.test",
            path = "/orders",
            requestHeaders = listOf(
                KeyValue("Content-Type", "application/json"),
                KeyValue("Authorization", "██", redacted = true),
            ),
            requestBody = BodyPreview("""{"id":1}""", 8, false),
        )
        val curl = buildCurl(txn)
        assertTrue(curl.contains("-X POST"))
        assertTrue(curl.contains("'https://api.test/orders'"))
        assertTrue(curl.contains("-H 'Authorization: ██'"))
        assertTrue(curl.contains("""--data '{"id":1}'"""))
    }

    @Test
    fun escapesSingleQuotesInUrlAndHeaderName() {
        val txn = NetworkTransaction(
            id = "1",
            timestamp = Instant.fromEpochMilliseconds(0),
            method = "GET",
            url = "https://api.test/x?q='; rm -rf ~'",
            host = "api.test",
            path = "/x",
            requestHeaders = listOf(KeyValue("X-Evil'", "v'al")),
        )
        val curl = buildCurl(txn)
        assertEquals(
            "curl \\\n" +
                "  'https://api.test/x?q='\\''; rm -rf ~'\\''' \\\n" +
                "  -H 'X-Evil'\\'': v'\\''al'",
            curl,
        )
    }

    @Test
    fun getOmitsMethodFlag() {
        val txn = NetworkTransaction(
            id = "1",
            timestamp = Instant.fromEpochMilliseconds(0),
            method = "GET",
            url = "https://api.test/x",
            host = "api.test",
            path = "/x",
        )
        assertTrue(!buildCurl(txn).contains("-X"))
    }
}

class ExportJsonTest {
    @Test
    fun serializesNetworkAndAnalytics() {
        val json = entriesToJson(
            listOf(
                NetworkTransaction("1", Instant.fromEpochMilliseconds(0), "GET", "https://h/a", "h", "/a", status = 200),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(0), "purchase", "firebase"),
            ),
        )
        assertTrue(json.contains("\"type\": \"network\""))
        assertTrue(json.contains("\"type\": \"analytics\""))
        assertTrue(json.contains("\"purchase\""))
    }

    @Test
    fun cappedExportKeepsNewestValidJsonAndReportsCount() {
        val entries = (1..50).map {
            NetworkTransaction(
                "$it", Instant.fromEpochMilliseconds(it.toLong()), "GET", "https://h/$it", "h", "/$it",
                responseBody = BodyPreview("x".repeat(500), 500, false),
            )
        }
        val (json, kept) = entriesToJsonCapped(entries, maxChars = 3_000)

        assertTrue(kept in 1 until 50, "expected a capped subset, kept $kept")
        assertEquals(kept, Json.parseToJsonElement(json).jsonArray.size)
        assertTrue(json.contains("\"id\": \"50\""))
        assertFalse(json.contains("\"id\": \"1\""))
    }

    @Test
    fun cappedExportReturnsEverythingUnderTheCap() {
        val entries = listOf(
            NetworkTransaction("1", Instant.fromEpochMilliseconds(0), "GET", "https://h/a", "h", "/a"),
            AnalyticsEntry("2", Instant.fromEpochMilliseconds(1), "purchase", "firebase"),
        )
        val (json, kept) = entriesToJsonCapped(entries)
        assertEquals(2, kept)
        assertEquals(entriesToJson(entries), json)
    }
}

class GroupingTest {
    private fun event(id: String, name: String, screen: String? = null) = AnalyticsEntry(
        id = id,
        timestamp = Instant.fromEpochMilliseconds(id.toLong()),
        name = name,
        channel = "firebase",
        properties = screen?.let { listOf(KeyValue("screen", it)) } ?: emptyList(),
    )

    private fun call(id: String) = NetworkTransaction(
        id, Instant.fromEpochMilliseconds(id.toLong()), "GET", "https://h/$id", "h", "/$id",
    )

    @Test
    fun groupsByScreenViewNewestFirst() {
        val entries: List<SnoopEntry> = listOf(
            event("1", "screen_view", "home"),
            call("2"),
            event("3", "screen_view", "cart"),
            call("4"),
        )
        val groups = groupEntries(entries, "screen_view")
        assertEquals(listOf("cart", "home"), groups.map { it.screen })
        // within the newest group, entries are newest-first
        assertEquals(listOf("4", "3"), groups.first().entries.map { it.id })
    }

    @Test
    fun entriesBeforeFirstScreenViewLandInUnnamedGroup() {
        val groups = groupEntries(listOf(call("1"), event("2", "screen_view", "home")), "screen_view")
        assertEquals(listOf("home", null), groups.map { it.screen })
    }

    @Test
    fun stableKeyUnchangedWhenNewestGroupGrows() {
        val before = groupEntries(listOf(event("1", "screen_view", "home"), call("2")), "screen_view")
        val after = groupEntries(
            listOf(event("1", "screen_view", "home"), call("2"), call("3")),
            "screen_view",
        )
        assertEquals(before.first().stableKey(), after.first().stableKey())
        assertEquals("1", after.first().stableKey())
    }

    @Test
    fun nullGroupKeyKeepsSingleGroup() {
        val groups = groupEntries(listOf(call("1"), call("2")), null)
        assertEquals(1, groups.size)
        assertEquals(listOf("2", "1"), groups.single().entries.map { it.id })
    }
}
