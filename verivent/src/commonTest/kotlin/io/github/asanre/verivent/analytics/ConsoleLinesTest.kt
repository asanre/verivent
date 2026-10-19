package io.github.asanre.verivent.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleLinesTest {

    private fun joined(lines: List<String>) = lines.joinToString("") { it.substringAfter("] ") }

    @Test
    fun aLineThatFitsIsTheBareJson() {
        val json = "x".repeat(100)
        assertEquals(listOf(json), consoleLines("7", json, maxBytes = 100))
    }

    @Test
    fun oneByteOverSplitsIntoNumberedPiecesThatJoinBack() {
        val json = "x".repeat(101)
        val lines = consoleLines("7", json, maxBytes = 100)
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("[7 1/2] "))
        assertTrue(lines[1].startsWith("[7 2/2] "))
        assertEquals(json, joined(lines))
    }

    @Test
    fun everyPieceFitsTheByteLimitWithMultibyteText() {
        val json = "áé€😀".repeat(200)
        val lines = consoleLines("123", json, maxBytes = 120)
        assertTrue(lines.all { it.encodeToByteArray().size <= 120 })
        assertEquals(json, joined(lines))
    }
}
