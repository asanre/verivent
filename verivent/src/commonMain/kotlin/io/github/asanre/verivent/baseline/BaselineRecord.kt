package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.JSON_TYPES
import io.github.asanre.verivent.analytics.jsonType
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.KeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val prettyJson = Json { prettyPrint = true }

/** A baseline that [Baseline.diff] matches against the very session it was recorded from. */
internal fun recordBaseline(entries: List<AnalyticsEntry>): String = prettyJson.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("baseline", 1)
        put("order", "set")
        put("extra", "fail")
        putJsonArray("events") {
            entries.forEach { entry ->
                addJsonObject {
                    put("name", entry.name)
                    put("channel", entry.channel)
                    putJsonObject("schema") {
                        putJsonArray("required") { entry.properties.forEach { add(it.name) } }
                        putJsonObject("properties") { entry.properties.forEach { put(it.name, propertySchema(it)) } }
                        put("additionalProperties", false)
                    }
                }
            }
        }
    },
)

/**
 * The value sent as a `const`. A redacted one only keeps its type, and one with no JSON type is
 * only expected to be there. A list keeps its shape instead: its items vary from run to run, and
 * the test says which one it knows with `contains`.
 */
private fun propertySchema(property: KeyValue): JsonObject {
    val type = property.type ?: return JsonObject(emptyMap())
    if (property.redacted) return typeSchema(listOf(type))
    if (type !in JSON_TYPES) return JsonObject(emptyMap())
    val value = if (type == "string") JsonPrimitive(property.value) else parsed(property.value) ?: return typeSchema(listOf(type))
    return if (value is JsonArray) shapeOf(listOf(value)) else buildJsonObject { put("const", value) }
}

/** The schema every one of [values] meets, with no `const`: types, nested keys and items. */
private fun shapeOf(values: List<JsonElement>): JsonObject {
    val types = values.map(::jsonType).distinct().let { if ("number" in it) it - "integer" else it }
    val single = types.singleOrNull()
    return when {
        single == "object" -> buildJsonObject {
            put("type", "object")
            val objects = values.map { it as JsonObject }
            val keys = objects.flatMap { it.keys }.distinct()
            putJsonObject("properties") { keys.forEach { key -> put(key, shapeOf(objects.mapNotNull { it[key] })) } }
        }
        single == "array" -> buildJsonObject {
            put("type", "array")
            val items = values.flatMap { it as JsonArray }
            if (items.isNotEmpty()) {
                put("minItems", 1)
                put("items", shapeOf(items))
            }
        }
        else -> typeSchema(types)
    }
}

private fun typeSchema(types: List<String>) = buildJsonObject {
    types.singleOrNull()?.let { put("type", it) } ?: putJsonArray("type") { types.forEach { add(it) } }
}

private fun parsed(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()
