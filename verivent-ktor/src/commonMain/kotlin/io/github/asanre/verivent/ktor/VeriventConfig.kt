package com.snoop.ktor

import io.ktor.client.request.HttpRequestBuilder

/**
 * DSL for `install(Snoop) { ... }`. Everything is a lambda so the host filters and redacts with
 * plain predicates. All lists are additive across multiple calls.
 */
class SnoopConfig {

    /** Bodies larger than this (in bytes/chars) are captured truncated with a "truncated" flag. */
    var maxContentLength: Long = 250_000

    internal val headerRedactors = mutableListOf<(String) -> Boolean>()
    internal val queryParamRedactors = mutableListOf<(String) -> Boolean>()
    internal val bodyTransforms = mutableListOf<(String) -> String>()
    internal val requestFilters = mutableListOf<(HttpRequestBuilder) -> Boolean>()

    /**
     * Redact matching headers with a visible placeholder. Matches on the header name.
     * `Authorization`, `Cookie` and `Set-Cookie` are always redacted on top of these.
     */
    fun sanitizeHeader(predicate: (String) -> Boolean) {
        headerRedactors += predicate
    }

    /**
     * Redact matching query parameter values in the captured URL. Matches on the parameter name.
     * Common secret-bearing parameters (`api_key`, `token`, ...) are always redacted on top of these.
     */
    fun sanitizeQueryParameter(predicate: (String) -> Boolean) {
        queryParamRedactors += predicate
    }

    /**
     * Rewrite captured body previews (request and response) before they are stored — e.g. blank out
     * a JSON field. Transforms apply in registration order, after truncation; only the stored
     * preview is affected, never the real traffic.
     */
    fun sanitizeBody(transform: (String) -> String) {
        bodyTransforms += transform
    }

    /** Keep only requests for which every filter returns true; a false skips capture entirely. */
    fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        requestFilters += predicate
    }
}
