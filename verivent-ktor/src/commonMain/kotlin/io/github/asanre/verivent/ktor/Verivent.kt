package io.github.asanre.verivent.ktor

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.internal.runSafely
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.redaction.RedactionEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * Ktor client plugin that feeds every request/response into the shared [Inspector] store.
 * Install once: `install(Verivent) { ... }`. All capture is wrapped so the debug tooling can never
 * break a real request — any failure inside Verivent is swallowed.
 *
 * With `HttpRequestRetry` installed, each physical attempt is timed and shown as its own entry:
 * Ktor hands every attempt a fresh attribute set, so there is no key to fold them together on.
 * Attempts retried on a status code carry that status; ones retried on an exception the retry
 * plugin swallows stay pending.
 */
val Verivent = createClientPlugin("Verivent", ::VeriventConfig) {
    val config = pluginConfig
    val redaction = RedactionEngine(
        headerPredicates = config.headerRedactors,
        queryParamPredicates = config.queryParamRedactors,
        bodyTransforms = config.bodyTransforms,
    )
    val maxLen = config.maxContentLength

    val callIdKey = AttributeKey<String>("VeriventCallId")
    val startKey = AttributeKey<TimeSource.Monotonic.ValueTimeMark>("VeriventStart")

    // Only Verivent's own recording can be logged here — `cause` belongs to the host.
    fun recordFailure(callId: String, cause: Throwable) = runSafely("ktor.record-failure") {
        // Ktor's timeout message embeds the full URL, query string and all, so it has
        // to go through redaction like any other captured URL would.
        val message = redaction.redactUrl(cause.message ?: cause::class.simpleName ?: "error")
        Inspector.updateNetwork(callId) { it.copy(error = message) }
    }

    on(SendingRequest) { request, content ->
        runSafely("ktor.request") {
            if (!Inspector.enabled) return@runSafely
            if (config.requestFilters.any { !it(request) }) return@runSafely

            val callId = Inspector.nextId()
            request.attributes.put(callIdKey, callId)
            request.attributes.put(startKey, TimeSource.Monotonic.markNow())

            val url = request.url.build()
            Inspector.recordNetwork(
                NetworkTransaction(
                    id = callId,
                    timestamp = Inspector.now(),
                    method = request.method.value,
                    url = redaction.redactUrl(url.toString()),
                    host = url.host,
                    path = url.encodedPath,
                    requestContentType = content.contentType?.toString(),
                    requestHeaders = captureRequestHeaders(request, content, redaction),
                    requestBody = captureOutgoing(content, maxLen)
                        ?.formRedactedBy(redaction, content.contentType)
                        ?.redactedBy(redaction),
                ),
            )
        }
    }

    onResponse { response ->
        runSafely("ktor.response") {
            if (!Inspector.enabled) return@runSafely
            val callId = response.call.attributes.getOrNull(callIdKey) ?: return@runSafely
            // Stop the clock before Verivent reads the body, or the host's call looks slower than it was.
            val duration = response.call.attributes.getOrNull(startKey)?.elapsedNow()?.inWholeMilliseconds
            val body = try {
                readResponseBody(response, maxLen)
            } catch (cause: Throwable) {
                if (!cause.isHostFailure()) throw cause
                // The host's own call fails with this too: show it on the timeline, keep it out of the log.
                recordFailure(callId, cause)
                return@runSafely
            }?.formRedactedBy(redaction, response.contentType())
                ?.redactedBy(redaction)
            Inspector.updateNetwork(callId) {
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
            request.attributes.getOrNull(callIdKey)?.let { recordFailure(it, cause) }
            throw cause
        }
    }
}

/** A timeout or cancellation of the call itself: the host sees it through its own call, so it isn't Verivent's. */
private fun Throwable.isHostFailure(): Boolean = generateSequence(this) { it.cause }.any {
    it is CancellationException || it is HttpRequestTimeoutException || it is SocketTimeoutException
}
