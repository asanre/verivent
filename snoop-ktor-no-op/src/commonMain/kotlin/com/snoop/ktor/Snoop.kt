package com.snoop.ktor

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder

/**
 * No-op mirror of `snoop-ktor` with the identical public API. Swap it in for release builds via
 * `releaseImplementation` so no traffic is captured and no bodies are retained.
 */
class SnoopConfig {
    var maxContentLength: Long = 250_000
    fun sanitizeHeader(predicate: (String) -> Boolean) = Unit
    fun sanitizeQueryParameter(predicate: (String) -> Boolean) = Unit
    fun sanitizeBody(transform: (String) -> String) = Unit
    fun filter(predicate: (HttpRequestBuilder) -> Boolean) = Unit
    fun markAsAnalyticsTransport(predicate: (HttpRequestBuilder) -> Boolean) = Unit
}

val Snoop = createClientPlugin("Snoop", ::SnoopConfig) { /* no-op */ }
