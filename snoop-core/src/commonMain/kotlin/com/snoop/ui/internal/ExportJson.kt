package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val exportJson = Json { prettyPrint = true }

/** Serialize the whole session to a JSON array for the "Export" action (share/copy). */
internal fun entriesToJson(entries: List<SnoopEntry>): String =
    exportJson.encodeToString(
        JsonArray.serializer(),
        buildJsonArray { entries.forEach { add(it.toJson()) } },
    )

/**
 * Like [entriesToJson] but capped to roughly [maxChars], keeping the newest entries (the clipboard
 * transaction has a hard binder limit around 1MB). Returns the JSON and how many entries made it.
 */
internal fun entriesToJsonCapped(entries: List<SnoopEntry>, maxChars: Int = 400_000): Pair<String, Int> {
    val keptNewestFirst = mutableListOf<JsonObject>()
    var used = 0
    for (entry in entries.asReversed()) {
        val obj = entry.toJson()
        val cost = exportJson.encodeToString(JsonObject.serializer(), obj).length
        if (keptNewestFirst.isNotEmpty() && used + cost > maxChars) break
        keptNewestFirst += obj
        used += cost
    }
    val json = exportJson.encodeToString(
        JsonArray.serializer(),
        buildJsonArray { keptNewestFirst.asReversed().forEach { add(it) } },
    )
    return json to keptNewestFirst.size
}

private fun SnoopEntry.toJson(): JsonObject = when (this) {
    is NetworkTransaction -> buildJsonObject {
        put("type", "network")
        put("id", id)
        put("timestamp", timestamp.toString())
        put("method", method)
        put("url", url)
        put("status", status)
        put("durationMillis", durationMillis)
        put("error", error)
        putJsonObject("requestHeaders") { headers(requestHeaders) }
        put("requestBody", requestBody?.text)
        putJsonObject("responseHeaders") { headers(responseHeaders) }
        put("responseBody", responseBody?.text)
    }
    is AnalyticsEntry -> buildJsonObject {
        put("type", "analytics")
        put("id", id)
        put("timestamp", timestamp.toString())
        put("name", name)
        put("channel", channel)
        put("correlatedCallId", correlatedCallId)
        put("annotations", annotations.joinToString(",") { it.label })
        putJsonObject("properties") { headers(properties) }
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.headers(items: List<KeyValue>) {
    items.forEach { put(it.name, it.value) }
}
