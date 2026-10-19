package com.snoop.ui

import com.snoop.ui.internal.compactPrintJson
import com.snoop.ui.internal.nestingTooDeep
import com.snoop.ui.internal.prettyPrintJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonFormattingTest {

    @Test
    fun absurdlyDeepPayloadIsRefusedInsteadOfParsed() {
        // On Kotlin/Native the parser would blow the stack, taking the host process with it.
        val bomb = "[".repeat(10_000) + "]".repeat(10_000)
        assertNull(prettyPrintJson(bomb))
        assertNull(compactPrintJson(bomb))
    }

    @Test
    fun ordinarilyNestedPayloadsStillFormat() {
        val deep = "[".repeat(50) + "1" + "]".repeat(50)
        assertEquals(50, prettyPrintJson(deep)?.count { it == '[' })
        assertEquals(deep, compactPrintJson(deep))
    }

    @Test
    fun bracesInsideStringsDoNotCountAsNesting() {
        assertFalse(nestingTooDeep("""{"tpl":"{{{{{{"}""", max = 3))
        assertFalse(nestingTooDeep("""{"escaped":"\"{{{{{{"}""", max = 3))
        assertTrue(nestingTooDeep("[[[[[]]]]]", max = 3))
    }

    @Test
    fun depthExactlyAtTheLimitStillFormats() {
        assertFalse(nestingTooDeep("[[[]]]", max = 3))
        assertTrue(nestingTooDeep("[[[[]]]]", max = 3))
    }

    @Test
    fun nonJsonStaysNull() {
        assertNull(prettyPrintJson("not json at all"))
        assertNull(compactPrintJson("not json at all"))
    }
}
