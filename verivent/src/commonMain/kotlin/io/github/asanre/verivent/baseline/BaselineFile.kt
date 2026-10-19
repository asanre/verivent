package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.Schema
import io.github.asanre.verivent.analytics.asObject
import io.github.asanre.verivent.analytics.asString
import io.github.asanre.verivent.analytics.displayText
import io.github.asanre.verivent.analytics.invalid
import io.github.asanre.verivent.analytics.jsonOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A parsed baseline: what [Baseline.record] writes, with its `{{variables}}` filled in. */
internal class BaselineFile(val events: List<ExpectedEvent>, val sequence: Boolean, val allowExtra: Boolean) {

    class ExpectedEvent(val name: String, val channel: String, val schema: Schema)

    companion object {
        fun parse(json: String, variables: Map<String, Any?>): BaselineFile {
            val root = runCatching { Json.parseToJsonElement(json) }
                .getOrElse { throw IllegalArgumentException("not valid JSON", it) }
                .asObject("the baseline")
            val events = (root["events"] as? JsonArray) ?: invalid("events must be an array")
            return BaselineFile(
                events = events.mapIndexed { index, element ->
                    val event = element.asObject("events[$index]")
                    val name = event["name"].asString("events[$index].name")
                    val schema = event["schema"].asObject("$name.schema").filledIn(variables) as JsonObject
                    ExpectedEvent(name, event["channel"].asString("events[$index].channel"), Schema.parse(schema, name))
                },
                sequence = when (root["order"]?.asString("order") ?: "set") {
                    "set" -> false
                    "sequence" -> true
                    else -> invalid("order must be set or sequence")
                },
                allowExtra = when (root["extra"]?.asString("extra") ?: "fail") {
                    "fail" -> false
                    "allow" -> true
                    else -> invalid("extra must be fail or allow")
                },
            )
        }
    }
}

private val VARIABLE = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")

/**
 * Fills `{{name}}` in every string. A string that is just the variable becomes its value, type
 * included (`"{{count}}"` with 3 is the integer 3); one inside a longer string is interpolated.
 * Done on the parsed tree, so a value can never break out of its string.
 */
private fun JsonElement.filledIn(variables: Map<String, Any?>): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.filledIn(variables) })
    is JsonArray -> JsonArray(map { it.filledIn(variables) })
    is JsonPrimitive -> if (!isString || "{{" !in content) this else {
        fun valueOf(name: String): JsonElement {
            if (name !in variables) invalid("{{$name}} has no value")
            return jsonOf(variables[name])
        }
        VARIABLE.matchEntire(content)?.let { valueOf(it.groupValues[1]) }
            ?: JsonPrimitive(VARIABLE.replace(content) { valueOf(it.groupValues[1]).displayText() })
    }
}
