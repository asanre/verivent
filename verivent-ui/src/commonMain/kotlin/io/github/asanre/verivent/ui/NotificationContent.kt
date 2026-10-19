package io.github.asanre.verivent.ui

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkState
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.TimelineEntry

/** How many recent entries the live notification lists. */
internal const val NOTIFICATION_MAX_LINES = 5

/**
 * The most recent [max] entries, newest first, as short notification lines — shared by the Android
 * `InboxStyle` notification and the iOS notification body. Pure so both platforms and a test agree
 * on the exact text.
 */
internal fun notificationLines(entries: List<TimelineEntry>, max: Int = NOTIFICATION_MAX_LINES): List<String> =
    entries.asReversed().take(max).map { it.toNotificationLine() }

private fun TimelineEntry.toNotificationLine(): String = when (this) {
    is NetworkTransaction -> when (state) {
        NetworkState.Complete -> "$status $method $path"
        NetworkState.Pending -> "⏳ $method $path"
        NetworkState.Failed -> "❌ $method $path"
    }
    is AnalyticsEntry -> "● $name"
}
