package com.snoop.web

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
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
    fun userPropertiesAreSentOnlyWhenTheEventHasThem() {
        val json = encodeEntries(
            listOf(
                AnalyticsEntry(
                    "1",
                    Instant.fromEpochMilliseconds(1000),
                    "search",
                    "firebase",
                    userProperties = listOf(KeyValue("user_logged", "false")),
                ),
                AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "view_item_list", "server-side"),
            ),
        )
        val (withUser, without) = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        val userProperty = withUser.getValue("userProperties").jsonArray.single().jsonObject
        assertEquals("user_logged", userProperty.getValue("name").jsonPrimitive.content)
        assertEquals("false", userProperty.getValue("value").jsonPrimitive.content)
        assertTrue("userProperties" !in without)
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
}
