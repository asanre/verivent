package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.KeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Captured properties rebuilt as the JSON the plan checks, so a recorded or exported session can be
 * checked again — against another plan version, a baseline, in the browser. A redacted value keeps
 * only its type; a property without one (exported before types were) is only checked for presence.
 */
internal class ExportedValues(
    val json: JsonObject,
    val hidden: Set<String>,
    val untyped: Set<String>,
    val foreignTypes: Map<String, String>,
)

internal fun exportedValues(properties: List<KeyValue>): ExportedValues {
    val hidden = HashSet<String>()
    val untyped = HashSet<String>()
    val foreign = HashMap<String, String>()
    val json = properties.associate { property ->
        val type = property.type
        property.name to when {
            type == null -> JsonPrimitive(property.value).also { untyped += property.name }
            // Checked as a value of its type that can't be looked into or compared with anything.
            property.redacted -> JsonNull.also {
                hidden += property.name
                foreign[property.name] = type
            }
            type == "string" -> JsonPrimitive(property.value)
            type !in JSON_TYPES -> JsonPrimitive(property.value).also { foreign[property.name] = type }
            else -> parsed(property.value)?.takeIf { jsonType(it).satisfies(type) } ?: JsonNull.also {
                hidden += property.name
                foreign[property.name] = type
            }
        }
    }
    return ExportedValues(JsonObject(json), hidden, untyped, foreign)
}

private fun parsed(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()

// A whole number captured as `number` can print without its decimals (`2` on JS) and read back as an
// integer, which every `number` schema accepts anyway.
private fun String.satisfies(type: String) = this == type || (this == "integer" && type == "number")

/** [TrackingPlan.check] on captured properties instead of the host's values. */
internal fun TrackingPlan.check(name: String, channel: String, properties: List<KeyValue>): List<Finding>? {
    val values = exportedValues(properties)
    return check(name, channel, values.json, values.hidden, values.untyped, values.foreignTypes)
}
