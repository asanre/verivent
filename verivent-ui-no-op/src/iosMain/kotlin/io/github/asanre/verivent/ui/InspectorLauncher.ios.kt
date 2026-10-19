package io.github.asanre.verivent.ui

/** No-op mirror of the iOS entry points; nothing is shown in release builds. */
object InspectorLauncher {
    fun open() = Unit
    fun showNotification() = Unit
    fun dismissNotification() = Unit
}
