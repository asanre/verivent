package io.github.asanre.verivent.core.internal

import platform.Foundation.NSLog

// Percent signs are doubled and the text passed as the format string itself: captured payloads
// routinely contain `%`, and NSLog would otherwise read arguments that were never pushed.
private fun log(message: String) = NSLog("Verivent " + message.replace("%", "%%"))

internal actual fun platformLogWarn(message: String, throwable: Throwable?) {
    val cause = throwable?.let { " — ${it::class.simpleName}: ${it.message}" }.orEmpty()
    log("$message$cause")
}

internal actual fun platformLogInfo(message: String) {
    log(message)
}
