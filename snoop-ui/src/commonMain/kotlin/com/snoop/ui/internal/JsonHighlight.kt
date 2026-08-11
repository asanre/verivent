package com.snoop.ui.internal

internal enum class JsonTokenType { Key, StringValue, Number, Keyword, Punctuation }

internal data class JsonSpan(val start: Int, val end: Int, val type: JsonTokenType)

/**
 * Spans for coloring already-formatted JSON. A linear lexer, not a parser: the input comes from
 * [prettyPrintJson], so structure is trusted. A string is a key when the next non-space char is `:`.
 */
internal fun jsonSpans(text: String): List<JsonSpan> = buildList {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '"' -> {
                val start = i
                i++
                while (i < text.length) {
                    when (text[i]) {
                        '\\' -> i += 2
                        '"' -> {
                            i++
                            break
                        }
                        else -> i++
                    }
                }
                var j = i
                while (j < text.length && text[j].isWhitespace()) j++
                val type = if (j < text.length && text[j] == ':') JsonTokenType.Key else JsonTokenType.StringValue
                add(JsonSpan(start, minOf(i, text.length), type))
            }
            c == '-' || c.isDigit() -> {
                val start = i
                while (i < text.length && (text[i].isDigit() || text[i] in "-+.eE")) i++
                add(JsonSpan(start, i, JsonTokenType.Number))
            }
            c.isLetter() -> {
                val start = i
                while (i < text.length && text[i].isLetter()) i++
                if (text.substring(start, i) in setOf("true", "false", "null")) {
                    add(JsonSpan(start, i, JsonTokenType.Keyword))
                }
            }
            c in "{}[],:" -> {
                add(JsonSpan(i, i + 1, JsonTokenType.Punctuation))
                i++
            }
            else -> i++
        }
    }
}
