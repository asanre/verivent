package com.snoop.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snoop.core.SnoopCore

/**
 * Android entry points: open the inspector as its own Activity, or post a persistent notification
 * that reopens it while you navigate the host app.
 */
object Snoop {

    private const val CHANNEL_ID = "snoop_inspector"
    private const val NOTIFICATION_ID = 0x50_00

    fun open(context: Context) {
        context.startActivity(
            Intent(context, SnoopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Show/refresh the ongoing notification. Call again to update the captured-entry count. */
    fun showNotification(context: Context) {
        ensureChannel(context)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SnoopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags,
        )
        val clear = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, SnoopClearReceiver::class.java),
            flags,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.snoop_ic_logo)
            .setContentTitle("Snoop")
            .setContentText("${SnoopCore.store.entries.value.size} entries captured — tap to inspect")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Clear", clear)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+); the host must request it.
        }
    }

    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Snoop inspector", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}

/** Backs the notification's "Clear" action. */
class SnoopClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SnoopCore.store.clear()
        Snoop.showNotification(context)
    }
}
