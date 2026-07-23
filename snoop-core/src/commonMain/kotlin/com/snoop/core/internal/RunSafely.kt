package com.snoop.core.internal

/**
 * Run capture-side code swallowing every failure: debug tooling must never break the host app.
 * Shared by the capture adapters (Ktor plugin, analytics sink); not API for hosts.
 */
inline fun runSafely(block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
        // Swallowed by design.
    }
}
