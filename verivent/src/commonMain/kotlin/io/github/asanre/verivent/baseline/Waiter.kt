package io.github.asanre.verivent.baseline

import kotlin.time.Duration

/**
 * How a test waits for analytics to arrive, on its framework's clock. Compose's test clock only
 * moves while the test waits on it, so a Compose test passes its own:
 *
 * ```kotlin
 * fun ComposeUiTest.waiter() = Waiter { timeout, condition ->
 *     try {
 *         waitUntil(timeout.inWholeMilliseconds, condition)
 *         true
 *     } catch (_: ComposeTimeoutException) {
 *         false
 *     }
 * }
 * ```
 */
fun interface Waiter {

    /** Whether [condition] held before [timeout] ran out. */
    fun until(timeout: Duration, condition: () -> Boolean): Boolean

    companion object {
        /** Polls in real time: right for UI Automator and plain instrumentation, not under a virtual clock. */
        val RealTime: Waiter = Waiter(::pollInRealTime)
    }
}

internal expect fun pollInRealTime(timeout: Duration, condition: () -> Boolean): Boolean
