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
    fun anInvalidPlanThrowsTheDevicesMessage() {
        val error = assertFailsWith<IllegalArgumentException> { Plan("""{ "events": [] }""") }
        assertEquals("version must be a string", error.message)
    }
}
