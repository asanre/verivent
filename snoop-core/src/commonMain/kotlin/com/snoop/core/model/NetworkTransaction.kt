package com.snoop.core.model

import kotlin.time.Instant

enum class NetworkState { Pending, Complete, Failed }

data class NetworkTransaction(
    override val id: String,
    override val timestamp: Instant,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val requestContentType: String? = null,
    val requestHeaders: List<KeyValue> = emptyList(),
    val requestBody: BodyPreview? = null,
    val status: Int? = null,
    val responseContentType: String? = null,
    val responseHeaders: List<KeyValue> = emptyList(),
    val responseBody: BodyPreview? = null,
    val protocol: String? = null,
    val durationMillis: Long? = null,
    val error: String? = null,
    /** Ids of analytics events that were sent over this call (see correlation). */
    val correlatedEventIds: List<String> = emptyList(),
) : SnoopEntry {
    val state: NetworkState
        get() = when {
            error != null -> NetworkState.Failed
            status == null -> NetworkState.Pending
            else -> NetworkState.Complete
        }
}
