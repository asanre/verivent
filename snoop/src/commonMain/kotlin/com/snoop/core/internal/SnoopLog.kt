package com.snoop.core.internal

/**
 * Snoop's own diagnostics channel (Logcat on Android, NSLog on iOS). Not API for hosts.
 *
 * Only failures of Snoop's own code are reported here — never an error raised by the host or by a
 * third-party framework, which the host already sees through its own channels.
 */
object SnoopLog {

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        platformLogWarn("[$tag] $message", throwable)
    }

    fun info(tag: String, message: String) {
        platformLogInfo("[$tag] $message")
    }
}

internal expect fun platformLogWarn(message: String, throwable: Throwable?)

internal expect fun platformLogInfo(message: String)
