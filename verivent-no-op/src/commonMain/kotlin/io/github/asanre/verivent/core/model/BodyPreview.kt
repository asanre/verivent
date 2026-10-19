package io.github.asanre.verivent.core.model

/** No-op mirror of a captured request/response body preview. */
data class BodyPreview(
    val text: String,
    val byteCount: Long,
    val truncated: Boolean,
) {
    val isEmpty: Boolean get() = byteCount == 0L
}
