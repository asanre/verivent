package io.github.asanre.verivent.core.model

/**
 * A captured header or analytics property, with [redacted] flagging that [value] is a placeholder.
 *
 * [type] is an analytics property's JSON type (`string`, `integer`, `number`, `boolean`, `null`,
 * `object` or `array`, or the class name of a value JSON has none for), taken before redaction so a
 * redacted value keeps it. An `object` or `array` [value] is JSON text. Null for headers.
 */
data class KeyValue(
    val name: String,
    val value: String,
    val redacted: Boolean = false,
    val type: String? = null,
)
