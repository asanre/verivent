package com.snoop.core.redaction

import com.snoop.core.internal.SnoopLog
import com.snoop.core.model.KeyValue

/**
 * Replaces sensitive header/property values with a visible [placeholder] so the reader can see that
 * a field exists without exposing its value, and runs the host's body transforms. Predicates are
 * offered the name as captured and, when it differs, its lowercase form — HTTP/2 sends header names
 * lowercase, so a rule written either way still matches. Defaults cover the usual auth headers and
 * query parameters; the host adds more via the config DSL. A throwing predicate counts as a match
 * (fail closed) — a broken host lambda must never leak a value.
 */
class RedactionEngine(
    private val headerPredicates: List<(String) -> Boolean> = emptyList(),
    private val propertyPredicates: List<(String) -> Boolean> = emptyList(),
    private val queryParamPredicates: List<(String) -> Boolean> = emptyList(),
    private val bodyTransforms: List<(String) -> String> = emptyList(),
    private val placeholder: String = DEFAULT_PLACEHOLDER,
) {

    fun redactHeader(name: String, value: String): KeyValue {
        val hit = DEFAULT_HEADERS.contains(name.lowercase()) || headerPredicates.anyFailClosed(name)
        return KeyValue(name, if (hit) placeholder else value, hit)
    }

    fun redactProperty(key: String, value: String): KeyValue {
        val hit = propertyPredicates.anyFailClosed(key)
        return KeyValue(key, if (hit) placeholder else value, hit)
    }

    /**
     * Redact sensitive query parameter values in [url]. Pure string transform — no URL parsing
     * dependency — so malformed URLs pass through untouched. The fragment is preserved.
     */
    fun redactUrl(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val afterQuery = url.substring(queryStart + 1)
        val fragmentStart = afterQuery.indexOf('#')
        val query = if (fragmentStart >= 0) afterQuery.substring(0, fragmentStart) else afterQuery
        val fragment = if (fragmentStart >= 0) afterQuery.substring(fragmentStart) else ""
        return url.substring(0, queryStart) + "?" + redactParams(query) + fragment
    }

    /**
     * Redact a `name=value&…` form body with the same rules [redactUrl] applies to a query string —
     * a `POST` of `password=…&client_secret=…` is exactly as sensitive as the same pair in a URL.
     * Pure string transform, so names are matched as sent: a percent-encoded name doesn't match.
     */
    fun redactFormBody(body: String): String = redactParams(body)

    private fun redactParams(params: String): String = params.split('&').joinToString("&") { param ->
        val eq = param.indexOf('=')
        if (eq < 0) return@joinToString param
        val name = param.substring(0, eq)
        val hit = DEFAULT_QUERY_PARAMS.contains(name.lowercase()) || queryParamPredicates.anyFailClosed(name)
        if (hit) "$name=$placeholder" else param
    }

    /**
     * Rewrite a captured body preview through the host's transforms, in registration order. A
     * throwing transform replaces the whole text instead of leaking the unsanitized original.
     */
    fun redactBody(text: String): String = bodyTransforms.fold(text) { acc, transform ->
        runCatching { transform(acc) }
            .onFailure { SnoopLog.warn(TAG, "body sanitizer threw; dropping the body", it) }
            .getOrDefault(BODY_FAILURE_PLACEHOLDER)
    }

    private fun List<(String) -> Boolean>.anyFailClosed(name: String): Boolean {
        val folded = name.lowercase()
        return any { predicate ->
            runCatching { predicate(name) || (folded != name && predicate(folded)) }
                .onFailure { SnoopLog.warn(TAG, "redaction predicate threw on '$name'; redacting", it) }
                .getOrDefault(true)
        }
    }

    companion object {
        private const val TAG = "redaction"
        private const val BODY_FAILURE_PLACEHOLDER = "(body sanitizer failed)"

        const val DEFAULT_PLACEHOLDER = "██"
        val DEFAULT_HEADERS = setOf("authorization", "cookie", "set-cookie")
        val DEFAULT_QUERY_PARAMS = setOf(
            "api_key", "apikey", "api_secret", "key", "token", "access_token", "id_token",
            "refresh_token", "auth", "secret", "password", "client_secret", "signature", "sig",
        )
    }
}
