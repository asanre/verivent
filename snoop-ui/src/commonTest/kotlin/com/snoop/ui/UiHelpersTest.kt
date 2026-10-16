package com.snoop.ui

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.internal.buildCurl
import com.snoop.ui.internal.entriesToJsonCapped
import com.snoop.ui.internal.formatBytes
import com.snoop.ui.internal.formatDuration
import com.snoop.ui.internal.groupEntries
import com.snoop.ui.internal.stableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        assertTrue(curl.contains("-X 'POST'"))
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

    @Test
    fun quotesTheMethodSoItCannotEscapeTheCommand() {
        val txn = NetworkTransaction(
            id = "1",
            timestamp = Instant.fromEpochMilliseconds(0),
            method = "POST; rm -rf ~",
            url = "https://api.test/x",
            host = "api.test",
            path = "/x",
        )
        assertTrue(buildCurl(txn).contains("-X 'POST; RM -RF ~'"), buildCurl(txn))
    }
}

class FormattingTest {
    @Test
    fun formatBytesRoundsAndReachesGigabytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.0 KB", formatBytes(2038))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2_147_483_648))
        assertEquals("—", formatBytes(-1))
    }

    @Test
    fun formatDurationRoundsAndRejectsNegatives() {
        assertEquals("999 ms", formatDuration(999))
        assertEquals("1.0 s", formatDuration(1000))
        assertEquals("2.0 s", formatDuration(1_950))
        assertEquals("—", formatDuration(-1))
    }
}

