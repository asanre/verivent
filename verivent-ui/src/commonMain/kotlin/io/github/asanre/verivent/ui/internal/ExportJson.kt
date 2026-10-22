package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.web.encodeExportEntry

/** Must match the export's pretty-print indent, so joined entries line up as array elements. */
private const val ARRAY_INDENT = "    "

/** The exported JSON, with how many of the session's [events] made it into it ([kept]). */
internal data class CappedExport(val json: String, val kept: Int, val events: Int)

/**
 * Serialize the session's analytics events to a JSON array for the "Export" action (share/copy),
 * capped to roughly [maxChars] and keeping the newest events (the clipboard transaction has a hard
 * binder limit around 1MB). Network calls stay in the timeline and aren't exported.
 *
 * Each event is the core's export JSON, the same the web viewer downloads and the console lines
 * carry, indented for whoever reads the shared file.
 */
@OptIn(InternalVeriventApi::class)
internal fun entriesToJsonCapped(entries: List<TimelineEntry>, maxChars: Int = 400_000): CappedExport {
    val events = entries.filterIsInstance<AnalyticsEntry>()
    val keptNewestFirst = mutableListOf<String>()
    var used = 0
    for (event in events.asReversed()) {
        val text = encodeExportEntry(event, pretty = true)
        if (keptNewestFirst.isNotEmpty() && used + text.length > maxChars) break
        keptNewestFirst += text
        used += text.length
    }
    if (keptNewestFirst.isEmpty()) return CappedExport("[]", 0, events.size)
    val json = keptNewestFirst.asReversed()
        .joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.prependIndent(ARRAY_INDENT) }
    return CappedExport(json, keptNewestFirst.size, events.size)
}
