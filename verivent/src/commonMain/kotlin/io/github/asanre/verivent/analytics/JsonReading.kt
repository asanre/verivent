package io.github.asanre.verivent.analytics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** [json] as an object, or an [IllegalArgumentException] saying what [what] is and what's wrong. */
internal fun parseObject(json: String, what: String): JsonObject =
    runCatching { Json.parseToJsonElement(json) }
        .getOrElse { throw IllegalArgumentException("not valid JSON", it) }
        .asObject(what)

internal fun JsonElement?.asObject(what: String): JsonObject = this as? JsonObject ?: invalid("$what must be an object")

internal fun JsonElement?.asString(what: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("$what must be a string")

internal fun JsonElement?.asBoolean(what: String): Boolean =
    (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: invalid("$what must be true or false")

internal fun invalid(reason: String): Nothing = throw IllegalArgumentException(reason)
