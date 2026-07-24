package com.snoop.core.model

/** A captured request/response body, possibly truncated to the configured limit. */
data class BodyPreview(
    val text: String,
    val byteCount: Long,
    val truncated: Boolean,
) {
    val isEmpty: Boolean get() = byteCount == 0L
}
