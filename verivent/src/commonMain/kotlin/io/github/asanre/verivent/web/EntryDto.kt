package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.TimelineEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** [type] is an analytics property's JSON type; headers have none, so it's left out. */
@Serializable
internal data class KvDto(val name: String, val value: String, val redacted: Boolean, val type: String? = null)

@Serializable
internal data class IssueDto(val property: String, val message: String)

/** A list, not an object, so the order survives: JS reorders integer-like object keys. */
@Serializable
internal data class SectionDto(val title: String, val properties: List<KvDto>)

@Serializable
internal data class EntryDto(
    val type: String,
    val id: String,
    val ts: Long,
    // network
    val method: String? = null,
    val url: String? = null,
    val host: String? = null,
    val path: String? = null,
    val status: Int? = null,
    val state: String? = null,
    val durationMillis: Long? = null,
    val error: String? = null,
    val protocol: String? = null,
    val requestContentType: String? = null,
    val responseContentType: String? = null,
    val requestHeaders: List<KvDto>? = null,
    val requestBody: String? = null,
    val requestBodyTruncated: Boolean? = null,
    val requestByteCount: Long? = null,
    val responseHeaders: List<KvDto>? = null,
    val responseBody: String? = null,
    val responseBodyTruncated: Boolean? = null,
    val responseByteCount: Long? = null,
    // analytics
    val name: String? = null,
    val channel: String? = null,
    val annotations: List<String>? = null,
    val properties: List<KvDto>? = null,
    val groupLabel: String? = null,
    val sections: List<SectionDto>? = null,
    val planVersion: String? = null,
    val issues: List<IssueDto>? = null,
    val variant: String? = null,
    // the entry that opens an export (type "source"), and every console line
    val source: SourceDto? = null,
)

/**
 * One SSE frame: [ids] is the full timeline in order, [entries] only the ones the client doesn't
 * have yet. The client rebuilds its list from [ids], which also prunes whatever got evicted.
 */
@Serializable
internal data class StreamFrameDto(val ids: List<String>, val entries: List<EntryDto>)

private fun KeyValue.toDto() = KvDto(name, value, redacted, type)

internal fun KvDto.toKeyValue() = KeyValue(name, value, redacted, type)

internal fun TimelineEntry.toDto(): EntryDto = when (this) {
    is NetworkTransaction -> EntryDto(
        type = "network",
        id = id,
        ts = timestamp.toEpochMilliseconds(),
        method = method,
        url = url,
        host = host,
        path = path,
        status = status,
        state = state.name.lowercase(),
        durationMillis = durationMillis,
        error = error,
        protocol = protocol,
        requestContentType = requestContentType,
        responseContentType = responseContentType,
        requestHeaders = requestHeaders.map { it.toDto() },
        requestBody = requestBody?.text,
        requestBodyTruncated = requestBody?.truncated,
        requestByteCount = requestBody?.byteCount,
        responseHeaders = responseHeaders.map { it.toDto() },
        responseBody = responseBody?.text,
        responseBodyTruncated = responseBody?.truncated,
        responseByteCount = responseBody?.byteCount,
    )
    is AnalyticsEntry -> EntryDto(
        type = "analytics",
        id = id,
        ts = timestamp.toEpochMilliseconds(),
        name = name,
        channel = channel,
        annotations = annotations.map { it.label },
        properties = properties.map { it.toDto() },
        groupLabel = groupLabel,
        sections = sections.takeIf { it.isNotEmpty() }
            ?.map { (title, section) -> SectionDto(title, section.map { it.toDto() }) },
        planVersion = planVersion,
        issues = issues.takeIf { it.isNotEmpty() }?.map { IssueDto(it.property, it.message) },
        variant = variant,
    )
}

private val webJson = Json { encodeDefaults = false }

internal fun encodeEntries(entries: List<TimelineEntry>): String =
    webJson.encodeToString(ListSerializer(EntryDto.serializer()), entries.map { it.toDto() })

/** A `logTo` console line: the event as exported, plus the [source][exportSource] a file states once. */
internal fun encodeConsoleLine(entry: AnalyticsEntry): String =
    webJson.encodeToString(EntryDto.serializer(), entry.toDto().copy(source = exportSource))

private val prettyWebJson = Json(webJson) { prettyPrint = true }

/**
 * One analytics event as every export writes it: [encodeExport] holds it, and a console line is it
 * plus the source, so whatever reads one session file reads them all. Exports carry only analytics
 * events; network calls stay in the live timeline. [pretty] indents it by four spaces for a file
 * people read.
 */
@InternalVeriventApi
fun encodeExportEntry(entry: AnalyticsEntry, pretty: Boolean = false): String =
    (if (pretty) prettyWebJson else webJson).encodeToString(EntryDto.serializer(), entry.toDto())

/** Must match the export's pretty-print indent, so joined entries line up as array elements. */
private const val ARRAY_INDENT = "    "

/**
 * A session file, as the inspector's Export and the web viewer's download write it: a JSON array
 * that opens with a `"type": "source"` entry naming the [platform and versions][SourceDto] once,
 * then [events], each as [encodeExportEntry] writes it. The opener carries an `id` and a `ts` (when
 * the file was written) because readers from 0.10.0 require both on every entry; they skip it as
 * they skip anything that isn't `"analytics"`.
 */
@InternalVeriventApi
fun encodeExport(events: List<AnalyticsEntry>): String {
    val opener = exportSource?.let { source ->
        prettyWebJson.encodeToString(
            EntryDto.serializer(),
            EntryDto(type = "source", id = "source", ts = Inspector.now().toEpochMilliseconds(), source = source),
        )
    }
    val entries = listOfNotNull(opener) + events.map { encodeExportEntry(it, pretty = true) }
    if (entries.isEmpty()) return "[]"
    return entries.joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.prependIndent(ARRAY_INDENT) }
}

internal fun encodeFrame(ids: List<String>, changed: List<TimelineEntry>): String =
    webJson.encodeToString(StreamFrameDto.serializer(), StreamFrameDto(ids, changed.map { it.toDto() }))
