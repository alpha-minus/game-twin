package com.twinlore.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Keeps the app's network while the overlay panel is up.
 *
 * Folding to the overlay moves the app's task behind the game, which leaves the
 * process with nothing but overlay windows: background, as far as network policy
 * is concerned. Doze, the restricted standby bucket and the battery optimizers
 * that drive them then cut its network, and the panel's refreshes spin until
 * they time out while its top bar reports no connection. A foreground service is
 * the priority all of those leave alone.
 *
 * It does nothing else. No wakelock, no thread, no polling: the cost is a
 * process priority and a quiet notification, for exactly as long as the panel's
 * window exists.
 */
class OverlayNetworkService : Service() {

    override fun onCreate() {
        super.onCreate()
        // Here rather than in onStartCommand. A panel shown and hidden in quick
        // succession can stop the service before its start command arrives, and
        // a foreground start that is promised and never made is a crash. onCreate
        // always runs first.
        createNotificationChannel()
        val notification = createNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            Log.w(TAG, "Could not keep the overlay in the foreground", it)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Overlay",
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Overlay is open")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "OverlayNetworkService"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 9002

        /**
         * Started while the panel's window is visible, which is what lets an app
         * holding the overlay permission start a foreground service from behind
         * the game. Refused anyway, the panel works as it did before, so the
         * failure is logged and nothing more.
         */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, OverlayNetworkService::class.java))
            }.onFailure { Log.w(TAG, "Could not start the overlay's network service", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OverlayNetworkService::class.java)) }
                .onFailure { Log.w(TAG, "Could not stop the overlay's network service", it) }
        }
    }
}
