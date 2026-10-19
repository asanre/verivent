package io.github.asanre.verivent.analytics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The host's properties as JSON, plus the class name of each top-level value JSON has no type for
 * (a `Date`, an enum…). Such a value travels as its `toString()`, but the plan still reports it by
 * its class, as it always has; one nested deeper is just a string.
 */
internal class HostValues(val json: JsonObject, val foreignTypes: Map<String, String>) {

    /** The JSON type of the top-level property [key], as the export carries it. */
    fun typeOf(key: String): String? = foreignTypes[key] ?: json[key]?.let(::jsonType)
}

internal fun hostValues(properties: Map<String, Any?>): HostValues {
    val budget = Budget()
    val foreign = HashMap<String, String>()
    val json = JsonObject(
        properties.mapValues { (key, value) ->
            if (!value.isJsonValue()) foreign[key] = value!!::class.simpleName ?: "unknown"
            toJson(value, depth = 1, budget)
        },
    )
    return HostValues(json, foreign)
}

/** One host value as JSON, with the same limits as [hostValues]. */
internal fun jsonOf(value: Any?): JsonElement = toJson(value, depth = 1, Budget())

/** The value as the export and the viewers show it: strings bare, everything else as JSON. */
internal fun JsonElement.displayText(): String = if (this is JsonPrimitive && isString) content else toString()

/** The JSON Schema type name of a value, used in messages instead of the value itself. */
internal fun jsonType(element: JsonElement): String = when (element) {
    is JsonNull -> "null"
    is JsonObject -> "object"
    is JsonArray -> "array"
    is JsonPrimitive -> when {
        element.isString -> "string"
        element.booleanOrNull != null -> "boolean"
        element.content.none { it == '.' || it == 'e' || it == 'E' } && element.longOrNull != null -> "integer"
        else -> "number"
    }
}

/**
 * Deepest level a property is converted and checked to. A host can hand over a map that contains
 * itself, and on Kotlin/Native a stack overflow aborts the process instead of throwing.
 */
internal const val MAX_DEPTH = 64

/** Stand-in for whatever lies past [MAX_DEPTH] or past the node budget. */
private val CUT = JsonPrimitive("…")

/** Containers converted per event: a map holding itself twice would otherwise double per level. */
private class Budget(var left: Int = 10_000)

private fun toJson(value: Any?, depth: Int, budget: Budget): JsonElement {
    if (value is Map<*, *> || value is Collection<*> || value is Array<*>) {
        if (depth > MAX_DEPTH || budget.left-- <= 0) return CUT
    }
    return when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        // Passed as is, not widened: a Float widened to Double prints 1.100000023841858.
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v, depth + 1, budget) })
        is Collection<*> -> JsonArray(value.map { toJson(it, depth + 1, budget) })
        is Array<*> -> JsonArray(value.map { toJson(it, depth + 1, budget) })
        else -> JsonPrimitive(value.toString())
    }
}

private fun Any?.isJsonValue(): Boolean =
    this == null || this is String || this is Boolean || this is Number ||
        this is Map<*, *> || this is Collection<*> || this is Array<*>
