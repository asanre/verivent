package com.snoop.ktor

import com.snoop.core.SnoopCore
import com.snoop.core.internal.runSafely
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.redaction.RedactionEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.isSaved
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.discard
import io.ktor.utils.io.readRemaining
import kotlin.time.TimeSource
import kotlinx.io.readByteArray

/**
 * Ktor client plugin that feeds every request/response into the shared [SnoopCore] store.
 * Install once: `install(Snoop) { ... }`. All capture is wrapped so the debug tooling can never
 * break a real request — any failure inside Snoop is swallowed.
 *
 * With `HttpRequestRetry` installed, each physical attempt is timed and shown as its own entry:
 * Ktor hands every attempt a fresh attribute set, so there is no key to fold them together on.
 * Attempts retried on a status code carry that status; ones retried on an exception the retry
 * plugin swallows stay pending.
 */
val Snoop = createClientPlugin("Snoop", ::SnoopConfig) {
    val config = pluginConfig
    val redaction = RedactionEngine(
        headerPredicates = config.headerRedactors,
        queryParamPredicates = config.queryParamRedactors,
        bodyTransforms = config.bodyTransforms,
    )
    val maxLen = config.maxContentLength

    val callIdKey = AttributeKey<String>("SnoopCallId")
    val startKey = AttributeKey<TimeSource.Monotonic.ValueTimeMark>("SnoopStart")
    val transportKey = AttributeKey<Boolean>("SnoopTransport")

    on(SendingRequest) { request, content ->
        runSafely {
            if (!SnoopCore.enabled) return@runSafely
            if (config.requestFilters.any { !it(request) }) return@runSafely

            val callId = SnoopCore.nextId()
            request.attributes.put(callIdKey, callId)
            request.attributes.put(startKey, TimeSource.Monotonic.markNow())
            config.analyticsTransport?.let { request.attributes.put(transportKey, it(request)) }

            val url = request.url.build()
            SnoopCore.recordNetwork(
                NetworkTransaction(
                    id = callId,
                    timestamp = SnoopCore.now(),
                    method = request.method.value,
                    url = redaction.redactUrl(url.toString()),
                    host = url.host,
                    path = url.encodedPath,
                    requestContentType = content.contentType?.toString(),
                    requestHeaders = captureRequestHeaders(request, content, redaction),
                    requestBody = captureOutgoing(content, maxLen)?.redactedBy(redaction),
                ),
            )
        }
    }

    onResponse { response ->
        runSafely {
            if (!SnoopCore.enabled) return@runSafely
            val callId = response.call.attributes.getOrNull(callIdKey) ?: return@runSafely
            // Stop the clock before Snoop reads the body, or the host's call looks slower than it was.
            val duration = response.call.attributes.getOrNull(startKey)?.elapsedNow()?.inWholeMilliseconds
            val body = readResponseBody(response, maxLen)?.redactedBy(redaction)
            SnoopCore.updateNetwork(callId) {
                it.copy(
                    status = response.status.value,
                    protocol = response.version.toString(),
                    responseContentType = response.headers[HttpHeaders.ContentType],
                    responseHeaders = response.headers.entries().flatMap { (name, values) ->
                        values.map { redaction.redactHeader(name, it) }
                    },
                    responseBody = body,
                    durationMillis = duration,
                )
            }
            if (response.call.attributes.getOrNull(transportKey) == true) {
                SnoopCore.correlateRecentAnalytics(callId)
            }
        }
    }

    // onResponse never fires on a transport failure; catch it here to record the error.
    client.plugin(HttpSend).intercept { request ->
        try {
            execute(request)
        } catch (cause: Throwable) {
            val callId = request.attributes.getOrNull(callIdKey)
            if (callId != null) {
                runSafely {
                    // Ktor's timeout message embeds the full URL, query string and all, so it has
                    // to go through redaction like any other captured URL would.
                    val message = redaction.redactUrl(cause.message ?: cause::class.simpleName ?: "error")
                    SnoopCore.updateNetwork(callId) { it.copy(error = message) }
                }
            }
            throw cause
        }
    }
}

private fun BodyPreview.redactedBy(redaction: RedactionEngine): BodyPreview =
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

private fun captureRequestHeaders(
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

private fun captureOutgoing(content: OutgoingContent, maxLen: Long): BodyPreview? = when (content) {
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
private suspend fun readResponseBody(response: HttpResponse, maxLen: Long): BodyPreview? {
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

private fun ContentType?.isTextLike(): Boolean = when {
    this == null -> false
    contentType == "text" -> contentSubtype != "event-stream"
    contentType == "application" ->
        contentSubtype in TEXT_APPLICATION_SUBTYPES ||
            contentSubtype.endsWith("+json") || contentSubtype.endsWith("+xml")
    else -> false
}

private val TEXT_APPLICATION_SUBTYPES = setOf("json", "xml", "javascript", "x-www-form-urlencoded")
