package com.snoop.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snoop.core.SnoopCore

/** Backs the notification's "Clear" action. */
class SnoopClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SnoopCore.store.clear()
        Snoop.showNotification(context)
    }
}
