@file:OptIn(ExperimentalNativeApi::class)

package com.snoop.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.snoop.core.internal.SnoopLog
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

private var presented: WeakReference<UIViewController>? = null

/**
 * Presents the inspector as a page sheet over whatever is on top of the foreground window — the
 * iOS counterpart of launching `SnoopActivity`. A no-op when the inspector is already on top.
 */
internal fun presentInspector() {
    val top = topViewController()
    if (top == null) {
        SnoopLog.warn("inspector", "no foreground window to present the inspector from")
        return
    }
    if (top === presented?.get()) return

    // A host whose own UI is SwiftUI may never have set CADisableMinimumFrameDurationOnPhone, and
    // the strict check would crash it the first time the inspector opens.
    val inspector = ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
        SnoopScreen(onClose = { presented?.get()?.dismissViewControllerAnimated(true, completion = null) })
    }
    inspector.modalPresentationStyle = UIModalPresentationPageSheet
    presented = WeakReference(inspector)
    top.presentViewController(inspector, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    // A tap on the notification while the app is in the background arrives before the scene is
    // active again, so a foreground-inactive scene has to count too.
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val window = (
        scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
            ?: scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundInactive }
        )
        ?.windows
        ?.filterIsInstance<UIWindow>()
        ?.let { windows -> windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
