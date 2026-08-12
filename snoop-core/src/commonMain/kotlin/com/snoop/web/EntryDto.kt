package com.snoop.web

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class KvDto(val name: String, val value: String, val redacted: Boolean)

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
)

/**
 * One SSE frame: [ids] is the full timeline in order, [entries] only the ones the client doesn't
 * have yet. The client rebuilds its list from [ids], which also prunes whatever got evicted.
 */
@Serializable
internal data class StreamFrameDto(val ids: List<String>, val entries: List<EntryDto>)

/** How the page must group the timeline, so it matches whatever the on-device UI shows. */
@Serializable
internal data class ConfigDto(val groupByEvent: String?, val groupLabelProperty: String?)

private fun KeyValue.toDto() = KvDto(name, value, redacted)

internal fun SnoopEntry.toDto(): EntryDto = when (this) {
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
    )
}

private val webJson = Json { encodeDefaults = false }

internal fun encodeEntries(entries: List<SnoopEntry>): String =
    webJson.encodeToString(ListSerializer(EntryDto.serializer()), entries.map { it.toDto() })

internal fun encodeFrame(ids: List<String>, changed: List<SnoopEntry>): String =
    webJson.encodeToString(StreamFrameDto.serializer(), StreamFrameDto(ids, changed.map { it.toDto() }))

// Its own Json: `webJson` drops defaults, and a null here has to reach the page as an explicit null
// (grouping off) rather than as a missing key the page would have to guess about.
private val configJson = Json { explicitNulls = true }

internal fun encodeConfig(groupByEvent: String?, groupLabelProperty: String?): String =
    configJson.encodeToString(ConfigDto.serializer(), ConfigDto(groupByEvent, groupLabelProperty))
