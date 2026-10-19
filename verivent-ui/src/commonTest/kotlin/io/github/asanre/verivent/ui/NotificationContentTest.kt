package io.github.asanre.verivent.ui

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NotificationContentTest {

    private fun call(id: String, method: String, path: String, status: Int?, error: String? = null) =
        NetworkTransaction(
            id = id,
            timestamp = Instant.fromEpochMilliseconds(id.toLong()),
            method = method,
            url = "https://h$path",
            host = "h",
            path = path,
            status = status,
            error = error,
        )

    private fun event(id: String, name: String) =
        AnalyticsEntry(id, Instant.fromEpochMilliseconds(id.toLong()), name, "firebase")

    @Test
    fun formatsEachStateAndKeepsNewestFirst() {
        val lines = notificationLines(
            listOf(
                event("1", "screen_view"),
                call("2", "GET", "/pending", status = null),
                call("3", "GET", "/boom", status = null, error = "timeout"),
                call("4", "GET", "/ok", status = 200),
            ),
        )
        assertEquals(
            listOf("200 GET /ok", "❌ GET /boom", "⏳ GET /pending", "● screen_view"),
            lines,
        )
    }

    @Test
    fun capsToTheMostRecentEntries() {
        val entries = (1..8).map { event(it.toString(), "e$it") }
        val lines = notificationLines(entries)
        assertEquals(5, lines.size)
        assertEquals("● e8", lines.first())
        assertEquals("● e4", lines.last())
    }
}
