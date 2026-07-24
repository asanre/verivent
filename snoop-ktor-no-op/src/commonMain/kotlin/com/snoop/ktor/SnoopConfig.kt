package com.snoop.ktor

import io.ktor.client.request.HttpRequestBuilder

/** No-op mirror of `snoop-ktor`'s config DSL: every hook is inert. */
class SnoopConfig {
    var maxContentLength: Long = 250_000
    fun sanitizeHeader(predicate: (String) -> Boolean) = Unit
    fun sanitizeQueryParameter(predicate: (String) -> Boolean) = Unit
    fun sanitizeBody(transform: (String) -> String) = Unit
    fun filter(predicate: (HttpRequestBuilder) -> Boolean) = Unit
    fun markAsAnalyticsTransport(predicate: (HttpRequestBuilder) -> Boolean) = Unit
}
