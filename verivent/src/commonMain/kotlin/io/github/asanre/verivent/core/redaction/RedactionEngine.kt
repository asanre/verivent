package io.github.asanre.verivent.core.redaction

import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.github.asanre.verivent.core.model.KeyValue

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
     * Redact sensitive parameter values in [url] — in the query string and in the fragment alike.
     * An OAuth implicit-flow redirect carries its token only in `#access_token=…`, often with no
     * query string at all. A fragment that isn't a `name=value` list (`#section`, `#/route`) has
     * nothing to match and passes through. Pure string transform — no URL parsing dependency — so
     * malformed URLs pass through untouched.
     */
    fun redactUrl(url: String): String {
        val fragmentStart = url.indexOf('#')
        val head = if (fragmentStart >= 0) url.substring(0, fragmentStart) else url
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart + 1) else null
        val queryStart = head.indexOf('?')
        val query = if (queryStart >= 0) head.substring(queryStart + 1) else null

        val safeQuery = query?.let(::redactParams)
        val safeFragment = fragment?.let(::redactParams)
        if (safeQuery === query && safeFragment === fragment) return url
        val path = if (queryStart >= 0) head.substring(0, queryStart) else head
        return path + safeQuery?.let { "?$it" }.orEmpty() + safeFragment?.let { "#$it" }.orEmpty()
    }

    /**
     * Redact a `name=value&…` form body with the same rules [redactUrl] applies to a query string —
     * a `POST` of `password=…&client_secret=…` is exactly as sensitive as the same pair in a URL.
     * Pure string transform, so names are matched as sent: a percent-encoded name doesn't match.
     */
    fun redactFormBody(body: String): String = redactParams(body)

    private fun redactParams(params: String): String {
        var redacted = false
        val safe = params.split('&').map { param ->
            val eq = param.indexOf('=')
            if (eq < 0) return@map param
            val name = param.substring(0, eq)
            val hit = DEFAULT_QUERY_PARAMS.contains(name.lowercase()) || queryParamPredicates.anyFailClosed(name)
            if (!hit) return@map param
            redacted = true
            "$name=$placeholder"
        }
        // Nothing matched is the common case — every request with a query runs through here, and
        // failed ones run through it twice. Hand back the original instead of a copy of it.
        return if (redacted) safe.joinToString("&") else params
    }

    /**
     * Rewrite a captured body preview through the host's transforms, in registration order. A
     * throwing transform replaces the whole text instead of leaking the unsanitized original.
     */
    fun redactBody(text: String): String = bodyTransforms.fold(text) { acc, transform ->
        runCatching { transform(acc) }
            .onFailure { DiagnosticLog.warn(TAG, "body sanitizer threw; dropping the body", it) }
            .getOrDefault(BODY_FAILURE_PLACEHOLDER)
    }

    private fun List<(String) -> Boolean>.anyFailClosed(name: String): Boolean {
        val folded = name.lowercase()
        return any { predicate ->
            runCatching { predicate(name) || (folded != name && predicate(folded)) }
                .onFailure { DiagnosticLog.warn(TAG, "redaction predicate threw on '$name'; redacting", it) }
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
