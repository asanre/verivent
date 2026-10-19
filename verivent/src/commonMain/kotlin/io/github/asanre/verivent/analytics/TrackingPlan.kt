package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The analytics team's definition of each event, parsed from the tracking plan JSON:
 *
 * ```json
 * { "version": "1.4.0",
 *   "events": [ { "name": "search", "channel": "firebase",
 *                 "schema": { "required": ["search_term"],
 *                             "properties": { "search_term": { "type": "string" } },
 *                             "additionalProperties": false } } ] }
 * ```
 *
 * Each schema is a JSON Schema subset over the event's properties: `required`, `properties` with
 * `type`, `enum` and `pattern`, and `additionalProperties` (true when absent, as in JSON Schema).
 * Other keywords are ignored.
 */
internal class TrackingPlan private constructor(
    val version: String,
    private val schemas: Map<PlanEvent, EventSchema>,
) {

    val summary = PlanSummary(version, schemas.keys.toList())

    /** What breaks the plan in this event, or null when the plan doesn't define it. */
    fun issuesFor(name: String, channel: String, properties: Map<String, Any?>): List<PlanIssue>? =
        schemas[PlanEvent(name, channel)]?.issuesFor(properties)

    companion object {
        /** @throws IllegalArgumentException naming what is wrong with [json]. */
        fun parse(json: String): TrackingPlan {
            val root = runCatching { Json.parseToJsonElement(json) }
                .getOrElse { throw IllegalArgumentException("not valid JSON", it) }
                .asObject("the document")
            val version = root["version"].asString("version")
            val events = (root["events"] as? JsonArray) ?: invalid("events must be an array")
            val schemas = mutableMapOf<PlanEvent, EventSchema>()
            events.forEachIndexed { index, element ->
                val event = element.asObject("events[$index]")
                val key = PlanEvent(event["name"].asString("events[$index].name"), event["channel"].asString("events[$index].channel"))
                require(key !in schemas) { "${key.name} on ${key.channel} is defined twice" }
                schemas[key] = EventSchema.parse(event["schema"].asObject("${key.name}.schema"), key.name)
            }
            return TrackingPlan(version, schemas)
        }
    }
}

private class EventSchema(
    private val required: List<String>,
    private val properties: Map<String, PropertySchema>,
    private val additionalProperties: Boolean,
) {

    fun issuesFor(values: Map<String, Any?>): List<PlanIssue> = buildList {
        required.filterNot { it in values }.forEach { add(PlanIssue(it, "Required, but not sent")) }
        values.forEach { (key, value) ->
            val schema = properties[key]
            if (schema == null) {
                if (!additionalProperties) add(PlanIssue(key, "Not in the plan for this event"))
            } else {
                schema.issueFor(value)?.let { add(PlanIssue(key, it)) }
            }
        }
    }

    companion object {
        fun parse(schema: JsonObject, event: String): EventSchema = EventSchema(
            required = schema["required"]?.let { it as? JsonArray ?: invalid("$event.required must be an array") }
                .orEmpty()
                .map { it.asString("$event.required") },
            properties = schema["properties"]?.asObject("$event.properties").orEmpty()
                .mapValues { (key, value) -> PropertySchema.parse(value.asObject("$event.$key"), "$event.$key") },
            additionalProperties = schema["additionalProperties"]?.let { flag ->
                (flag as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                    ?: invalid("$event.additionalProperties must be true or false")
            } ?: true,
        )
    }
}

private class PropertySchema(
    private val types: List<String>,
    private val allowed: List<JsonPrimitive>?,
    private val pattern: Regex?,
) {

    fun issueFor(value: Any?): String? {
        val type = jsonType(value)
        return when {
            types.isNotEmpty() && types.none { type.satisfies(it, value) } ->
                "Expected ${types.joinToString(" or ")}, got $type"
            allowed != null && allowed.none { it.matches(value) } ->
                "Not an allowed value · expects ${allowed.map { it.content }.joinAsChoice()}"
            pattern != null && value is String && value.length > MAX_PATTERN_INPUT ->
                "Too long to check against the pattern · over $MAX_PATTERN_INPUT characters"
            pattern != null && value is String && !pattern.containsMatchIn(value) ->
                "Doesn't match the pattern ${pattern.pattern}"
            else -> null
        }
    }

    companion object {
        fun parse(schema: JsonObject, path: String): PropertySchema = PropertySchema(
            types = when (val type = schema["type"]) {
                null -> emptyList()
                is JsonArray -> type.toList()
                else -> listOf(type)
            }.map { element ->
                element.asString("$path.type").also {
                    require(it in JSON_TYPES) { "$path.type '$it' is not one of $JSON_TYPES" }
                }
            },
            allowed = schema["enum"]?.let { values ->
                (values as? JsonArray)?.map { it as? JsonPrimitive ?: invalid("$path.enum must hold plain values") }
                    ?: invalid("$path.enum must be an array")
            },
            pattern = schema["pattern"]?.let { pattern ->
                val source = pattern.asString("$path.pattern")
                runCatching { Regex(source) }.getOrElse { invalid("$path.pattern is not a valid regex") }
            },
        )
    }
}

/** "a", "a or b", "a, b or c". */
private fun List<String>.joinAsChoice(): String =
    if (size < 2) joinToString() else dropLast(1).joinToString(", ") + " or " + last()

/**
 * Longest value run through a plan's `pattern`. The check runs on the host's thread for every event,
 * and the plan survives restarts, so a pattern that backtracks badly would stall every launch. The
 * cap bounds the polynomial cases; an exponential pattern can still stall on a shorter value.
 */
private const val MAX_PATTERN_INPUT = 256

private val JSON_TYPES = setOf("string", "integer", "number", "boolean", "null", "object", "array")

/** The JSON Schema type name of a value the host passed, used in messages instead of the value. */
private fun jsonType(value: Any?): String = when (value) {
    null -> "null"
    is String -> "string"
    is Boolean -> "boolean"
    is Byte, is Short, is Int, is Long -> "integer"
    is Float, is Double -> "number"
    is Map<*, *> -> "object"
    is Collection<*>, is Array<*> -> "array"
    else -> value::class.simpleName ?: "unknown"
}

private fun String.satisfies(expected: String, value: Any?): Boolean = when {
    this == expected -> true
    expected == "number" -> this == "integer"
    expected == "integer" -> this == "number" && value.isWholeNumber()
    else -> false
}

private fun Any?.isWholeNumber(): Boolean = this is Number && toDouble().let { it.isFinite() && it % 1.0 == 0.0 }

private fun JsonPrimitive.matches(value: Any?): Boolean = when {
    this is JsonNull -> value == null
    isString -> value is String && value == content
    value is Boolean -> booleanOrNull == value
    value is Number -> doubleOrNull == value.toDouble()
    else -> false
}

private fun JsonElement?.asObject(what: String): JsonObject = this as? JsonObject ?: invalid("$what must be an object")

private fun JsonElement?.asString(what: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("$what must be a string")

private fun invalid(reason: String): Nothing = throw IllegalArgumentException(reason)
