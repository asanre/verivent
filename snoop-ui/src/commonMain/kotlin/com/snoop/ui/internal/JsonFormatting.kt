package com.snoop.ui.internal

import com.snoop.core.model.KeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val prettyJson = Json { prettyPrint = true }

/** Re-indent a JSON payload for the "Pretty" view; null if it isn't valid JSON (show Raw instead). */
internal fun prettyPrintJson(raw: String): String? = try {
    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
} catch (_: Throwable) {
    null
}

/** Collapse a JSON payload to one line for the "Raw" view; null if it isn't valid JSON. */
internal fun compactPrintJson(raw: String): String? = try {
    Json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
} catch (_: Throwable) {
    null
}

/** Render analytics properties as a JSON object so the payload gets the same Pretty/Raw treatment. */
internal fun propertiesToJson(properties: List<KeyValue>): String =
    Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { properties.forEach { put(it.name, it.value) } },
    )