class ExportJsonTest {
    @Test
    fun serializesNetworkAndAnalytics() {
        val (json, kept) = entriesToJsonCapped(
            listOf(
                NetworkTransaction("1", Instant.fromEpochMilliseconds(0), "GET", "https://h/a", "h", "/a", status = 200),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(0), "purchase", "firebase"),
            ),
        )
        assertEquals(2, kept)
        assertTrue(json.contains("\"type\": \"network\""))
        assertTrue(json.contains("\"type\": \"analytics\""))
        assertTrue(json.contains("\"purchase\""))
    }

    @Test
    fun repeatedHeadersSurviveTheExportInOrder() {
        val (json, _) = entriesToJsonCapped(
            listOf(
                NetworkTransaction(
                    "1", Instant.fromEpochMilliseconds(0), "GET", "https://h/a", "h", "/a", status = 200,
                    responseHeaders = listOf(
                        KeyValue("Set-Cookie", "a=1"),
                        KeyValue("Set-Cookie", "b=2"),
                        KeyValue("Content-Type", "text/plain"),
                    ),
                ),
            ),
        )
        val headers = Json.parseToJsonElement(json).jsonArray.single()
            .jsonObject.getValue("responseHeaders").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content to it.jsonObject.getValue("value").jsonPrimitive.content }

        assertEquals(
            listOf("Set-Cookie" to "a=1", "Set-Cookie" to "b=2", "Content-Type" to "text/plain"),
            headers,
        )
    }

    @Test
    fun analyticsPropertiesStayAnObject() {
        val (json, _) = entriesToJsonCapped(
            listOf(
                AnalyticsEntry(
                    "1", Instant.fromEpochMilliseconds(0), "purchase", "firebase",
                    properties = listOf(KeyValue("value", "42.5")),
                ),
            ),
        )
        val properties = Json.parseToJsonElement(json).jsonArray.single().jsonObject.getValue("properties")
        assertEquals("42.5", properties.jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun exportCarriesSectionsAndIssuesOnlyWhenPresent() {
        val (json, _) = entriesToJsonCapped(
            listOf(
                AnalyticsEntry(
                    "1", Instant.fromEpochMilliseconds(0), "search", "firebase",
                    sections = mapOf("User properties" to listOf(KeyValue("user_logged", "false"))),
                    issues = listOf("missing required: search_term"),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(0), "view_item_list", "server-side"),
            ),
        )
        val (withSection, without) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        val section = withSection.getValue("sections").jsonArray.single().jsonObject
        assertEquals("User properties", section.getValue("title").jsonPrimitive.content)
        assertEquals("false", section.getValue("properties").jsonObject.getValue("user_logged").jsonPrimitive.content)
        assertTrue("sections" !in without)
        assertEquals("missing required: search_term", withSection.getValue("issues").jsonArray.single().jsonPrimitive.content)
        assertTrue("issues" !in without)
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
    fun uncappedExportKeepsEveryEntryOldestFirst() {
        val entries = (1..5).map {
            NetworkTransaction("$it", Instant.fromEpochMilliseconds(it.toLong()), "GET", "https://h/$it", "h", "/$it")
        }
        val (json, kept) = entriesToJsonCapped(entries, maxChars = Int.MAX_VALUE)

        assertEquals(5, kept)
        val ids = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("1", "2", "3", "4", "5"), ids)
    }

    @Test
    fun emptySessionExportsAnEmptyArray() {
        val (json, kept) = entriesToJsonCapped(emptyList())
        assertEquals(0, kept)
        assertEquals(0, Json.parseToJsonElement(json).jsonArray.size)
    }
}

class GroupingTest {
    private fun event(id: String, name: String, groupLabel: String? = null) = AnalyticsEntry(
        id = id,
        timestamp = Instant.fromEpochMilliseconds(id.toLong()),
        name = name,
        channel = "firebase",
        groupLabel = groupLabel,
    )

    private fun call(id: String) = NetworkTransaction(
        id, Instant.fromEpochMilliseconds(id.toLong()), "GET", "https://h/$id", "h", "/$id",
    )

    @Test
    fun groupsByStampedEventsNewestFirst() {
        val entries: List<SnoopEntry> = listOf(
            event("1", "screen_view", "home"),
            call("2"),
            event("3", "screen_view", "cart"),
            call("4"),
        )
        val groups = groupEntries(entries, grouped = true)
        assertEquals(listOf("cart", "home"), groups.map { it.screen })
        // within the newest group, entries are newest-first
        assertEquals(listOf("4", "3"), groups.first().entries.map { it.id })
    }

    @Test
    fun entriesBeforeTheFirstStampLandInUnnamedGroup() {
        val groups = groupEntries(listOf(call("1"), event("2", "screen_view", "home")), grouped = true)
        assertEquals(listOf("home", null), groups.map { it.screen })
    }

    @Test
    fun revisitingTheSameScreenOpensASecondGroup() {
        val entries: List<SnoopEntry> = listOf(
            event("1", "screen_view", "home"),
            call("2"),
            event("3", "screen_view", "home"),
            call("4"),
        )
        val groups = groupEntries(entries, grouped = true)
        assertEquals(listOf("home", "home"), groups.map { it.screen })
        assertEquals(listOf("3", "1"), groups.map { it.stableKey() })
    }

    @Test
    fun stableKeyUnchangedWhenNewestGroupGrows() {
        val before = groupEntries(listOf(event("1", "screen_view", "home"), call("2")), grouped = true)
        val after = groupEntries(
            listOf(event("1", "screen_view", "home"), call("2"), call("3")),
            grouped = true,
        )
        assertEquals(before.first().stableKey(), after.first().stableKey())
        assertEquals("1", after.first().stableKey())
    }

    @Test
    fun ungroupedKeepsSingleGroup() {
        val groups = groupEntries(listOf(call("1"), call("2")), grouped = false)
        assertEquals(1, groups.size)
        assertEquals(listOf("2", "1"), groups.single().entries.map { it.id })
    }

    @Test
    fun anUnstampedAnalyticsEventNeverOpensAGroup() {
        val groups = groupEntries(listOf(event("1", "screen_view"), call("2")), grouped = true)
        assertEquals(listOf<String?>(null), groups.map { it.screen })
    }

    @Test
    fun theHeaderRemembersWhichEventOpenedTheGroup() {
        val groups = groupEntries(listOf(event("1", "page_shown", "checkout"), call("2")), grouped = true)
        assertEquals(listOf("checkout"), groups.map { it.screen })
        assertEquals(listOf("page_shown"), groups.map { it.openedBy })
    }
}
