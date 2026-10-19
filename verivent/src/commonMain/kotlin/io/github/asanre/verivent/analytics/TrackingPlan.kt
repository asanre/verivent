package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

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
 * Each schema is a JSON Schema subset over the event's properties: `type`, `enum`, `const`,
 * `pattern`, `properties`, `required`, `additionalProperties` (true when absent, as in JSON Schema),
 * `items` (one schema for every element), `minItems` and `contains`, nested as deep as the values.
 * Other keywords are ignored.
 */
internal class TrackingPlan private constructor(
    val version: String,
    private val schemas: Map<PlanEvent, Schema>,
) {

    val summary = PlanSummary(version, schemas.keys.toList())

    /**
     * What breaks the plan in this event, or null when the plan doesn't define it. Top-level
     * properties in [hidden] are checked, all the way down, for presence and type only; those in
     * [untyped] only for presence. [foreignTypes] names the type of top-level values that aren't
     * JSON. Findings inside lists are grouped by path, so 60 broken items are one finding.
     */
    fun check(
        name: String,
        channel: String,
        properties: JsonObject,
        hidden: Set<String> = emptySet(),
        untyped: Set<String> = emptySet(),
        foreignTypes: Map<String, String> = emptyMap(),
    ): List<Finding>? {
        return schemas[PlanEvent(name, channel)]?.check(properties, hidden, untyped, foreignTypes)
    }

    /** [check] on the host's own values, as one [PlanIssue] per finding. */
    fun issuesFor(
        name: String,
        channel: String,
        properties: Map<String, Any?>,
        hidden: Set<String> = emptySet(),
    ): List<PlanIssue>? {
        val values = hostValues(properties)
        return check(name, channel, values.json, hidden, foreignTypes = values.foreignTypes)?.map { it.toPlanIssue() }
    }

    companion object {
        /** @throws IllegalArgumentException naming what is wrong with [json]. */
        fun parse(json: String): TrackingPlan {
            val root = runCatching { Json.parseToJsonElement(json) }
                .getOrElse { throw IllegalArgumentException("not valid JSON", it) }
                .asObject("the document")
            val version = root["version"].asString("version")
            val events = (root["events"] as? JsonArray) ?: invalid("events must be an array")
            val schemas = mutableMapOf<PlanEvent, Schema>()
            events.forEachIndexed { index, element ->
                val event = element.asObject("events[$index]")
                val key = PlanEvent(event["name"].asString("events[$index].name"), event["channel"].asString("events[$index].channel"))
                require(key !in schemas) { "${key.name} on ${key.channel} is defined twice" }
                schemas[key] = Schema.parse(event["schema"].asObject("${key.name}.schema"), key.name)
            }
            return TrackingPlan(version, schemas)
        }
    }
}

/**
 * One way an event breaks the plan. [path] is the property, `.child` into objects and `[*]` into
 * every item of a list; [count] of [total] items break it when the path goes through a list.
 */
internal data class Finding(val path: String, val message: String, val count: Int? = null, val total: Int? = null) {

    fun toPlanIssue() = PlanIssue(path, if (count == null) message else "$message · $count of $total")
}

/** What breaks this event schema in [properties]; see [TrackingPlan.check] for the sets. */
internal fun Schema.check(
    properties: JsonObject,
    hidden: Set<String> = emptySet(),
    untyped: Set<String> = emptySet(),
    foreignTypes: Map<String, String> = emptyMap(),
): List<Finding> {
    val walk = Walk(hidden, untyped, foreignTypes)
    walk.objectChildren(this, properties, path = "", depth = 0, hidden = false)
    return walk.findings()
}

