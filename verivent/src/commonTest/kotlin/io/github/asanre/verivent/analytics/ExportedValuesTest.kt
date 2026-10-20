package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.web.EntryDto
import io.github.asanre.verivent.web.encodeEntries
import io.github.asanre.verivent.web.toKeyValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** An exported session checked again must give the verdicts it was captured with, nothing more. */
class ExportedValuesTest {

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() = Inspector.reset()

    private val plan = """
        { "version": "1", "events": [ { "name": "play", "channel": "firebase", "schema": {
          "required": ["count"],
          "properties": {
            "count": { "type": "integer" }, "ratio": { "type": "number" }, "whole": { "type": "integer" },
            "flag": { "type": "boolean" }, "nothing": { "type": "null" }, "label": { "type": "string", "pattern": "^a" },
            "token": { "type": "string", "enum": ["a"] }, "rate": { "type": "number" },
            "tracks": { "type": "array", "items": { "type": "object", "properties": { "ms": { "type": "integer" } } } },
            "meta": { "type": "object", "properties": { "id": { "type": "string" } } } } } } ] }
    """

    private fun exportedAgain(): List<List<PlanIssue>?> {
        val json = encodeEntries(Inspector.entriesSnapshot)
        val checker = TrackingPlan.parse(plan)
        return Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(EntryDto.serializer()), json).map { dto ->
            checker.issuesFor(dto.name!!, dto.channel!!, exportedValues(dto.properties.orEmpty().map { it.toKeyValue() }))
        }
    }

    @Test
    fun everyJsonTypeSurvivesTheExportWithoutFalseIssues() {
        Inspector.loadTrackingPlan(plan)
        val sink = AnalyticsSink { sanitizeProperty { it == "token" } }
        sink.log(
            "play", "firebase",
            mapOf(
                "count" to 3, "ratio" to 0.5, "whole" to 2.0, "flag" to true, "nothing" to null, "label" to "abc",
                "token" to "secret", "rate" to 1.5f,
                "tracks" to listOf(mapOf("ms" to 1), mapOf("ms" to 2L)), "meta" to mapOf("id" to "x"),
            ),
        )
        val captured = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>().single()
        assertEquals(emptyList(), captured.issues)
        assertEquals(listOf(emptyList()), exportedAgain())
    }

    @Test
    fun brokenEventsExportTheSameIssuesTheyWereCapturedWith() {
        Inspector.loadTrackingPlan(plan)
        val sink = AnalyticsSink { sanitizeProperty { it == "token" } }
        sink.log(
            "play", "firebase",
            mapOf(
                "ratio" to "half", "label" to "zzz", "token" to 7,
                "tracks" to listOf(mapOf("ms" to "slow"), mapOf("ms" to 2)), "meta" to "{}",
            ),
        )
        val captured = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>().single()
        assertTrue(captured.issues.size == 6, captured.issues.toString())
        assertEquals(listOf(captured.issues), exportedAgain())
    }

    @Test
    fun aRedactedPropertyKeepsItsTypeAndOneWithoutATypeIsOnlyCheckedForPresence() {
        val checker = TrackingPlan.parse(plan)
        val properties = listOf(
            KeyValue("count", "██", redacted = true, type = "integer"),
            KeyValue("label", "zzz"),
            KeyValue("token", "██", redacted = true, type = "boolean"),
            // A whole number captured as a double: the device saw it was whole, so it passed.
            KeyValue("whole", "██", redacted = true, type = "number"),
        )
        assertEquals(
            listOf(PlanIssue("token", "Expected string, got boolean")),
            checker.issuesFor("play", "firebase", exportedValues(properties)),
        )
    }
}
