package com.snoop.ui.internal

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val exportJson = Json { prettyPrint = true }

/** Must match [exportJson]'s pretty-print indent, so joined entries line up as array elements. */
private const val ARRAY_INDENT = "    "

/**
 * Serialize the session to a JSON array for the "Export" action (share/copy), capped to roughly
 * [maxChars] and keeping the newest entries (the clipboard transaction has a hard binder limit
 * around 1MB). Returns the JSON and how many entries made it.
 */
internal fun entriesToJsonCapped(entries: List<SnoopEntry>, maxChars: Int = 400_000): Pair<String, Int> {
    val keptNewestFirst = mutableListOf<String>()
    var used = 0
    for (entry in entries.asReversed()) {
        val text = exportJson.encodeToString(JsonObject.serializer(), entry.toJson())
        if (keptNewestFirst.isNotEmpty() && used + text.length > maxChars) break
        keptNewestFirst += text
        used += text.length
    }
    if (keptNewestFirst.isEmpty()) return "[]" to 0
    val json = keptNewestFirst.asReversed()
        .joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.prependIndent(ARRAY_INDENT) }
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
        putHeaders("requestHeaders", requestHeaders)
        put("requestBody", requestBody?.text)
        putHeaders("responseHeaders", responseHeaders)
        put("responseBody", responseBody?.text)
    }
    is AnalyticsEntry -> buildJsonObject {
        put("type", "analytics")
        put("id", id)
        put("timestamp", timestamp.toString())
        put("name", name)
        put("channel", channel)
        put("annotations", annotations.joinToString(",") { it.label })
        put("properties", propertiesJsonObject(properties))
        if (sections.isNotEmpty()) {
            putJsonArray("sections") {
                sections.forEach { (title, section) ->
                    addJsonObject {
                        put("title", title)
                        put("properties", propertiesJsonObject(section))
                    }
                }
            }
        }
    }
}

/**
 * Headers as an ordered array of `{name, value}`. An object would fold repeats into one entry, and
 * three `Set-Cookie`s collapsing into a single line is exactly the thing you export a session to
 * look at.
 */
private fun JsonObjectBuilder.putHeaders(key: String, items: List<KeyValue>) {
    putJsonArray(key) {
        items.forEach { addJsonObject { put("name", it.name); put("value", it.value) } }
    }
}
