package io.github.asanre.verivent.core.internal

/** No-op mirror: the release build stays mute. */
object DiagnosticLog {

    fun warn(tag: String, message: String, throwable: Throwable? = null) = Unit

    fun info(tag: String, message: String) = Unit
}
