package io.github.asanre.verivent.ktor

import io.ktor.client.request.HttpRequestBuilder

/** No-op mirror of `verivent-ktor`'s config DSL: every hook is inert. */
class VeriventConfig {
    var maxContentLength: Long = 250_000
    fun sanitizeHeader(predicate: (String) -> Boolean) = Unit
    fun sanitizeQueryParameter(predicate: (String) -> Boolean) = Unit
    fun sanitizeBody(transform: (String) -> String) = Unit
    fun sanitizePath(transform: (String) -> String) = Unit
    fun filter(predicate: (HttpRequestBuilder) -> Boolean) = Unit
}
