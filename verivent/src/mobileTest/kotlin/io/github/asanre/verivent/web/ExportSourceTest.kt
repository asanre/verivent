package io.github.asanre.verivent.web

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(InternalVeriventApi::class)
class ExportSourceTest {

    @AfterTest
    fun tearDown() = Inspector.reset()

    private val source = assertNotNull(exportSource, "the devices always know their source")

    @Test
    fun anExportOpensWithTheSourceOnce() {
        val events = listOf(
            AnalyticsEntry("1", Instant.fromEpochMilliseconds(1000), "screen_view", "firebase"),
            AnalyticsEntry("2", Instant.fromEpochMilliseconds(2000), "purchase", "firebase"),
        )
        val (opener, first, second) = Json.parseToJsonElement(encodeExport(events)).jsonArray.map { it.jsonObject }

        assertEquals("source", opener.text("type"))
        assertEquals("source", opener.text("id"))
        assertTrue(opener.getValue("ts").jsonPrimitive.long > 0, "the opener says when the file was written")
        assertEquals(source.platform, opener.getValue("source").jsonObject.text("platform"))
        assertEquals(VERIVENT_VERSION, opener.getValue("source").jsonObject.text("veriventVersion"))
        assertEquals(listOf("1", "2"), listOf(first, second).map { it.text("id") })
        assertTrue(listOf(first, second).none { "source" in it }, "the events don't repeat it")
    }

    @Test
    fun aConsoleLineCarriesTheSourceAFileStatesOnce() {
        val lines = mutableListOf<String>()
        AnalyticsSink { logTo { lines += it } }.log("search", "firebase", mapOf("q" to "shoes"))

        val line = Json.parseToJsonElement(lines.single()).jsonObject
        assertEquals("search", line.text("name"))
        assertEquals(source.platform, line.getValue("source").jsonObject.text("platform"))
        assertEquals(VERIVENT_VERSION, line.getValue("source").jsonObject.text("veriventVersion"))
    }

    @Test
    fun theVersionIsTheReleasedOne() {
        assertTrue(Regex("""\d+\.\d+\.\d+(-\S+)?""").matches(VERIVENT_VERSION), "got '$VERIVENT_VERSION'")
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
}
