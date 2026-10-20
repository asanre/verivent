package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.KeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Captured properties rebuilt as the plan checks them, so a recorded or exported session can be
 * checked again — against another plan version, a baseline, in the browser. A redacted value keeps
 * only its type; a property without one (exported before types were) is only checked for presence.
 */
internal fun exportedValues(properties: List<KeyValue>): CheckedValues {
    val facts = HashMap<String, Fact>()
    val json = properties.associate { property ->
        val type = property.type
        fun known(fact: Fact, value: JsonElement): JsonElement = value.also { facts[property.name] = fact }
        property.name to when {
            type == null -> known(Fact.Untyped, JsonPrimitive(property.value))
            property.redacted -> known(Fact.Opaque(type), JsonNull)
            type !in JSON_TYPES -> known(Fact.Foreign(type), JsonPrimitive(property.value))
            type == "string" -> JsonPrimitive(property.value)
            // Anything else was captured as JSON text; text that isn't JSON (a NaN) keeps its type only.
            else -> runCatching { Json.parseToJsonElement(property.value) }.getOrNull() ?: known(Fact.Opaque(type), JsonNull)
        }
    }
    return CheckedValues(JsonObject(json), facts)
}