internal class Schema(
    private val types: List<String>,
    private val allowed: List<JsonPrimitive>?,
    private val const: JsonElement?,
    private val pattern: Regex?,
    val properties: Map<String, Schema>,
    val required: List<String>,
    val additionalProperties: Boolean,
    val items: Schema?,
    private val minItems: Int?,
    val contains: Schema?,
) {

    /** A value of the wrong type isn't looked into any further. */
    fun typeIssue(value: JsonElement, type: String): String? =
        if (types.isNotEmpty() && types.none { type.satisfies(it, value) }) "Expected ${types.joinToString(" or ")}, got $type" else null

    /** The checks on the value itself; a [foreign] value only ever equals itself. */
    fun valueIssue(value: JsonElement, foreign: Boolean): String? = when {
        allowed != null && (foreign || allowed.none { jsonEquals(it, value) }) ->
            "Not an allowed value · expects ${allowed.map { it.content }.joinAsChoice()}"
        const != null && (foreign || !jsonEquals(const, value)) -> "Not the expected value · expects ${const.displayText()}"
        foreign -> null
        pattern != null && value is JsonPrimitive && value.isString && value.content.length > MAX_PATTERN_INPUT ->
            "Too long to check against the pattern · over $MAX_PATTERN_INPUT characters"
        pattern != null && value is JsonPrimitive && value.isString && !pattern.containsMatchIn(value.content) ->
            "Doesn't match the pattern ${pattern.pattern}"
        minItems != null && value is JsonArray && value.size < minItems -> "Expected at least $minItems items"
        else -> null
    }

    companion object {
        fun parse(schema: JsonObject, path: String): Schema = Schema(
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
            const = schema["const"],
            pattern = schema["pattern"]?.let { pattern ->
                val source = pattern.asString("$path.pattern")
                runCatching { Regex(source) }.getOrElse { invalid("$path.pattern is not a valid regex") }
            },
            properties = schema["properties"]?.asObject("$path.properties").orEmpty()
                .mapValues { (key, value) -> parse(value.asObject("$path.$key"), "$path.$key") },
            required = schema["required"]?.let { it as? JsonArray ?: invalid("$path.required must be an array") }
                .orEmpty()
                .map { it.asString("$path.required") },
            additionalProperties = schema["additionalProperties"]?.let { flag ->
                (flag as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                    ?: invalid("$path.additionalProperties must be true or false")
            } ?: true,
            items = schema["items"]?.let { parse(it.asObject("$path.items"), "$path.items") },
            minItems = schema["minItems"]?.let { min ->
                (min as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it >= 0 }
                    ?: invalid("$path.minItems must be a non-negative integer")
            },
            contains = schema["contains"]?.let { parse(it.asObject("$path.contains"), "$path.contains") },
        )
    }
}

/**
 * One pass over an event. Issues are keyed by path and message in the order they are first seen,
 * and every item a `[*]` path visits is counted, so a finding can say how many items break it.
 */
private class Walk(
    private val hiddenRoots: Set<String>,
    private val untypedRoots: Set<String>,
    private val foreignTypes: Map<String, String>,
) {
    private val issues = LinkedHashMap<Pair<String, String>, Int>()
    private val visits = HashMap<String, Int>()

    fun findings(): List<Finding> = issues.map { (key, count) ->
        val (path, message) = key
        val list = path.substringBeforeLast("[*]", missingDelimiterValue = "")
        if (list.isEmpty()) Finding(path, message) else Finding(path, message, count, visits["$list[*]"] ?: count)
    }

    fun objectChildren(schema: Schema, value: JsonObject, path: String, depth: Int, hidden: Boolean) {
        schema.required.filterNot { it in value }.forEach { add(child(path, it), "Required, but not sent") }
        value.forEach { (key, child) ->
            val childPath = child(path, key)
            val childSchema = schema.properties[key]
            when {
                childSchema != null -> node(
                    schema = childSchema,
                    value = child,
                    path = childPath,
                    depth = depth + 1,
                    hidden = hidden || (depth == 0 && key in hiddenRoots),
                    untyped = depth == 0 && key in untypedRoots,
                    foreignType = if (depth == 0) foreignTypes[key] else null,
                )
                !schema.additionalProperties -> add(childPath, "Not in the plan for this event")
            }
        }
    }

    private fun node(
        schema: Schema,
        value: JsonElement,
        path: String,
        depth: Int,
        hidden: Boolean,
        untyped: Boolean = false,
        foreignType: String? = null,
    ) {
        if (untyped) return
        if (depth > MAX_DEPTH) return add(path, "Too deeply nested to check · over $MAX_DEPTH levels")
        schema.typeIssue(value, foreignType ?: jsonType(value))?.let { return add(path, it) }
        // A hidden value is only ever checked for presence and type: any other verdict reveals it.
        if (!hidden) schema.valueIssue(value, foreign = foreignType != null)?.let { add(path, it) }
        when (value) {
            is JsonObject -> objectChildren(schema, value, path, depth, hidden)
            is JsonArray -> {
                schema.items?.let { items ->
                    val itemPath = "$path[*]"
                    value.forEach { item ->
                        visits[itemPath] = (visits[itemPath] ?: 0) + 1
                        node(items, item, itemPath, depth + 1, hidden)
                    }
                }
                val contains = schema.contains
                if (contains != null && !hidden && value.none { item -> matches(contains, item, depth + 1) }) {
                    add(path, "No item matches the expected one")
                }
            }
            else -> Unit
        }
    }

    /** Whether [value] complies with [schema], checked apart so its issues don't leak into this event's. */
    private fun matches(schema: Schema, value: JsonElement, depth: Int): Boolean {
        val probe = Walk(emptySet(), emptySet(), emptyMap())
        probe.node(schema, value, path = "", depth = depth, hidden = false)
        return probe.issues.isEmpty()
    }

    private fun add(path: String, message: String) {
        val key = path to message
        issues[key] = (issues[key] ?: 0) + 1
    }

    private fun child(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"
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

internal val JSON_TYPES = setOf("string", "integer", "number", "boolean", "null", "object", "array")

private fun String.satisfies(expected: String, value: JsonElement): Boolean = when {
    this == expected -> true
    expected == "number" -> this == "integer"
    expected == "integer" -> this == "number" && value.isWholeNumber()
    else -> false
}

private fun JsonElement.isWholeNumber(): Boolean =
    (this as? JsonPrimitive)?.doubleOrNull?.let { it.isFinite() && it % 1.0 == 0.0 } == true

/** JSON equality, where numbers compare by value: `1` equals `1.0`. */
internal fun jsonEquals(a: JsonElement, b: JsonElement): Boolean = when {
    a is JsonObject && b is JsonObject -> a.keys == b.keys && a.all { (key, value) -> jsonEquals(value, b.getValue(key)) }
    a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { jsonEquals(a[it], b[it]) }
    a is JsonPrimitive && b is JsonPrimitive -> when {
        a.isString || b.isString -> a.isString && b.isString && a.content == b.content
        a.booleanOrNull != null || b.booleanOrNull != null -> a.booleanOrNull == b.booleanOrNull
        a.content == "null" || b.content == "null" -> a.content == b.content
        a.content == b.content -> true
        else -> a.doubleOrNull != null && a.doubleOrNull == b.doubleOrNull
    }
    else -> false
}

internal fun JsonElement?.asObject(what: String): JsonObject = this as? JsonObject ?: invalid("$what must be an object")

internal fun JsonElement?.asString(what: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("$what must be a string")

internal fun invalid(reason: String): Nothing = throw IllegalArgumentException(reason)
