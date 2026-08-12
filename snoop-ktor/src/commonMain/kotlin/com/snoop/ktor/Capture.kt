package com.snoop.ktor

import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.redaction.RedactionEngine
import io.ktor.client.plugins.isSaved
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.discard
import io.ktor.utils.io.readRemaining
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write

internal fun BodyPreview.redactedBy(redaction: RedactionEngine): BodyPreview =
    copy(text = redaction.redactBody(text))

/**
 * Redact the `name=value&…` pairs of a form-urlencoded body, before the host's own body transforms
 * run: a `POST /oauth/token` carries `password` and `client_secret` exactly like a query string
 * would, and the query-parameter rules already know those names.
 */
internal fun BodyPreview.formRedactedBy(redaction: RedactionEngine, contentType: ContentType?): BodyPreview =
    if (contentType?.withoutParameters() == ContentType.Application.FormUrlEncoded) {
        copy(text = redaction.redactFormBody(text))
    } else {
        this
    }

private fun binaryPlaceholder(contentType: ContentType?, byteCount: Long): BodyPreview =
    BodyPreview("(binary body: ${contentType ?: "unknown"} — not captured)", byteCount, false)

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
 * Text preview of [bytes], decoded with the charset the message declared — a Latin-1 response used
 * to come out of the store full of replacement characters.
 *
 * UTF-8 (and no charset at all, the sane default) keeps the fast path with its multi-byte-safe cut.
 * Anything else goes through Ktor's decoder, which offers no equivalent notion of a safe boundary,
 * so those previews are cut on the byte. A charset name the platform can't honour — Kotlin/Native
 * supports only a handful — falls back to UTF-8 rather than losing the body.
 */
private fun textPreview(bytes: ByteArray, contentType: ContentType?, maxLen: Long, byteCount: Long): BodyPreview {
    val truncated = bytes.size > maxLen
    val charset = runCatching { contentType?.charset() }.getOrNull()
    if (charset == null || charset == Charsets.UTF_8) {
        val end = if (truncated) bytes.utf8SnappedEnd(maxLen.toInt()) else bytes.size
        return BodyPreview(bytes.decodeToString(0, end), byteCount, truncated)
    }
    val end = if (truncated) maxLen.toInt().coerceAtMost(bytes.size) else bytes.size
    return BodyPreview(decodeWithCharset(bytes, end, truncated, charset), byteCount, truncated)
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

internal fun captureRequestHeaders(
    request: HttpRequestBuilder,
    content: OutgoingContent,
    redaction: RedactionEngine,
): List<KeyValue> {
    val headers = request.headers.entries().flatMap { (name, values) ->
        values.map { redaction.redactHeader(name, it) }
    }
    val contentType = content.contentType?.toString()
    return if (contentType != null && request.headers[HttpHeaders.ContentType] == null) {
        headers + redaction.redactHeader(HttpHeaders.ContentType, contentType)
    } else {
        headers
    }
}

internal fun captureOutgoing(content: OutgoingContent, maxLen: Long): BodyPreview? = when (content) {
    is OutgoingContent.ByteArrayContent -> {
        val bytes = content.bytes()
        if (content.contentType.isTextLike()) {
            textPreview(bytes, content.contentType, maxLen, bytes.size.toLong())
        } else {
            binaryPlaceholder(content.contentType, bytes.size.toLong())
        }
    }
    is OutgoingContent.NoContent -> null
    // WriteChannelContent / ReadChannelContent / ProtocolUpgrade: reading would consume the stream.
    else -> null
}

/**
 * Ktor's default SaveBody plugin saves regular responses before `onResponse` runs, and each
 * `rawContent` access on a saved call replays a fresh channel — reading here can't affect the host.
 * Streaming paths (`prepareGet {}.execute {}`, `bodyAsChannel`, SSE) skip saving; reading those
 * would steal the host's bytes, so they get a placeholder instead. Binary bodies are skipped to
 * keep huge blobs out of the store.
 *
 * Failures propagate: the caller already runs inside `runSafely`.
 */
internal suspend fun readResponseBody(response: HttpResponse, maxLen: Long): BodyPreview? {
    val contentType = response.contentType()
    return when {
        !response.isSaved ->
            BodyPreview("(streaming body — not captured)", response.contentLength() ?: -1, false)
        !contentType.isTextLike() ->
            binaryPlaceholder(contentType, response.contentLength() ?: -1)
        else -> {
            val channel = response.bodyAsChannel()
            val head = channel.readRemaining(maxLen + 1).readByteArray()
            val remaining = channel.discard()
            textPreview(head, contentType, maxLen, head.size + remaining)
        }
    }
}
