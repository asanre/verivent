package com.snoop.ui

/** No-op mirror of the iOS entry points; nothing is shown in release builds. */
object Snoop {
    fun showNotification() = Unit
    fun dismissNotification() = Unit
}
