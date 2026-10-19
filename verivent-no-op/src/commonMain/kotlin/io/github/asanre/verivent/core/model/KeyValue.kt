package io.github.asanre.verivent.core.model

/** No-op mirror of a captured header or analytics property. */
data class KeyValue(
    val name: String,
    val value: String,
    val redacted: Boolean = false,
)
