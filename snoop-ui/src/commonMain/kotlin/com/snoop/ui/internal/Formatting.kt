package com.snoop.ui.internal

import com.snoop.core.model.KeyValue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${oneDecimal(bytes / 1024.0)} KB"
    else -> "${oneDecimal(bytes / (1024.0 * 1024))} MB"
}

private fun oneDecimal(value: Double): String {
    val scaled = (value * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}

internal fun formatDuration(millis: Long): String =
    if (millis < 1000) "$millis ms" else "${oneDecimal(millis / 1000.0)} s"

/** Render analytics properties as a JSON object so the payload gets the same Pretty/Raw treatment. */
internal fun propertiesToJson(properties: List<KeyValue>): String =
    Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { properties.forEach { put(it.name, it.value) } },
    )
