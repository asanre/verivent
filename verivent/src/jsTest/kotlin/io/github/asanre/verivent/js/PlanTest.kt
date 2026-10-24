package io.github.asanre.verivent.js

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlanTest {

    private val plan = Plan(
        """{ "version": "1.4.0", "events": [ { "name": "play", "channel": "firebase", "schema": {
             "required": ["tracks"],
             "properties": { "tracks": { "type": "array",
               "items": { "properties": { "duration_ms": { "type": "integer" } } } } } } } ] }""",
    )

    @Test
    fun describesThePlan() {
        assertEquals("1.4.0", plan.version)
        assertEquals(listOf("play" to "firebase"), plan.events.map { it.name to it.channel })
    }

    @Test
    fun checksAnEventFromItsJsonProperties() {
        val tracks = (1..60).joinToString(",", "[", "]") { """{"duration_ms":"$it"}""" }
        val issue = plan.check("play", "firebase", """{"tracks":$tracks}""")!!.single()
        assertEquals("tracks[*].duration_ms", issue.path)
        assertEquals("Expected integer, got string", issue.message)
        assertEquals(60, issue.count)
        assertEquals(60, issue.total)
        assertEquals("tracks[*].duration_ms · Expected integer, got string · 60 of 60", issue.line)
        assertNull(plan.check("search", "firebase", "{}"))
    }

    @Test
    fun checksAnExportedSessionAgain() {
        val session = """[
            { "type": "network", "id": "1", "ts": 0 },
            { "type": "analytics", "id": "2", "ts": 0, "name": "play", "channel": "firebase",
              "properties": [ { "name": "tracks", "value": "[{\"duration_ms\":12}]", "redacted": false, "type": "array" } ] },
            { "type": "analytics", "id": "3", "ts": 0, "name": "play", "channel": "firebase", "properties": [] },
            { "type": "analytics", "id": "4", "ts": 0, "name": "login", "channel": "firebase",
              "properties": [ { "name": "user", "value": "x", "redacted": false } ] } ]"""
        val events = plan.checkSession(session)
        assertEquals(listOf("complies", "breaks", "not_in_plan"), events.map { it.verdict })
        assertEquals(listOf("tracks · Required, but not sent"), events[1].issues.map { it.line })
        assertEquals(listOf(false, false, true), events.map { it.untyped })
    }

    @Test
    fun aSessionSaysTheVariantEachEventWasSentAsByTheseRulesNotTheStampedOne() {
        val plan = Plan(
            """{ "version": "2.0.0", "events": [ { "name": "ui_interaction", "channel": "firebase",
                 "schema": { "required": ["element_id"], "properties": { "element_id": { "type": "string" }, "action": {} } },
                 "variants": { "by": ["element_id"], "list": [
                   { "id": "share_playlist", "when": { "element_id": "playlist_share" }, "properties": { "action": { "const": "share" } } } ] } } ] }""",
        )
        fun sent(id: String, element: String, action: String) =
            """{ "type": "analytics", "id": "$id", "ts": 0, "name": "ui_interaction", "channel": "firebase", "variant": "stale",
                 "properties": [ { "name": "element_id", "value": "$element", "redacted": false, "type": "string" },
                                 { "name": "action", "value": "$action", "redacted": false, "type": "string" } ] }"""
        val events = plan.checkSession("[${sent("1", "playlist_share", "share")}, ${sent("2", "playlist_share", "tap")}, ${sent("3", "cart_icon", "tap")}]")
        assertEquals(listOf("share_playlist", "share_playlist", null), events.map { it.variant })
        assertEquals(listOf("action · For share_playlist: Not the expected value · expects share"), events[1].issues.map { it.line })
        assertEquals(listOf("element_id · Not a planned variant · expects playlist_share"), events[2].issues.map { it.line })
    }

    @Test
    fun skipsTheEntryThatOpensAnExport() {
        val session = """[
            { "type": "source", "id": "source", "ts": 1, "source": { "platform": "ios", "veriventVersion": "0.10.1" } },
            { "type": "analytics", "id": "1", "ts": 0, "name": "play", "channel": "firebase", "properties": [] } ]"""
        assertEquals(listOf("1"), plan.checkSession(session).map { it.id })
    }

    @Test
    fun anInvalidPlanThrowsTheDevicesMessage() {
        val error = assertFailsWith<IllegalArgumentException> { Plan("""{ "events": [] }""") }
        assertEquals("version must be a string", error.message)
    }
}
