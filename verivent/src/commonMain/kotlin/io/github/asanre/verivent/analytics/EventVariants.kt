package io.github.asanre.verivent.analytics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The variants of an event sent with the same properties in several ways (a generic event), told
 * apart by the values of the properties in [by]: each variant only narrows what the event's schema
 * already allows.
 *
 * ```json
 * "variants": { "by": ["element_id"],
 *               "list": [ { "id": "share_playlist", "when": { "element_id": "playlist_share" },
 *                           "required": ["item_id"],
 *                           "properties": { "action": { "const": "share" }, "item_id": { "pattern": "^pl_" } } } ] }
 * ```
 */
internal class EventVariants private constructor(private val by: List<String>, private val list: List<Variant>) {

    val ids: List<String> = list.map { it.id }

    /**
     * What a send no variant fits is told: a finding sits on one property, so one that combines
     * several goes on the first and names them all.
     */
    private val notPlanned = (if (by.size == 1) "Not a planned variant" else "Not a planned variant of ${by.joinToString(" + ")}") +
        " · expects " + list.map { variant -> by.joinToString(" + ") { variant.match.getValue(it).displayText() } }.joinAsChoice()

    /**
     * [common], what the event's own schema found in [values], plus what the variant they were sent
     * as breaks. Only a discriminator the event's schema accepted picks one: what it rejected is
     * already reported, and so is any property it rejected.
     */
    fun check(values: CheckedValues, common: List<Finding>): PlanCheck {
        val rejected = common.mapTo(HashSet()) { it.path }
        if (by.any { it in rejected }) return PlanCheck(common, variant = null)
        by.firstOrNull { values.facts[it] is Fact.Hidden || values.facts[it] is Fact.Opaque }?.let { redacted ->
            return PlanCheck(common + Finding(redacted, "Redacted: can't tell which variant, only the common part was checked"), variant = null)
        }
        val variant = list.firstOrNull { it.fits(values) }
            ?: return PlanCheck(common + Finding(by.first(), notPlanned), variant = null)
        val own = variant.schema.check(values)
            .filter { it.path !in rejected }
            .map { it.copy(message = "For ${variant.id}: ${it.message}") }
        return PlanCheck(common + own, variant.id)
    }

    /** The id of the variant [values] were sent as, or null when none fits or the discriminator is redacted. */
    fun variantOf(values: CheckedValues): String? = list.firstOrNull { it.fits(values) }?.id

    // A redacted value, or one JSON has no type for, never equals a plan's; one exported before types
    // were is only its text.
    private fun Variant.fits(values: CheckedValues): Boolean = by.all { property ->
        val sent = values.json[property] ?: return@all false
        val planned = match.getValue(property)
        when (values.facts[property]) {
            null -> jsonEquals(planned, sent)
            Fact.Untyped -> planned.content == (sent as? JsonPrimitive)?.content
            Fact.Hidden, is Fact.Opaque, is Fact.Foreign -> false
        }
    }

    /** [match] holds the value of each property in `by`, as the plan's `when` sets them. */
    private class Variant(val id: String, val match: Map<String, JsonPrimitive>, val schema: Schema)

    companion object {
        private val KEYS = setOf("by", "list")
        private val NARROWING = setOf("const", "enum", "pattern")

        /** [variants] of the event [event], whose schema is [schema]. */
        fun parse(variants: JsonObject, schema: Schema, event: String): EventVariants {
            val path = "$event.variants"
            (variants.keys - KEYS).firstOrNull()?.let { invalid("$path holds only by and list, not $it") }
            val by = (variants["by"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.map { it.asString("$path.by") }
                ?: invalid("$path.by must be a non-empty array")
            by.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { invalid("$path.by lists $it twice") }
            fun requireProperty(property: String, at: String) =
                require(property in schema.properties) { "$at: $property is not a property of $event" }
            by.forEach { property ->
                requireProperty(property, "$path.by")
                require(property in schema.required) { "$path.by: $property must be required, so a variant can always be told" }
            }
            val list = (variants["list"] as? JsonArray)?.takeIf { it.isNotEmpty() }
                ?: invalid("$path.list must be a non-empty array")
            fun variant(element: JsonObject, at: String): Variant {
                val id = element["id"].asString("$at.id").ifEmpty { invalid("$at.id must not be empty") }
                val named = "$path.$id"
                val match = element["when"].asObject("$named.when")
                require(match.keys == by.toSet()) { "$named.when must set exactly ${by.joinToString(", ")}" }
                val properties = element["properties"]?.asObject("$named.properties").orEmpty()
                properties.forEach { (property, narrowing) ->
                    requireProperty(property, "$named.properties")
                    require(property !in by) { "$named.properties: $property tells variants apart, its when sets it" }
                    (narrowing.asObject("$named.properties.$property").keys - NARROWING).firstOrNull()?.let {
                        invalid("$named.properties.$property: a variant narrows a value with const, enum or pattern, not $it")
                    }
                }
                val required = element["required"]?.let { it as? JsonArray ?: invalid("$named.required must be an array") }
                    .orEmpty()
                    .map { it.asString("$named.required") }
                required.forEach { requireProperty(it, "$named.required") }
                return Variant(
                    id = id,
                    match = match.mapValues { (property, value) -> value as? JsonPrimitive ?: invalid("$named.when.$property must be a plain value") },
                    schema = Schema.parse(
                        buildJsonObject {
                            put("properties", JsonObject(properties))
                            put("required", JsonArray(required.map(::JsonPrimitive)))
                        },
                        named,
                    ),
                )
            }

            val parsed = mutableListOf<Variant>()
            list.forEachIndexed { index, element ->
                val variant = variant(element.asObject("$path.list[$index]"), "$path.list[$index]")
                require(parsed.none { it.id == variant.id }) { "$path: ${variant.id} is defined twice" }
                parsed.firstOrNull { other -> by.all { jsonEquals(other.match.getValue(it), variant.match.getValue(it)) } }
                    ?.let { invalid("$path: ${it.id} and ${variant.id} have the same when") }
                parsed += variant
            }
            return EventVariants(by, parsed)
        }
    }
}
