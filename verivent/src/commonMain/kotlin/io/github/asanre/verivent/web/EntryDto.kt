package io.github.asanre.verivent.web

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
    )
}

private val webJson = Json { encodeDefaults = false }

internal fun encodeEntries(entries: List<TimelineEntry>): String =
    webJson.encodeToString(ListSerializer(EntryDto.serializer()), entries.map { it.toDto() })

internal fun encodeEntry(entry: TimelineEntry): String = webJson.encodeToString(EntryDto.serializer(), entry.toDto())

/**
 * One entry as every export writes it: the web viewer's download, the app's share and the console
 * lines all carry this same JSON, so whatever reads one session file reads them all.
 */
@InternalVeriventApi
fun encodeExportEntry(entry: TimelineEntry): String = encodeEntry(entry)

internal fun encodeFrame(ids: List<String>, changed: List<TimelineEntry>): String =
    webJson.encodeToString(StreamFrameDto.serializer(), StreamFrameDto(ids, changed.map { it.toDto() }))
