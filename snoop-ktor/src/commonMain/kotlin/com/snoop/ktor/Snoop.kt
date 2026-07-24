package com.snoop.ktor

import com.snoop.core.SnoopCore
import com.snoop.core.internal.runSafely
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.redaction.RedactionEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlin.time.TimeSource

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

    on(SendingRequest) { request, content ->
        runSafely {
            if (!SnoopCore.enabled) return@runSafely
            if (config.requestFilters.any { !it(request) }) return@runSafely

            val callId = SnoopCore.nextId()
            request.attributes.put(callIdKey, callId)
            request.attributes.put(startKey, TimeSource.Monotonic.markNow())

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
