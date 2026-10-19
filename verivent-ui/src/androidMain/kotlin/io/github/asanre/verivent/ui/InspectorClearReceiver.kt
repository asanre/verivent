package io.github.asanre.verivent.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.asanre.verivent.core.Inspector

/** Backs the notification's "Clear" action. */
class InspectorClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Inspector.store.clear()
        InspectorLauncher.showNotification(context)
    }
}
