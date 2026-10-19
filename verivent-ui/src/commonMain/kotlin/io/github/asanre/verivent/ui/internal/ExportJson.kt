package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.web.encodeExportEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val exportJson = Json { prettyPrint = true }

/** Must match [exportJson]'s pretty-print indent, so joined entries line up as array elements. */
private const val ARRAY_INDENT = "    "

/**
 * Serialize the session to a JSON array for the "Export" action (share/copy), capped to roughly
 * [maxChars] and keeping the newest entries (the clipboard transaction has a hard binder limit
 * around 1MB). Returns the JSON and how many entries made it.
 *
 * Each entry is the core's export JSON, the same the web viewer downloads and the console lines
 * carry, indented for whoever reads the shared file.
 */
@OptIn(InternalVeriventApi::class)
internal fun entriesToJsonCapped(entries: List<TimelineEntry>, maxChars: Int = 400_000): Pair<String, Int> {
    val keptNewestFirst = mutableListOf<String>()
    var used = 0
    for (entry in entries.asReversed()) {
        val text = exportJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(encodeExportEntry(entry)))
        if (keptNewestFirst.isNotEmpty() && used + text.length > maxChars) break
        keptNewestFirst += text
        used += text.length
    }
    if (keptNewestFirst.isEmpty()) return "[]" to 0
    val json = keptNewestFirst.asReversed()
        .joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.prependIndent(ARRAY_INDENT) }
    return json to keptNewestFirst.size
}
