package io.github.asanre.verivent.core.internal

/**
 * Verivent's own diagnostics channel (Logcat on Android, NSLog on iOS). Not API for hosts.
 *
 * Only failures of Verivent's own code are reported here — never an error raised by the host or by a
 * third-party framework, which the host already sees through its own channels.
 */
object DiagnosticLog {

    /** Sees every warning as well, so a test can tell one was given. */
    internal var onWarn: ((String) -> Unit)? = null

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        onWarn?.invoke("[$tag] $message")
        platformLogWarn("[$tag] $message", throwable)
    }

    fun info(tag: String, message: String) {
        platformLogInfo("[$tag] $message")
    }
}

internal expect fun platformLogWarn(message: String, throwable: Throwable?)

internal expect fun platformLogInfo(message: String)
