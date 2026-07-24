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
