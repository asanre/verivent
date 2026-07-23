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
 */
val Snoop = createClientPlugin("Snoop", ::SnoopConfig) {
    val config = pluginConfig
    val redaction = RedactionEngine(
        headerPredicates = config.headerRedactors,
        queryParamPredicates = config.queryParamRedactors,
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
                    requestBody = captureOutgoing(content, maxLen)?.sanitized(config.bodyTransforms),
                ),
            )
        }
    }

    onResponse { response ->
        val callId = response.call.attributes.getOrNull(callIdKey) ?: return@onResponse
        val body = readResponseBody(response, maxLen)?.sanitized(config.bodyTransforms)
        runSafely {
            if (!SnoopCore.enabled) return@runSafely
            val duration = response.call.attributes.getOrNull(startKey)?.elapsedNow()?.inWholeMilliseconds
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
                    SnoopCore.updateNetwork(callId) {
                        it.copy(error = cause.message ?: cause::class.simpleName ?: "error")
                    }
                }
            }
            throw cause
        }
    }
}

private fun BodyPreview.sanitized(transforms: List<(String) -> String>): BodyPreview {
    if (transforms.isEmpty()) return this
    // A throwing transform replaces the preview instead of leaking the unsanitized text.
    val text = transforms.fold(text) { acc, transform ->
        runCatching { transform(acc) }.getOrDefault("(body sanitizer failed)")
    }
    return copy(text = text)
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
            val slice = if (truncated) bytes.copyOf(maxLen.toInt()) else bytes
            BodyPreview(slice.decodeToString(), bytes.size.toLong(), truncated)
        } else {
            BodyPreview("(binary body: ${content.contentType ?: "unknown"} — not captured)", bytes.size.toLong(), false)
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
 */
private suspend fun readResponseBody(response: HttpResponse, maxLen: Long): BodyPreview? = try {
    val contentType = response.contentType()
    when {
        !response.isSaved ->
            BodyPreview("(streaming body — not captured)", response.contentLength() ?: -1, false)
        !contentType.isTextLike() ->
            BodyPreview("(binary body: ${contentType ?: "unknown"} — not captured)", response.contentLength() ?: -1, false)
        else -> {
            val channel = response.bodyAsChannel()
            val head = channel.readRemaining(maxLen + 1).readByteArray()
            val remaining = channel.discard()
            val truncated = head.size > maxLen
            val slice = if (truncated) head.copyOf(maxLen.toInt()) else head
            BodyPreview(slice.decodeToString(), head.size + remaining, truncated)
        }
    }
} catch (_: Throwable) {
    null
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
