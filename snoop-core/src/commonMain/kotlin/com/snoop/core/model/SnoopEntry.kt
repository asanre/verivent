package com.snoop.core.model

import kotlin.time.Instant

/** A single item shown in the unified Snoop timeline: either a network call or an analytics event. */
sealed interface SnoopEntry {
    val id: String
    val timestamp: Instant
}

/** A captured header or analytics property, with [redacted] flagging that [value] is a placeholder. */
data class KeyValue(
    val name: String,
    val value: String,
    val redacted: Boolean = false,
)

/** A captured request/response body, possibly truncated to the configured limit. */
data class BodyPreview(
    val text: String,
    val byteCount: Long,
    val truncated: Boolean,
) {
    val isEmpty: Boolean get() = byteCount == 0L
}

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

/** A semantic badge attached to an analytics event by the host's `annotate {}` rules. */
enum class Annotation { Ecommerce, Conversion, Consent, ScreenView }

data class AnalyticsEntry(
    override val id: String,
    override val timestamp: Instant,
    val name: String,
    /** Free-form vendor/channel label ("firebase", "gtm", ...); the UI groups and colors by it. */
    val channel: String,
    val properties: List<KeyValue> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    /** Set when this event was sent over a network call marked as analytics transport. */
    val correlatedCallId: String? = null,
) : SnoopEntry
