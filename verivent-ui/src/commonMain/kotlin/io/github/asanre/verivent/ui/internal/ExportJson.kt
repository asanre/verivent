package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.web.encodeExport
import io.github.asanre.verivent.web.encodeExportEntry

/** The exported JSON, with how many of the session's [events] made it into it ([kept]). */
internal data class CappedExport(val json: String, val kept: Int, val events: Int)

/**
 * Serialize the session's analytics events to a JSON array for the "Export" action (share/copy),
 * capped to roughly [maxChars] and keeping the newest events (the clipboard transaction has a hard
 * binder limit around 1MB). Network calls stay in the timeline and aren't exported.
 *
 * The file is the core's export, the same the web viewer downloads: the source first, then each
 * event, indented for whoever reads the shared file.
 */
@OptIn(InternalVeriventApi::class)
internal fun entriesToJsonCapped(entries: List<TimelineEntry>, maxChars: Int = 400_000): CappedExport {
    val events = entries.filterIsInstance<AnalyticsEntry>()
    val keptNewestFirst = mutableListOf<AnalyticsEntry>()
    var used = 0
    for (event in events.asReversed()) {
        val size = encodeExportEntry(event, pretty = true).length
        if (keptNewestFirst.isNotEmpty() && used + size > maxChars) break
        keptNewestFirst += event
        used += size
    }
    if (keptNewestFirst.isEmpty()) return CappedExport("[]", 0, events.size)
    return CappedExport(encodeExport(keptNewestFirst.asReversed()), keptNewestFirst.size, events.size)
}
