package io.github.asanre.verivent.ui

import android.content.Context

/** No-op mirror of the Android entry points; nothing is shown in release builds. */
object InspectorLauncher {
    fun open(context: Context) = Unit
    fun showNotification(context: Context) = Unit
    fun dismissNotification(context: Context) = Unit
}
