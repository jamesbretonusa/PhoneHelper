package com.example.phonehelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

/**
 * প্রতিবার ফোন আনলক করলে (ACTION_USER_PRESENT) আজকের গণনা এক বাড়ায়।
 */
class PickupService : Service() {

    companion object {
        private const val CHANNEL_ID = "pickup_channel"
        private const val NOTIF_ID = 102
    }

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                Prefs.increment(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL_ID, "পিকআপ কাউন্টার", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("পিকআপ কাউন্টার চালু")
            .setContentText("ফোন আনলক করা গুনছি")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!registered) {
            val f = IntentFilter(Intent.ACTION_USER_PRESENT)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, f)
            }
            registered = true
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (registered) {
            try { unregisterReceiver(receiver) } catch (e: Exception) { }
            registered = false
        }
        super.onDestroy()
    }
}
