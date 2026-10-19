package io.github.asanre.verivent.core.capture

import io.github.asanre.verivent.core.model.BodyPreview
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import kotlinx.io.Buffer
import kotlinx.io.write

/**
 * Turns captured bytes into the text preview the viewers show, shared by every capture adapter —
 * the Ktor plugin and the native iOS bridge alike, so a truncated body reads the same on both.
 *
 * Content types cross as `String?` rather than Ktor's [ContentType] on purpose: `verivent-native` gets
 * `verivent` as `implementation`, so Ktor is not on its compile classpath, and the no-op mirror
 * would have to grow a Ktor dependency it exists to avoid. Parsing happens here, where Ktor is
 * already available.
 */
object BodyCapture {

    /** Whether a body of this content type is worth capturing as text (vs a binary placeholder). */
    fun isTextLike(contentType: String?): Boolean {
        val parsed = parse(contentType) ?: return false
        return when (parsed.contentType) {
            "text" -> parsed.contentSubtype != "event-stream"
            "application" ->
                parsed.contentSubtype in TEXT_APPLICATION_SUBTYPES ||
                    parsed.contentSubtype.endsWith("+json") || parsed.contentSubtype.endsWith("+xml")
            else -> false
        }
    }

    /**
     * Text preview of [bytes], decoded with the charset the message declared — a Latin-1 response
     * used to come out of the store full of replacement characters.
     *
     * UTF-8 (and no charset at all, the sane default) keeps the fast path with its multi-byte-safe
     * cut. Anything else goes through Ktor's decoder, which offers no equivalent notion of a safe
     * boundary, so those previews are cut on the byte. A charset name the platform can't honour —
     * Kotlin/Native supports only a handful — falls back to UTF-8 rather than losing the body.
     *
     * [byteCount] is the size of the *whole* body, which may exceed [bytes] when the caller only
     * kept a prefix; it is what the viewer reports next to a truncation notice.
     */
    fun textPreview(
        bytes: ByteArray,
        contentType: String?,
        maxLength: Long,
        byteCount: Long = bytes.size.toLong(),
    ): BodyPreview {
        val truncated = bytes.size > maxLength
        val charset = runCatching { parse(contentType)?.charset() }.getOrNull()
        if (charset == null || charset == Charsets.UTF_8) {
            val end = if (truncated) bytes.utf8SnappedEnd(maxLength.toInt()) else bytes.size
            return BodyPreview(bytes.decodeToString(0, end), byteCount, truncated)
        }
        val end = if (truncated) maxLength.toInt().coerceAtMost(bytes.size) else bytes.size
        return BodyPreview(decodeWithCharset(bytes, end, truncated, charset), byteCount, truncated)
    }

    /** Stand-in for a body we deliberately didn't read, so the viewer still shows it was there. */
    fun binaryPlaceholder(contentType: String?, byteCount: Long): BodyPreview =
        BodyPreview("(binary body: ${contentType ?: "unknown"} — not captured)", byteCount, false)

    /**
     * A content type the peer sent malformed parses to null and counts as binary. Ktor hands the
     * plugin an already-parsed type; URLSession hands the bridge whatever the server wrote, so this
     * guard only ever fires on the iOS path.
     */
    private fun parse(contentType: String?): ContentType? =
        contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
}

private val TEXT_APPLICATION_SUBTYPES = setOf("json", "xml", "javascript", "x-www-form-urlencoded")

/**
 * Largest cut at or below [limit] that doesn't land inside a multi-byte UTF-8 sequence, so a
 * truncated body never ends in a replacement char the payload never had.
 */
private fun ByteArray.utf8SnappedEnd(limit: Int): Int {
    var end = limit.coerceIn(0, size)
    while (end in 1 until size && (this[end].toInt() and 0xC0) == 0x80) end--
    return end
}

/**
 * A truncating byte-offset cut can split a code unit, and Darwin's decoder throws on malformed
 * input where the JVM substitutes — trim up to a unit's worth of bytes to keep the decoded prefix
 * before giving up on the charset entirely.
 */
private fun decodeWithCharset(bytes: ByteArray, end: Int, truncated: Boolean, charset: Charset): String {
    val floor = if (truncated) (end - 3).coerceAtLeast(0) else end
    return (end downTo floor).firstNotNullOfOrNull { cut ->
        runCatching { charset.newDecoder().decode(Buffer().apply { write(bytes, 0, cut) }) }.getOrNull()
    } ?: bytes.decodeToString(0, end)
}
