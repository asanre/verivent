package com.snoop.core.internal

/** No-op mirror: same contract (never throws), without the diagnostics. */
inline fun runSafely(tag: String, block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
        // Swallowed by design.
    }
}
