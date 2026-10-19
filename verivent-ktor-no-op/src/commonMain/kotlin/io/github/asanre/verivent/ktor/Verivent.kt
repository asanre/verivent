package com.snoop.ktor

import io.ktor.client.plugins.api.createClientPlugin

/**
 * No-op mirror of `snoop-ktor` with the identical public API. Swap it in for release builds via
 * `releaseImplementation` so no traffic is captured and no bodies are retained.
 */
val Snoop = createClientPlugin("Snoop", ::SnoopConfig) { /* no-op */ }
