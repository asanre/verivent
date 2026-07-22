package com.snoop.web

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

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
}
