package io.github.asanre.verivent.ui

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.BodyPreview
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.ui.internal.buildCurl
import io.github.asanre.verivent.ui.internal.entriesToJsonCapped
import io.github.asanre.verivent.ui.internal.formatBytes
import io.github.asanre.verivent.ui.internal.formatDuration
import io.github.asanre.verivent.ui.internal.groupEntries
import io.github.asanre.verivent.ui.internal.GroupTracker
import io.github.asanre.verivent.ui.internal.keptFor
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
    fun exportCarriesOnlyTheAnalyticsEvents() {
        val (json, kept, events) = entriesToJsonCapped(
            listOf(
                AnalyticsEntry("1", Instant.fromEpochMilliseconds(0), "view_item", "firebase"),
                NetworkTransaction("2", Instant.fromEpochMilliseconds(1), "GET", "https://h/a", "h", "/a", status = 200),
                AnalyticsEntry("3", Instant.fromEpochMilliseconds(2), "purchase", "firebase"),
            ),
        )
        assertEquals(2, kept)
        assertEquals(2, events)
        val exported = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        assertEquals(listOf("1", "3"), exported.map { it.getValue("id").jsonPrimitive.content })
        assertTrue(exported.all { it.getValue("type").jsonPrimitive.content == "analytics" })
    }

    @Test
    fun analyticsPropertiesAreTheWebViewerExportWithTheirTypes() {
        val (json, _) = entriesToJsonCapped(
            listOf(
                AnalyticsEntry(
                    "1", Instant.fromEpochMilliseconds(0), "purchase", "firebase",
                    properties = listOf(KeyValue("value", "42.5", type = "number"), KeyValue("token", "██", redacted = true, type = "string")),
                ),
            ),
        )
        val properties = Json.parseToJsonElement(json).jsonArray.single().jsonObject.getValue("properties").jsonArray
            .map { it.jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content } }
        assertEquals(
            listOf(
                mapOf("name" to "value", "value" to "42.5", "redacted" to "false", "type" to "number"),
                mapOf("name" to "token", "value" to "██", "redacted" to "true", "type" to "string"),
            ),
            properties,
        )
    }

    @Test
    fun exportCarriesSectionsAndIssuesOnlyWhenPresent() {
        val (json, _) = entriesToJsonCapped(
            listOf(
                AnalyticsEntry(
                    "1", Instant.fromEpochMilliseconds(0), "search", "firebase",
                    sections = mapOf("User properties" to listOf(KeyValue("user_logged", "false"))),
                    planVersion = "1.0.0",
                    issues = listOf(PlanIssue("search_term", "Required, but not sent")),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(0), "view_item_list", "server-side"),
            ),
        )
        val (withSection, without) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        val section = withSection.getValue("sections").jsonArray.single().jsonObject
        assertEquals("User properties", section.getValue("title").jsonPrimitive.content)
        assertEquals("false", section.getValue("properties").jsonArray.single().jsonObject.getValue("value").jsonPrimitive.content)
        assertTrue("sections" !in without)
        val issue = withSection.getValue("issues").jsonArray.single().jsonObject
        assertEquals("search_term", issue.getValue("property").jsonPrimitive.content)
        assertEquals("1.0.0", withSection.getValue("planVersion").jsonPrimitive.content)
        assertTrue("issues" !in without)
        assertTrue("planVersion" !in without)
    }

    @Test
    fun cappedExportKeepsNewestValidJsonAndReportsCount() {
        val entries = (1..50).map {
            AnalyticsEntry(
                "$it", Instant.fromEpochMilliseconds(it.toLong()), "search", "firebase",
                properties = listOf(KeyValue("search_term", "x".repeat(500))),
            )
        }
        val (json, kept, events) = entriesToJsonCapped(entries, maxChars = 3_000)

        assertEquals(50, events)
        assertTrue(kept in 1 until 50, "expected a capped subset, kept $kept")
        assertEquals(kept, Json.parseToJsonElement(json).jsonArray.size)
        assertTrue(json.contains("\"id\": \"50\""))
        assertFalse(json.contains("\"id\": \"1\""))
    }

    @Test
    fun uncappedExportKeepsEveryEntryOldestFirst() {
        val entries = (1..5).map {
            AnalyticsEntry("$it", Instant.fromEpochMilliseconds(it.toLong()), "search", "firebase")
        }
        val (json, kept) = entriesToJsonCapped(entries, maxChars = Int.MAX_VALUE)

        assertEquals(5, kept)
        val ids = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("1", "2", "3", "4", "5"), ids)
    }

    @Test
    fun emptySessionExportsAnEmptyArray() {
        val (json, kept, events) = entriesToJsonCapped(emptyList())
        assertEquals(0, kept)
        assertEquals(0, events)
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
        val entries: List<TimelineEntry> = listOf(
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
        val entries: List<TimelineEntry> = listOf(
            event("1", "screen_view", "home"),
            call("2"),
            event("3", "screen_view", "home"),
            call("4"),
        )
        val groups = groupEntries(entries, grouped = true)
        assertEquals(listOf("home", "home"), groups.map { it.screen })
        assertEquals(listOf("3", "1"), groups.map { it.key })
    }

    @Test
    fun stableKeyUnchangedWhenNewestGroupGrows() {
        val before = groupEntries(listOf(event("1", "screen_view", "home"), call("2")), grouped = true)
        val after = groupEntries(
            listOf(event("1", "screen_view", "home"), call("2"), call("3")),
            grouped = true,
        )
        assertEquals(before.first().key, after.first().key)
        assertEquals("1", after.first().key)
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

    @Test
    fun aGroupKeepsItsKeyAndLabelWhenItsOpeningEventIsEvicted() {
        val tracker = GroupTracker()
        val before = tracker.group(listOf(event("1", "screen_view", "home"), call("2"), event("3", "screen_view", "cart"), call("4")), grouped = true)
        // The store drops the oldest entry: the event that opened "home".
        val after = tracker.group(listOf(call("2"), event("3", "screen_view", "cart"), call("4")), grouped = true)

        assertEquals(before.map { it.key to it.screen }, after.map { it.key to it.screen })
        assertEquals(listOf("2"), after.last().entries.map { it.id })
    }

    @Test
    fun collapsedGroupsThatNoLongerExistAreForgotten() {
        val groups = groupEntries(listOf(event("3", "screen_view", "cart"), call("4")), grouped = true)

        assertEquals(setOf("3"), setOf("1", "3").keptFor(groups))
    }

    @Test
    fun anEmptyTimelineHasNoGroups() {
        assertEquals(emptyList(), groupEntries(emptyList(), grouped = false))
        assertEquals(emptyList(), groupEntries(emptyList(), grouped = true))
    }
}
