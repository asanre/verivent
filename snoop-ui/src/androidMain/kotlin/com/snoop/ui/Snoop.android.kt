package com.snoop.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snoop.ui.R
import com.snoop.core.SnoopCore
import com.snoop.core.internal.SnoopLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Android entry points: open the inspector as its own Activity, or post a live notification that
 * lists the latest captured entries and reopens the inspector while you navigate the host app.
 */
object Snoop {

    private const val CHANNEL_ID = "snoop_inspector"
    private const val NOTIFICATION_ID = 0x50_00

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Guarded by the two @Synchronized entry points below: the "Clear" action posts from a binder
    // thread while the host shows or dismisses from its own, and a lost update leaks the collector
    // that re-posts a notification the user just dismissed.
    private var job: Job? = null

    fun open(context: Context) {
        context.startActivity(
            Intent(context, SnoopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Post the ongoing notification and keep it in sync with the store — each new capture refreshes
     * the listed lines. Idempotent: calling it again restarts the collection.
     */
    @Synchronized
    fun showNotification(context: Context) {
        ensureChannel(context)
        // The collection outlives the caller, so hold the application context, never an Activity.
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            SnoopCore.store.entries
                .map { notificationLines(it) }
                .distinctUntilChanged()
                .conflate()
                .collect { lines ->
                    notify(app, lines)
                    // Soft throttle: coalesce bursts to at most a few refreshes per second.
                    delay(300)
                }
        }
    }

    @Synchronized
    fun dismissNotification(context: Context) {
        job?.cancel()
        job = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission") // notify() is guarded by the SecurityException catch below.
    private fun notify(context: Context, lines: List<String>) {
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
        val count = SnoopCore.store.entries.value.size
        val style = NotificationCompat.InboxStyle()
        lines.forEach(style::addLine)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.snoop_ic_logo)
            .setContentTitle(SnoopCore.title)
            .setContentText("$count ${if (count == 1) "entry" else "entries"} captured — tap to inspect")
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Clear", clear)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (cause: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+); the host must request it.
            SnoopLog.warn("notification", "POST_NOTIFICATIONS not granted; nothing posted", cause)
        }
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
