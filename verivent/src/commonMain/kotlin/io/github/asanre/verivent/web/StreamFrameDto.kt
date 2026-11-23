package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.export.EntryDto
import io.github.asanre.verivent.export.toDto
import io.github.asanre.verivent.export.exportJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * One SSE frame: [ids] is the full timeline in order, [entries] only the ones the client doesn't
 * have yet. The client rebuilds its list from [ids], which also prunes whatever got evicted.
 */
@Serializable
internal data class StreamFrameDto(val ids: List<String>, val entries: List<EntryDto>)

internal fun encodeEntries(entries: List<TimelineEntry>): String =
    exportJson.encodeToString(ListSerializer(EntryDto.serializer()), entries.map { it.toDto() })

internal fun encodeFrame(ids: List<String>, changed: List<TimelineEntry>): String =
    exportJson.encodeToString(StreamFrameDto.serializer(), StreamFrameDto(ids, changed.map { it.toDto() }))
