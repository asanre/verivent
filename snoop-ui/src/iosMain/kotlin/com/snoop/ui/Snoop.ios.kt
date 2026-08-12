@file:OptIn(ExperimentalForeignApi::class)

package com.snoop.ui

import com.snoop.core.SnoopCore
import com.snoop.core.internal.SnoopLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.Foundation.NSError
import platform.Foundation.NSSelectorFromString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

private const val IDENTIFIER = "snoop_inspector"

/**
 * iOS entry points mirroring the Android [Snoop] object, minus the `Context`. [showNotification]
 * asks for alert permission inline (idempotent; a denial is a silent no-op), then keeps one
 * notification in sync with the store — each capture replaces the previous banner.
 */
object Snoop {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var delegate: NSObject? = null

    fun showNotification() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert) { granted, error: NSError? ->
            if (error != null) SnoopLog.warn(TAG, "notification authorization failed: ${error.localizedDescription}")
            if (granted) scope.launch { start(center) }
        }
    }

    fun dismissNotification() {
        job?.cancel()
        job = null
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(IDENTIFIER))
    }

    private fun start(center: UNUserNotificationCenter) {
        // Chain onto whatever delegate the host installed so foreground banners show without
        // swallowing the host's own notifications.
        if (delegate == null) {
            delegate = ForegroundBannerDelegate(center.delegate).also(center::setDelegate)
        }
        job?.cancel()
        job = scope.launch {
            SnoopCore.store.entries
                .map { notificationLines(it) }
                .distinctUntilChanged()
                .conflate()
                .collect { lines ->
                    publish(lines)
                    // Soft throttle: coalesce bursts to at most a few refreshes per second.
                    delay(300)
                }
        }
    }

    private fun publish(lines: List<String>) {
        val count = SnoopCore.store.entries.value.size
        val content = UNMutableNotificationContent().apply {
            setTitle("Snoop")
            setBody(
                if (lines.isEmpty()) {
                    "$count ${if (count == 1) "entry" else "entries"} captured — tap to inspect"
                } else {
                    lines.joinToString("\n")
                },
            )
        }
        // Fixed identifier: each request replaces the previously delivered notification.
        val request = UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error: NSError? ->
            if (error != null) SnoopLog.warn(TAG, "could not post the notification: ${error.localizedDescription}")
        }
    }

    private const val TAG = "notification"
}

private class ForegroundBannerDelegate(
    private val next: UNUserNotificationCenterDelegateProtocol?,
) : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        if (willPresentNotification.request.identifier == IDENTIFIER) {
            withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList)
            return
        }
        val forward = NSSelectorFromString("userNotificationCenter:willPresentNotification:withCompletionHandler:")
        if (next != null && next.respondsToSelector(forward)) {
            next.userNotificationCenter(center, willPresentNotification, withCompletionHandler)
        } else {
            withCompletionHandler(0uL)
        }
    }
}
