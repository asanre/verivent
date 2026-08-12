package com.snoop.ui.internal

import com.snoop.core.model.KeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val prettyJson = Json { prettyPrint = true }

/**
 * Deepest nesting the formatter will attempt. The parser recurses per level, and on Kotlin/Native a
 * stack overflow aborts the process instead of raising something catchable — so a hostile or merely
 * absurd payload has to be turned away before it is parsed, not after it blows up.
 */
private const val MAX_NESTING = 200

/** Re-indent a JSON payload for the "Pretty" view; null if it isn't valid JSON (show Raw instead). */
internal fun prettyPrintJson(raw: String): String? = format(raw, prettyJson)

/** Collapse a JSON payload to one line for the "Raw" view; null if it isn't valid JSON. */
internal fun compactPrintJson(raw: String): String? = format(raw, Json)

private fun format(raw: String, json: Json): String? {
    if (nestingTooDeep(raw, MAX_NESTING)) return null
    return try {
        json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
    } catch (_: Throwable) {
        null
    }
}

/**
 * Linear pre-scan for nesting beyond [max], skipping string literals so a `"{{{"` value can't trip
 * it. Cheap enough to run on every payload and it stops at the first level too deep.
 */
internal fun nestingTooDeep(raw: String, max: Int): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (char in raw) {
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '{' || char == '[' -> if (++depth > max) return true
            char == '}' || char == ']' -> depth--
        }
    }
    return false
}

/** Render analytics properties as a JSON object so the payload gets the same Pretty/Raw treatment. */
internal fun propertiesToJson(properties: List<KeyValue>): String =
    Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { properties.forEach { put(it.name, it.value) } },
    )
