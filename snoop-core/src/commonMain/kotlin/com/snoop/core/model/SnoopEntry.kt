package com.snoop.core.model

import kotlin.time.Instant

/** A single item shown in the unified Snoop timeline: either a network call or an analytics event. */
sealed interface SnoopEntry {
    val id: String
    val timestamp: Instant
}

/** Fixed per-entry allowance for the fields the byte estimate doesn't walk (ids, timestamps, refs). */
internal const val ENTRY_OVERHEAD_BYTES = 256

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
