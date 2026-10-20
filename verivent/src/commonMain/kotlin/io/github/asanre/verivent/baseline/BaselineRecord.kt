package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.Fact
import io.github.asanre.verivent.analytics.exportedValues
import io.github.asanre.verivent.analytics.jsonType
import io.github.asanre.verivent.core.model.AnalyticsEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
                    val values = exportedValues(entry.properties)
                    putJsonObject("schema") {
                        putJsonArray("required") { values.json.keys.forEach { add(it) } }
                        putJsonObject("properties") {
                            values.json.forEach { (name, value) -> put(name, propertySchema(value, values.facts[name])) }
                        }
                        put("additionalProperties", false)
                    }
                }
            }
        }
    },
)

/**
 * The value sent as a `const`. A redacted one only keeps its type, and one with no JSON type (or no
 * type at all) is only expected to be there. A list keeps its shape instead: its items vary from run to run, and
 * the test says which one it knows with `contains`.
 */
private fun propertySchema(value: JsonElement, fact: Fact?): JsonObject = when (fact) {
    null -> if (value is JsonArray) shapeOf(listOf(value)) else buildJsonObject { put("const", value) }
    is Fact.Opaque -> typeSchema(listOf(fact.type))
    Fact.Hidden -> typeSchema(listOf(jsonType(value)))
    Fact.Untyped, is Fact.Foreign -> JsonObject(emptyMap())
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
