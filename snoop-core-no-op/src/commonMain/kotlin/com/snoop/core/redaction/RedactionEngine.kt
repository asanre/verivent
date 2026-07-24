package com.snoop.core.redaction

import com.snoop.core.model.KeyValue

/** No-op mirror: nothing is ever captured, so nothing needs redacting — pure pass-through. */
class RedactionEngine(
    headerPredicates: List<(String) -> Boolean> = emptyList(),
    propertyPredicates: List<(String) -> Boolean> = emptyList(),
    queryParamPredicates: List<(String) -> Boolean> = emptyList(),
    bodyTransforms: List<(String) -> String> = emptyList(),
    placeholder: String = DEFAULT_PLACEHOLDER,
) {

    fun redactHeader(name: String, value: String): KeyValue = KeyValue(name, value)

    fun redactProperty(key: String, value: String): KeyValue = KeyValue(key, value)

    fun redactUrl(url: String): String = url

    fun redactBody(text: String): String = text

    companion object {
        const val DEFAULT_PLACEHOLDER = "██"

        // Empty in the no-op variant: nothing is captured, so nothing is redacted. Kept as symbols
        // for API parity; the real module owns the actual sensitive-name lists.
        val DEFAULT_HEADERS = emptySet<String>()
        val DEFAULT_QUERY_PARAMS = emptySet<String>()
    }
}
