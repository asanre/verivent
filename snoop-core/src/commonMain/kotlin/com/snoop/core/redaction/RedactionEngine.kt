package com.snoop.core.redaction

import com.snoop.core.model.KeyValue

/**
 * Replaces sensitive header/property values with a visible [placeholder] so the reader can see that
 * a field exists without exposing its value. Predicates match on the key name (case handled by the
 * caller's lambda). Defaults cover the usual auth headers; the host adds more via the config DSL.
 */
class RedactionEngine(
    private val headerPredicates: List<(String) -> Boolean> = emptyList(),
    private val propertyPredicates: List<(String) -> Boolean> = emptyList(),
    private val placeholder: String = DEFAULT_PLACEHOLDER,
) {

    fun redactHeader(name: String, value: String): KeyValue {
        val hit = DEFAULT_HEADERS.contains(name.lowercase()) || headerPredicates.any { it(name) }
        return KeyValue(name, if (hit) placeholder else value, hit)
    }

    fun redactProperty(key: String, value: String): KeyValue {
        val hit = propertyPredicates.any { it(key) }
        return KeyValue(key, if (hit) placeholder else value, hit)
    }

    companion object {
        const val DEFAULT_PLACEHOLDER = "██"
        val DEFAULT_HEADERS = setOf("authorization", "cookie", "set-cookie")
    }
}
