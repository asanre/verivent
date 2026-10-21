package io.github.asanre.verivent.baseline

import io.github.asanre.verivent.analytics.Schema
import io.github.asanre.verivent.analytics.asBoolean
import io.github.asanre.verivent.analytics.asObject
import io.github.asanre.verivent.analytics.asString
import io.github.asanre.verivent.analytics.displayText
import io.github.asanre.verivent.analytics.invalid
import io.github.asanre.verivent.analytics.jsonOf
import io.github.asanre.verivent.analytics.parseObject
import io.github.asanre.verivent.core.model.PlanEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * A baseline file: a flow's steps, each the events it expects. A flow without steps is one step,
 * written flat (`order` and `events` at the root); one with steps lists them under `steps`.
 */
internal class BaselineFile(val name: String, val steps: List<Step>) {

    /** A flow without steps: its one step is written flat. */
    val implicit: Boolean get() = steps.size == 1 && steps[0].name == null

    /** [name] is null for the one step of a flow without steps. */
    class Step(val name: String?, val sequence: Boolean, val events: List<ExpectedEvent>)

    /** [schema] is as written, `{{variables}}` included: they're filled in when it's compared. */
    class ExpectedEvent(val key: PlanEvent, val deferred: Boolean, val schema: JsonObject)

    fun toJson(): String = prettyJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("flow", name)
            if (implicit) {
                putStep(steps.single())
            } else {
                putJsonArray("steps") {
                    steps.forEach { step ->
                        addJsonObject {
                            put("name", step.name)
                            putStep(step)
                        }
                    }
                }
            }
        },
    )

    companion object {
        /** @throws IllegalArgumentException naming what is wrong, like the plan parser. */
        fun parse(json: String): BaselineFile {
            val root = parseObject(json, "the baseline")
            if ("baseline" in root) invalid("recorded with Verivent 0.9: record it again")
            root.keys.firstOrNull { it !in ROOT_KEYS }?.let { invalid("'$it' is not a baseline key: expects ${ROOT_KEYS.joinToString()}") }
            val name = root["flow"].asString("flow")
            val steps = root["steps"]
            val file = when {
                steps == null -> BaselineFile(name, listOf(parseStep(null, root, path = "")))
                "events" in root || "order" in root -> invalid("events and order go inside each step when there are steps")
                else -> {
                    val parsed = (steps as? JsonArray ?: invalid("steps must be an array")).mapIndexed { index, element ->
                        val step = element.asObject("steps[$index]")
                        parseStep(step["name"].asString("steps[$index].name"), step, path = "steps[$index].")
                    }
                    parsed.groupBy { it.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { invalid("step $it is listed twice") }
                    BaselineFile(name, parsed)
                }
            }
            return file.apply { requireDeferredOnce() }
        }

        /**
         * A deferred event is checked against everything sent after its step started, so the same
         * name and channel listed again there or in a later step would claim the same events twice.
         */
        private fun BaselineFile.requireDeferredOnce() {
            steps.forEachIndexed { index, step ->
                step.events.filter { it.deferred }.forEach { deferred ->
                    val later = steps.drop(index + 1).firstOrNull { later -> later.events.any { it.key == deferred.key } }
                    val where = when {
                        step.events.count { it.key == deferred.key } > 1 -> "in its own step"
                        later != null -> "in step ${later.name}"
                        else -> return@forEach
                    }
                    invalid("${deferred.key.label} is deferred, so it can't be listed again $where")
                }
            }
        }

        private fun parseStep(name: String?, step: JsonObject, path: String): Step {
            val events = step["events"] as? JsonArray ?: invalid("${path}events must be an array")
            return Step(
                name = name,
                sequence = when (step["order"]?.asString("${path}order") ?: "set") {
                    "set" -> false
                    "sequence" -> true
                    else -> invalid("${path}order must be set or sequence")
                },
                events = events.mapIndexed { index, element ->
                    val at = "${path}events[$index]"
                    val event = element.asObject(at)
                    ExpectedEvent(
                        key = PlanEvent(event["name"].asString("$at.name"), event["channel"].asString("$at.channel")),
                        deferred = event["deferred"]?.asBoolean("$at.deferred") ?: false,
                        schema = event["schema"].asObject("$at.schema").also { validate(it, "$at.schema") },
                    )
                },
            )
        }

        /**
         * Fails on a schema the plan's parser rejects, before the test runs anything. Its variables
         * are filled with empty text, which any keyword takes, `pattern` included.
         */
        private fun validate(schema: JsonObject, path: String) {
            val blanks = VARIABLE.findAll(schema.toString()).associate { it.groupValues[1] to "" }
            Schema.parse(schema.filledIn(blanks) as JsonObject, path)
        }

        private val ROOT_KEYS = listOf("flow", "order", "events", "steps")
    }
}

private val prettyJson = Json { prettyPrint = true }

private fun JsonObjectBuilder.putStep(step: BaselineFile.Step) {
    put("order", if (step.sequence) "sequence" else "set")
    putJsonArray("events") {
        step.events.forEach { event ->
            addJsonObject {
                put("name", event.key.name)
                put("channel", event.key.channel)
                if (event.deferred) put("deferred", true)
                put("schema", event.schema)
            }
        }
    }
}

private val VARIABLE = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")

/**
 * Fills `{{name}}` in every string. A string that is just the variable becomes its value, type
 * included (`"{{count}}"` with 3 is the integer 3); one inside a longer string is interpolated.
 * Done on the parsed tree, so a value can never break out of its string.
 */
internal fun JsonElement.filledIn(variables: Map<String, Any?>): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.filledIn(variables) })
    is JsonArray -> JsonArray(map { it.filledIn(variables) })
    is JsonPrimitive -> if (!isString || "{{" !in content) this else {
        fun valueOf(name: String): JsonElement {
            if (name !in variables) invalid("{{$name}} has no value: bind it in the flow")
            return jsonOf(variables[name])
        }
        VARIABLE.matchEntire(content)?.let { valueOf(it.groupValues[1]) }
            ?: JsonPrimitive(VARIABLE.replace(content) { valueOf(it.groupValues[1]).displayText() })
    }
}
