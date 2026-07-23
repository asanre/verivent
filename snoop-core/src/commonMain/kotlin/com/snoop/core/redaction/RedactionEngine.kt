package com.snoop.core.redaction

import com.snoop.core.model.KeyValue

/**
 * Replaces sensitive header/property values with a visible [placeholder] so the reader can see that
 * a field exists without exposing its value. Predicates match on the key name (case handled by the
 * caller's lambda). Defaults cover the usual auth headers and query parameters; the host adds more
 * via the config DSL. A throwing predicate counts as a match (fail closed) — a broken host lambda
 * must never leak a value.
 */
class RedactionEngine(
    private val headerPredicates: List<(String) -> Boolean> = emptyList(),
    private val propertyPredicates: List<(String) -> Boolean> = emptyList(),
    private val queryParamPredicates: List<(String) -> Boolean> = emptyList(),
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
        val redacted = query.split('&').joinToString("&") { param ->
            val eq = param.indexOf('=')
            if (eq < 0) return@joinToString param
            val name = param.substring(0, eq)
            val hit = DEFAULT_QUERY_PARAMS.contains(name.lowercase()) || queryParamPredicates.anyFailClosed(name)
            if (hit) "$name=$placeholder" else param
        }
        return url.substring(0, queryStart) + "?" + redacted + fragment
    }

    private fun List<(String) -> Boolean>.anyFailClosed(name: String): Boolean =
        any { predicate -> runCatching { predicate(name) }.getOrDefault(true) }

    companion object {
        const val DEFAULT_PLACEHOLDER = "██"
        val DEFAULT_HEADERS = setOf("authorization", "cookie", "set-cookie")
        val DEFAULT_QUERY_PARAMS = setOf(
            "api_key", "apikey", "api_secret", "key", "token", "access_token", "id_token",
            "refresh_token", "auth", "secret", "password", "client_secret", "signature", "sig",
        )
    }
}
