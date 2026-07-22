package com.snoop.ktor

import com.snoop.core.SnoopCore
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.redaction.RedactionEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import kotlin.time.TimeSource

/**
 * Ktor client plugin that feeds every request/response into the shared [SnoopCore] store.
 * Install once: `install(Snoop) { ... }`. All capture is wrapped so the debug tooling can never
 * break a real request — any failure inside Snoop is swallowed.
 */
val Snoop = createClientPlugin("Snoop", ::SnoopConfig) {
    val config = pluginConfig
    val redaction = RedactionEngine(headerPredicates = config.headerRedactors)
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
                    url = url.toString(),
                    host = url.host,
                    path = url.encodedPath,
                    requestContentType = content.contentType?.toString(),
                    requestHeaders = captureRequestHeaders(request, content, redaction),
                    requestBody = captureOutgoing(content, maxLen),
                ),
            )
        }
    }

    onResponse { response ->
        val callId = response.call.attributes.getOrNull(callIdKey) ?: return@onResponse
        val body = readResponseBody(response, maxLen)
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

private inline fun runSafely(block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
        // Debug tooling must never break the host's request pipeline.
    }
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
        val truncated = bytes.size > maxLen
        val slice = if (truncated) bytes.copyOf(maxLen.toInt()) else bytes
        BodyPreview(slice.decodeToString(), bytes.size.toLong(), truncated)
    }
    is OutgoingContent.NoContent -> null
    // WriteChannelContent / ReadChannelContent / ProtocolUpgrade: reading would consume the stream.
    else -> null
}

private suspend fun readResponseBody(response: HttpResponse, maxLen: Long): BodyPreview? = try {
    val text = response.bodyAsText()
    val truncated = text.length > maxLen
    BodyPreview(
        text = if (truncated) text.substring(0, maxLen.toInt()) else text,
        byteCount = text.length.toLong(),
        truncated = truncated,
    )
} catch (_: Throwable) {
    null
}
