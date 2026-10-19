package io.github.asanre.verivent.core.internal

/**
 * Run capture-side code swallowing every failure: debug tooling must never break the host app.
 * The failure still reaches [DiagnosticLog] under [tag], so a capture that silently goes missing can be
 * traced. Stays inline: callers pass suspending blocks.
 * Shared by the capture adapters (Ktor plugin, analytics sink); not API for hosts.
 */
inline fun runSafely(tag: String, block: () -> Unit) {
    try {
        block()
    } catch (cause: Throwable) {
        DiagnosticLog.warn(tag, "capture failed", cause)
    }
}
