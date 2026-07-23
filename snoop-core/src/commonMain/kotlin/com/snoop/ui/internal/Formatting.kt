package com.snoop.ui.internal

import com.snoop.core.model.KeyValue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.round
import kotlin.time.Instant

private val prettyJson = Json { prettyPrint = true }

private fun Int.pad(length: Int) = toString().padStart(length, '0')

/** "14:32:07.912" in the device's local time — the timestamp shown on every timeline row. */
internal fun formatClock(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.pad(2)}:${dt.minute.pad(2)}:${dt.second.pad(2)}.${(dt.nanosecond / 1_000_000).pad(3)}"
}

/** Re-indent a JSON payload for the "Pretty" view; null if it isn't valid JSON (show Raw instead). */
internal fun prettyPrintJson(raw: String): String? = try {
    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
} catch (_: Throwable) {
    null
}

/** Collapse a JSON payload to one line for the "Raw" view; null if it isn't valid JSON. */
internal fun compactPrintJson(raw: String): String? = try {
    Json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
} catch (_: Throwable) {
    null
}

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

/** Sizes come off the wire, so an unknown length (-1) or a bad count must not render as garbage. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "—"
    bytes < KB -> "$bytes B"
    bytes < MB -> "${oneDecimal(bytes / KB.toDouble())} KB"
    bytes < GB -> "${oneDecimal(bytes / MB.toDouble())} MB"
    else -> "${oneDecimal(bytes / GB.toDouble())} GB"
}

private fun oneDecimal(value: Double): String {
    val scaled = round(value * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}

internal fun formatDuration(millis: Long): String = when {
    millis < 0 -> "—"
    millis < 1000 -> "$millis ms"
    else -> "${oneDecimal(millis / 1000.0)} s"
}

/** Render analytics properties as a JSON object so the payload gets the same Pretty/Raw treatment. */
internal fun propertiesToJson(properties: List<KeyValue>): String =
    Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { properties.forEach { put(it.name, it.value) } },
    )
