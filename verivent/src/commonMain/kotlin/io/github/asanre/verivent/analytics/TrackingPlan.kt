package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
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
     * What breaks the plan in this event, or null when the plan doesn't define it. Findings inside
     * lists are grouped by path, so 60 broken items are one finding.
     */
    fun check(name: String, channel: String, values: CheckedValues): List<Finding>? =
        schemas[PlanEvent(name, channel)]?.check(values)

    /** [check] as one [PlanIssue] per finding, as events are stamped with. */
    fun issuesFor(name: String, channel: String, values: CheckedValues): List<PlanIssue>? =
        check(name, channel, values)?.map { it.toPlanIssue() }

    companion object {
        /** @throws IllegalArgumentException naming what is wrong with [json]. */
        fun parse(json: String): TrackingPlan {
            val root = parseObject(json, "the document")
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

/** What breaks this event schema in [values]. */
internal fun Schema.check(values: CheckedValues): List<Finding> {
    val walk = Walk()
    walk.objectChildren(this, values.json, path = "", depth = 0, hidden = false, facts = values.facts)
    return walk.findings()
}

internal class Schema(
    private val types: List<String>,
    private val allowed: List<JsonPrimitive>?,
    private val const: JsonElement?,
    /** How the `const` is shown in a message, when it was written as a `{{variable}}`. */
    private val constLabel: String?,
    private val pattern: Regex?,
    val properties: Map<String, Schema>,
    val required: List<String>,
    val additionalProperties: Boolean,
    val items: Schema?,
    private val minItems: Int?,
    val contains: Schema?,
) {

    /**
     * A value of the wrong type isn't looked into any further. [whole] says whether a `number` is a
     * whole one, which satisfies `integer`.
     */
    fun typeIssue(type: String, whole: () -> Boolean): String? =
        if (types.isNotEmpty() && types.none { type.satisfies(it, whole) }) "Expected ${types.joinToString(" or ")}, got $type" else null

    /** The checks on the value itself; a [foreign] value only ever equals itself. */
    fun valueIssue(value: JsonElement, foreign: Boolean): String? = when {
        allowed != null && (foreign || allowed.none { jsonEquals(it, value) }) ->
            "Not an allowed value · expects ${allowed.map { it.content }.joinAsChoice()}"
        const != null && (foreign || !jsonEquals(const, value)) -> "Not the expected value · expects ${constLabel ?: const.displayText()}"
        foreign -> null
        pattern != null && value is JsonPrimitive && value.isString && value.content.length > MAX_PATTERN_INPUT ->
            "Too long to check against the pattern · over $MAX_PATTERN_INPUT characters"
        pattern != null && value is JsonPrimitive && value.isString && !pattern.containsMatchIn(value.content) ->
            "Doesn't match the pattern ${pattern.pattern}"
        minItems != null && value is JsonArray && value.size < minItems -> "Expected at least $minItems items"
        else -> null
    }

    companion object {
        /**
         * [template] is the same schema before its `{{variables}}` were filled in, so a message
         * names the variable rather than the value the test bound to it.
         */
        fun parse(schema: JsonObject, path: String, template: JsonObject? = null): Schema = Schema(
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
            constLabel = template?.get("const")?.takeIf { it != schema["const"] }?.displayText(),
            pattern = schema["pattern"]?.let { pattern ->
                val source = pattern.asString("$path.pattern")
                runCatching { Regex(source) }.getOrElse { invalid("$path.pattern is not a valid regex") }
            },
            properties = schema["properties"]?.asObject("$path.properties").orEmpty()
                .mapValues { (key, value) -> parse(value.asObject("$path.$key"), "$path.$key", template.child("properties", key)) },
            required = schema["required"]?.let { it as? JsonArray ?: invalid("$path.required must be an array") }
                .orEmpty()
                .map { it.asString("$path.required") },
            additionalProperties = schema["additionalProperties"]?.asBoolean("$path.additionalProperties") ?: true,
            items = schema["items"]?.let { parse(it.asObject("$path.items"), "$path.items", template.child("items")) },
            minItems = schema["minItems"]?.let { min ->
                (min as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it >= 0 }
                    ?: invalid("$path.minItems must be a non-negative integer")
            },
            contains = schema["contains"]?.let { parse(it.asObject("$path.contains"), "$path.contains", template.child("contains")) },
        )

        private fun JsonObject?.child(vararg keys: String): JsonObject? =
            keys.fold(this as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) } as? JsonObject
    }
}

/**
 * One pass over an event. Issues are keyed by path and message in the order they are first seen,
 * and every item a `[*]` path visits is counted, so a finding can say how many items break it.
 */
private class Walk {
    private val issues = LinkedHashMap<Pair<String, String>, Int>()
    private val visits = HashMap<String, Int>()

    fun findings(): List<Finding> = issues.map { (key, count) ->
        val (path, message) = key
        val list = path.substringBeforeLast("[*]", missingDelimiterValue = "")
        if (list.isEmpty()) Finding(path, message) else Finding(path, message, count, visits["$list[*]"] ?: count)
    }

    /** Only the event's own properties carry [facts]: nested values are as JSON says. */
    fun objectChildren(
        schema: Schema,
        value: JsonObject,
        path: String,
        depth: Int,
        hidden: Boolean,
        facts: Map<String, Fact> = emptyMap(),
    ) {
        schema.required.filterNot { it in value }.forEach { add(child(path, it), "Required, but not sent") }
        value.forEach { (key, child) ->
            val childPath = child(path, key)
            val childSchema = schema.properties[key]
            when {
                childSchema != null -> node(childSchema, child, childPath, depth + 1, hidden, facts[key])
                !schema.additionalProperties -> add(childPath, "Not in the plan for this event")
            }
        }
    }

    private fun node(schema: Schema, value: JsonElement, path: String, depth: Int, hidden: Boolean, fact: Fact? = null) {
        if (depth > MAX_DEPTH) return add(path, "Too deeply nested to check · over $MAX_DEPTH levels")
        if (value.isCut) return add(path, "Too many lists and maps to check · over 10,000 per event")
        when (fact) {
            Fact.Untyped -> return
            is Fact.Opaque -> {
                // Whatever the value was, the device judged it already: only its type is left.
                schema.typeIssue(fact.type) { true }?.let { add(path, it) }
                return
            }
            else -> Unit
        }
        schema.typeIssue((fact as? Fact.Foreign)?.type ?: jsonType(value)) { value.isWholeNumber() }?.let { return add(path, it) }
        // A hidden value is only ever checked for presence and type: any other verdict reveals it.
        val hiddenHere = hidden || fact == Fact.Hidden
        if (!hiddenHere) schema.valueIssue(value, foreign = fact is Fact.Foreign)?.let { add(path, it) }
        when (value) {
            is JsonObject -> objectChildren(schema, value, path, depth, hiddenHere)
            is JsonArray -> {
                schema.items?.let { items ->
                    val itemPath = "$path[*]"
                    value.forEach { item ->
                        visits[itemPath] = (visits[itemPath] ?: 0) + 1
                        node(items, item, itemPath, depth + 1, hiddenHere)
                    }
                }
                val contains = schema.contains
                if (contains != null && !hiddenHere && value.none { item -> matches(contains, item, depth + 1) }) {
                    add(path, "No item matches the expected one")
                }
            }
            else -> Unit
        }
    }

    /** Whether [value] complies with [schema], checked apart so its issues don't leak into this event's. */
    private fun matches(schema: Schema, value: JsonElement, depth: Int): Boolean {
        val probe = Walk()
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

private fun String.satisfies(expected: String, whole: () -> Boolean): Boolean = when {
    this == expected -> true
    expected == "number" -> this == "integer"
    expected == "integer" -> this == "number" && whole()
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
