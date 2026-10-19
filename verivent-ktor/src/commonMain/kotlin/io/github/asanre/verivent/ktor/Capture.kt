package io.github.asanre.verivent.ktor

import io.github.asanre.verivent.core.capture.BodyCapture
import io.github.asanre.verivent.core.model.BodyPreview
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.redaction.RedactionEngine
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
        val contentType = content.contentType?.toString()
        if (BodyCapture.isTextLike(contentType)) {
            BodyCapture.textPreview(bytes, contentType, maxLen, bytes.size.toLong())
        } else {
            BodyCapture.binaryPlaceholder(contentType, bytes.size.toLong())
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
    val contentType = response.contentType()?.toString()
    return when {
        !response.isSaved ->
            BodyPreview("(streaming body — not captured)", response.contentLength() ?: -1, false)
        !BodyCapture.isTextLike(contentType) ->
            BodyCapture.binaryPlaceholder(contentType, response.contentLength() ?: -1)
        else -> {
            val channel = response.bodyAsChannel()
            val head = channel.readRemaining(maxLen + 1).readByteArray()
            val remaining = channel.discard()
            BodyCapture.textPreview(head, contentType, maxLen, head.size + remaining)
        }
    }
}
