package com.snoop.ktor

import io.ktor.http.ContentType

/** Whether a body of this content type is worth capturing as text (vs a binary/streaming placeholder). */
internal fun ContentType?.isTextLike(): Boolean = when {
    this == null -> false
    contentType == "text" -> contentSubtype != "event-stream"
    contentType == "application" ->
        contentSubtype in TEXT_APPLICATION_SUBTYPES ||
            contentSubtype.endsWith("+json") || contentSubtype.endsWith("+xml")
    else -> false
}

private val TEXT_APPLICATION_SUBTYPES = setOf("json", "xml", "javascript", "x-www-form-urlencoded")
