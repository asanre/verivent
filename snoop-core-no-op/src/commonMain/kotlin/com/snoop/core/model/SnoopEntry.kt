package com.snoop.core.model

import kotlin.time.Instant

/** No-op mirror. A single item in the unified Snoop timeline. */
sealed interface SnoopEntry {
    val id: String
    val timestamp: Instant
}

/** No-op mirror of a captured header or analytics property. */
data class KeyValue(
    val name: String,
    val value: String,
    val redacted: Boolean = false,
)

/** No-op mirror of a captured request/response body preview. */
data class BodyPreview(
    val text: String,
    val byteCount: Long,
    val truncated: Boolean,
) {
    val isEmpty: Boolean get() = byteCount == 0L
}
