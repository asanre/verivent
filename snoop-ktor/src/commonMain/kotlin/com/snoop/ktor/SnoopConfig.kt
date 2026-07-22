package com.snoop.ktor

import io.ktor.client.request.HttpRequestBuilder

/**
 * DSL for `install(Snoop) { ... }`. Kotlin-friendly like KtorMonitor: everything is a lambda so the
 * host filters and redacts with plain predicates. All lists are additive across multiple calls.
 */
class SnoopConfig {

    /** Bodies larger than this (in bytes/chars) are captured truncated with a "truncated" flag. */
    var maxContentLength: Long = 250_000

    internal val headerRedactors = mutableListOf<(String) -> Boolean>()
    internal val requestFilters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
    internal var analyticsTransport: ((HttpRequestBuilder) -> Boolean)? = null

    /**
     * Redact matching headers with a visible placeholder. Matches on the header name.
     * `Authorization`, `Cookie` and `Set-Cookie` are always redacted on top of these.
     */
    fun sanitizeHeader(predicate: (String) -> Boolean) {
        headerRedactors += predicate
    }

    /** Keep only requests for which every filter returns true; a false skips capture entirely. */
    fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        requestFilters += predicate
    }

    /**
     * Mark requests whose body carries analytics events (e.g. a GTM/collector endpoint). When such a
     * call completes, recent analytics events are correlated to it and shown in the event detail.
     */
    fun markAsAnalyticsTransport(predicate: (HttpRequestBuilder) -> Boolean) {
        analyticsTransport = predicate
    }
}
