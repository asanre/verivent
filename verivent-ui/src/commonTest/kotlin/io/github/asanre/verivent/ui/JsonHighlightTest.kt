package io.github.asanre.verivent.ui

import io.github.asanre.verivent.ui.internal.JsonSpan
import io.github.asanre.verivent.ui.internal.JsonTokenType
import io.github.asanre.verivent.ui.internal.jsonSpans
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonHighlightTest {

    private fun typesOf(text: String, type: JsonTokenType): List<String> =
        jsonSpans(text).filter { it.type == type }.map { text.substring(it.start, it.end) }

    @Test
    fun distinguishesKeysFromStringValues() {
        val json = "{\n  \"name\": \"Linen shirt\"\n}"
        assertEquals(listOf("\"name\""), typesOf(json, JsonTokenType.Key))
        assertEquals(listOf("\"Linen shirt\""), typesOf(json, JsonTokenType.StringValue))
    }

    @Test
    fun handlesEscapedQuotesInsideStrings() {
        val json = "{\"msg\": \"say \\\"hi\\\"\"}"
        assertEquals(listOf("\"say \\\"hi\\\"\""), typesOf(json, JsonTokenType.StringValue))
    }

    @Test
    fun tokenizesNumbers() {
        val json = "{\"price\": 39.99, \"count\": -3, \"exp\": 1.2e10}"
        assertEquals(listOf("39.99", "-3", "1.2e10"), typesOf(json, JsonTokenType.Number))
    }

    @Test
    fun tokenizesKeywords() {
        val json = "{\"retryable\": true, \"legacy\": false, \"tag\": null}"
        assertEquals(listOf("true", "false", "null"), typesOf(json, JsonTokenType.Keyword))
    }

    @Test
    fun ignoresBareWordsThatAreNotKeywords() {
        assertEquals(emptyList(), typesOf("truthy nullable", JsonTokenType.Keyword))
    }

    @Test
    fun unterminatedStringDoesNotOverflow() {
        val json = "{\"broken\": \"oops"
        val last = jsonSpans(json).last()
        assertEquals(JsonSpan(11, json.length, JsonTokenType.StringValue), last)
    }
}
