package io.github.asanre.verivent.ktor

import io.ktor.client.plugins.api.createClientPlugin

/**
 * No-op mirror of `verivent-ktor` with the identical public API. Swap it in for release builds via
 * `releaseImplementation` so no traffic is captured and no bodies are retained.
 */
val Verivent = createClientPlugin("Verivent", ::VeriventConfig) { /* no-op */ }
