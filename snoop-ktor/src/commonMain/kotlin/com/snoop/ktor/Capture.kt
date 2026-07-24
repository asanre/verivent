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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.discard
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

internal fun BodyPreview.redactedBy(redaction: RedactionEngine): BodyPreview =
    copy(text = redaction.redactBody(text))

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
            val truncated = bytes.size > maxLen
            val end = if (truncated) bytes.utf8SnappedEnd(maxLen.toInt()) else bytes.size
            BodyPreview(bytes.decodeToString(0, end), bytes.size.toLong(), truncated)
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
            val truncated = head.size > maxLen
            val end = if (truncated) head.utf8SnappedEnd(maxLen.toInt()) else head.size
            BodyPreview(head.decodeToString(0, end), head.size + remaining, truncated)
        }
    }
}
