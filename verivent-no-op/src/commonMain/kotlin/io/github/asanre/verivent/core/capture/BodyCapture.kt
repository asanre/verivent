package com.snoop.core.capture

import com.snoop.core.model.BodyPreview

/**
 * No-op mirror: nothing is ever captured, so nothing needs previewing. Kept String-shaped like the
 * real one so the mirror never has to depend on Ktor.
 */
object BodyCapture {

    fun isTextLike(contentType: String?): Boolean = false

    fun textPreview(
        bytes: ByteArray,
        contentType: String?,
        maxLength: Long,
        byteCount: Long = bytes.size.toLong(),
    ): BodyPreview = BodyPreview("", byteCount, false)

    fun binaryPlaceholder(contentType: String?, byteCount: Long): BodyPreview =
        BodyPreview("", byteCount, false)
}
