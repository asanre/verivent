package com.snoop.ui

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.internal.buildCurl
import com.snoop.ui.internal.entriesToJson
import com.snoop.ui.internal.groupEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

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
    fun nullGroupKeyKeepsSingleGroup() {
        val groups = groupEntries(listOf(call("1"), call("2")), null)
        assertEquals(1, groups.size)
        assertEquals(listOf("2", "1"), groups.single().entries.map { it.id })
    }
}
