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
    val requestHeaders: List<KvDto>? = null,
    val requestBody: String? = null,
    val responseHeaders: List<KvDto>? = null,
    val responseBody: String? = null,
    // analytics
    val name: String? = null,
    val channel: String? = null,
    val annotations: List<String>? = null,
    val correlatedCallId: String? = null,
    val properties: List<KvDto>? = null,
)

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
        requestHeaders = requestHeaders.map { it.toDto() },
        requestBody = requestBody?.text,
        responseHeaders = responseHeaders.map { it.toDto() },
        responseBody = responseBody?.text,
    )
    is AnalyticsEntry -> EntryDto(
        type = "analytics",
        id = id,
        ts = timestamp.toEpochMilliseconds(),
        name = name,
        channel = channel,
        annotations = annotations.map { it.label },
        correlatedCallId = correlatedCallId,
        properties = properties.map { it.toDto() },
    )
}

private val webJson = Json { encodeDefaults = false }

internal fun encodeEntries(entries: List<SnoopEntry>): String =
    webJson.encodeToString(ListSerializer(EntryDto.serializer()), entries.map { it.toDto() })
