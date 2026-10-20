package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.Fact
import io.github.asanre.verivent.analytics.exportedValues
import io.github.asanre.verivent.analytics.invalid
import io.github.asanre.verivent.analytics.jsonEquals
import io.github.asanre.verivent.analytics.jsonOf
import io.github.asanre.verivent.analytics.jsonType
import io.github.asanre.verivent.core.model.AnalyticsEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * [sent] as the events a step expects, to be polished by hand: each property a `const` of the
 * value sent, or `{{name}}` where it's a value the test [bound][FlowScope.bound]. A redacted value
 * keeps only its type, and a list only its shape, plus a `contains` for an item holding a bound value.
 */
internal fun recordEvents(sent: List<AnalyticsEntry>, variables: Map<String, Any?>): List<BaselineFile.ExpectedEvent> {
    val bound = Bound(variables)
    return sent.map { entry ->
        val values = exportedValues(entry.properties)
        val schema = buildJsonObject {
            putJsonArray("required") { values.json.keys.forEach { add(it) } }
            putJsonObject("properties") {
                values.json.forEach { (name, value) -> put(name, propertySchema(value, values.facts[name], bound)) }
            }
            put("additionalProperties", false)
        }
        BaselineFile.ExpectedEvent(entry.key, deferred = false, schema = schema)
    }
}

/** The test's bound values, to write as `{{name}}` wherever one was sent. */
private class Bound(variables: Map<String, Any?>) {
    private val values = variables.map { (name, value) -> name to jsonOf(value) }

    /** The template for [value], when exactly one bound variable holds it. */
    fun templateOf(value: JsonElement): JsonPrimitive? {
        val names = values.filter { (_, bound) -> jsonEquals(bound, value) }.map { it.first }
        if (names.size > 1) invalid("${names.joinToString(" and ")} are bound to the same value: the recording can't tell them apart")
        return names.singleOrNull()?.let { JsonPrimitive("{{$it}}") }
    }

    /** [value] with every part a bound variable holds written as its template. */
    fun templated(value: JsonElement): JsonElement = templateOf(value) ?: when (value) {
        is JsonObject -> JsonObject(value.mapValues { (_, child) -> templated(child) })
        is JsonArray -> JsonArray(value.map(::templated))
        is JsonPrimitive -> value
    }
}

private fun propertySchema(value: JsonElement, fact: Fact?, bound: Bound): JsonObject = when (fact) {
    null -> if (value is JsonArray) listSchema(value, bound) else buildJsonObject { put("const", bound.templated(value)) }
    is Fact.Opaque -> typeSchema(listOf(fact.type))
    Fact.Hidden -> typeSchema(listOf(jsonType(value)))
    Fact.Untyped, is Fact.Foreign -> JsonObject(emptyMap())
}

/**
 * A list by its shape, since its items vary from run to run, plus a `contains` for the first item
 * that holds a bound value: the one item the test knows.
 */
private fun listSchema(list: JsonArray, bound: Bound): JsonObject {
    val shape = shapeOf(listOf(list))
    val known = list.firstNotNullOfOrNull { item ->
        when (item) {
            is JsonObject -> item.entries.firstNotNullOfOrNull { (key, field) ->
                bound.templateOf(field)?.let { template ->
                    buildJsonObject {
                        putJsonObject("properties") { putJsonObject(key) { put("const", template) } }
                        putJsonArray("required") { add(key) }
                    }
                }
            }
            else -> bound.templateOf(item)?.let { template -> buildJsonObject { put("const", template) } }
        }
    } ?: return shape
    return JsonObject(shape + ("contains" to known))
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
    val single = types.singleOrNull()
    if (single != null) put("type", single) else putJsonArray("type") { types.forEach { add(it) } }
}
