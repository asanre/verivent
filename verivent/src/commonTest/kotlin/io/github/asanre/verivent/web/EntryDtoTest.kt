package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.BodyPreview
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EntryDtoTest {

    @Test
    fun encodesNetworkAndAnalyticsWithRedaction() {
        val json = encodeEntries(
            listOf(
                NetworkTransaction(
                    id = "1",
                    timestamp = Instant.fromEpochMilliseconds(1000),
                    method = "GET",
                    url = "https://h/a",
                    host = "h",
                    path = "/a",
                    status = 200,
                    requestHeaders = listOf(KeyValue("Authorization", "██", redacted = true)),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "purchase", "firebase"),
            ),
        )
        assertTrue(json.contains("\"type\":\"network\""))
        assertTrue(json.contains("\"state\":\"complete\""))
        assertTrue(json.contains("\"redacted\":true"))
        assertTrue(json.contains("\"type\":\"analytics\""))
        assertTrue(json.contains("\"channel\":\"firebase\""))
    }

    @OptIn(InternalVeriventApi::class)
    @Test
    fun eventsLeaveTheSourceToTheOpener() {
        val event = AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "purchase", "firebase")
        val exported = Json.parseToJsonElement(encodeExport(listOf(event))).jsonArray.map { it.jsonObject }
        val streamed = Json.parseToJsonElement(encodeEntries(listOf(event))).jsonArray.single().jsonObject

        val analytics = exported.filter { it.getValue("type").jsonPrimitive.content == "analytics" }
        assertEquals(listOf("2"), analytics.map { it.getValue("id").jsonPrimitive.content })
        assertTrue(analytics.none { "source" in it }, "a file states the source once, in its opener")
        assertTrue("source" !in streamed, "the live stream doesn't carry it")
    }

    @Test
    fun aFrameCarriesEveryIdButOnlyTheChangedEntries() {
        val second = AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "two", "firebase")
        val frame = Json.parseToJsonElement(encodeFrame(listOf("1", "2"), listOf(second))).jsonObject

        assertEquals(listOf("1", "2"), frame.getValue("ids").jsonArray.map { it.jsonPrimitive.content })
        val entries = frame.getValue("entries").jsonArray.map { it.jsonObject }
        assertEquals(1, entries.size, "an unchanged entry must not be re-sent")
        assertEquals("2", entries.single().getValue("id").jsonPrimitive.content)
        assertEquals("two", entries.single().getValue("name").jsonPrimitive.content)
    }

    @Test
    fun aStampedEventCarriesItsGroupLabelAndAnUnstampedOneOmitsIt() {
        val json = encodeEntries(
            listOf(
                AnalyticsEntry("1", Instant.fromEpochMilliseconds(1000), "screen_view", "firebase", groupLabel = "Home"),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "purchase", "firebase"),
            ),
        )
        val (opener, plain) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        assertEquals("Home", opener.getValue("groupLabel").jsonPrimitive.content)
        // Omitted, not null: webJson drops defaults, so the page has to treat absent as "no group".
        assertTrue("groupLabel" !in plain)
    }

    @Test
    fun sectionsAreSentOnlyWhenTheEventHasThem() {
        val json = encodeEntries(
            listOf(
                AnalyticsEntry(
                    "1",
                    Instant.fromEpochMilliseconds(1000),
                    "search",
                    "firebase",
                    sections = mapOf("User properties" to listOf(KeyValue("user_logged", "false"))),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "view_item_list", "server-side"),
            ),
        )
        val (withSection, without) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        val section = withSection.getValue("sections").jsonArray.single().jsonObject
        assertEquals("User properties", section.getValue("title").jsonPrimitive.content)
        val property = section.getValue("properties").jsonArray.single().jsonObject
        assertEquals("user_logged", property.getValue("name").jsonPrimitive.content)
        assertEquals("false", property.getValue("value").jsonPrimitive.content)
        assertTrue("sections" !in without)
    }

    @Test
    fun planVerdictIsSentOnlyWhenThePlanChecksTheEvent() {
        val json = encodeEntries(
            listOf(
                AnalyticsEntry(
                    "1", Instant.fromEpochMilliseconds(1000), "search", "firebase",
                    planVersion = "1.0.0", variant = "results", issues = listOf(PlanIssue("q", "Required, but not sent")),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "search", "firebase"),
            ),
        )
        val (withIssues, without) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        assertEquals("1.0.0", withIssues.getValue("planVersion").jsonPrimitive.content)
        assertEquals("results", withIssues.getValue("variant").jsonPrimitive.content)
        val issue = withIssues.getValue("issues").jsonArray.single().jsonObject
        assertEquals("q", issue.getValue("property").jsonPrimitive.content)
        assertEquals("Required, but not sent", issue.getValue("message").jsonPrimitive.content)
        assertTrue("issues" !in without)
        assertTrue("planVersion" !in without)
        assertTrue("variant" !in without)
    }

    @Test
    fun anEmptyFrameStillCarriesBothKeys() {
        val frame = Json.parseToJsonElement(encodeFrame(emptyList(), emptyList())).jsonObject
        assertTrue(frame.getValue("ids").jsonArray.isEmpty())
        assertTrue(frame.getValue("entries").jsonArray.isEmpty())
    }

    @Test
    fun networkCarriesViewerMetadataAndAnalyticsOmitsIt() {
        val json = encodeEntries(
            listOf(
                NetworkTransaction(
                    id = "1",
                    timestamp = Instant.fromEpochMilliseconds(1000),
                    method = "POST",
                    url = "https://h/a",
                    host = "h",
                    path = "/a",
                    status = 200,
                    protocol = "HTTP/2.0",
                    requestContentType = "application/json",
                    responseContentType = "application/json",
                    requestBody = BodyPreview("req", 3, false),
                    responseBody = BodyPreview("res-head", 5_000, true),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "purchase", "firebase"),
            ),
        )
        val (network, analytics) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }

        val newFields = listOf(
            "protocol", "requestContentType", "responseContentType",
            "requestBodyTruncated", "requestByteCount",
            "responseBodyTruncated", "responseByteCount",
        )
        newFields.forEach { assertTrue(it in network, "expected $it on network entry") }
        newFields.forEach { assertTrue(it !in analytics, "expected $it absent on analytics entry") }
        assertEquals("true", network["responseBodyTruncated"].toString())
        assertEquals("5000", network["responseByteCount"].toString())
    }

    @Test
    fun analyticsPropertiesCarryTheirTypeAndHeadersDont() {
        val json = encodeEntries(
            listOf(
                NetworkTransaction(
                    "1", Instant.fromEpochMilliseconds(0), "GET", "https://h/a", "h", "/a",
                    requestHeaders = listOf(KeyValue("Accept", "*/*")),
                ),
                AnalyticsEntry(
                    "2", Instant.fromEpochMilliseconds(0), "purchase", "firebase",
                    properties = listOf(KeyValue("value", "42", type = "integer"), KeyValue("token", "██", redacted = true, type = "string")),
                ),
            ),
        )
        val (network, analytics) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        assertTrue("type" !in network.getValue("requestHeaders").jsonArray.single().jsonObject)
        assertEquals(
            listOf("integer", "string"),
            analytics.getValue("properties").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content },
        )
    }

    @Test
    fun repeatedHeadersSurviveInOrder() {
        val json = encodeEntries(
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
}
