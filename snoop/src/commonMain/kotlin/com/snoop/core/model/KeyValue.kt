package com.snoop.core.model

/** A captured header or analytics property, with [redacted] flagging that [value] is a placeholder. */
data class KeyValue(
    val name: String,
    val value: String,
    val redacted: Boolean = false,
)
