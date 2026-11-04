package com.geekbros.plugins.callservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class HostAvailabilityService : Service() {

    companion object {
        private const val TAG = "HostAvailabilityService"
        const val NOTIFICATION_ID = 20002

        const val CHANNEL_ID_DEFAULT = "host_availability_channel"
        const val CHANNEL_NAME_DEFAULT = "Host Availability"

        const val CHANNEL_ID_MINIMAL = "host_availability_minimal_channel"
        const val CHANNEL_NAME_MINIMAL = "Host Availability (Minimal)"

        const val ACTION_START = "com.geekbros.plugins.callservice.ACTION_START_AVAILABILITY"
        const val ACTION_UPDATE = "com.geekbros.plugins.callservice.ACTION_UPDATE_AVAILABILITY"
        const val ACTION_STOP = "com.geekbros.plugins.callservice.ACTION_STOP_AVAILABILITY"

        const val EXTRA_SHOW_DETAILED = "extra_show_detailed"

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (!isServiceRunning) {
                stopSelf()
                return START_NOT_STICKY
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_STOP -> {
                Log.d(TAG, "HostAvailabilityService ACTION_STOP received")
                isServiceRunning = false
                try {
                    stopForeground(true)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping foreground: ${e.message}", e)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val showDetailed = intent.getBooleanExtra(EXTRA_SHOW_DETAILED, true)
                Log.d(TAG, "HostAvailabilityService ${intent.action} showDetailed=$showDetailed")

                createNotificationChannels()
                val notification = buildNotification(showDetailed)

                if (intent.action == ACTION_START) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, notification, 0)
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                        isServiceRunning = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start HostAvailabilityService in foreground: ${e.message}", e)
                        isServiceRunning = false
                        stopSelf()
                        return START_NOT_STICKY
                    }
                } else {
                    if (isServiceRunning) {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.notify(NOTIFICATION_ID, notification)
                    }
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        try {
            stopForeground(true)
        } catch (_: Exception) {}
        Log.d(TAG, "HostAvailabilityService onDestroy")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel 1: Default (low importance, silent, ongoing)
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                CHANNEL_NAME_DEFAULT,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status notification while host call availability is turned ON"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(defaultChannel)

            // Channel 2: Minimal (min importance, silent, minimal presence)
            val minimalChannel = NotificationChannel(
                CHANNEL_ID_MINIMAL,
                CHANNEL_NAME_MINIMAL,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Minimal status indicator when host availability notification is minimized"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(minimalChannel)
        }
    }

    private fun buildNotification(showDetailed: Boolean): Notification {
        val channelId = if (showDetailed) CHANNEL_ID_DEFAULT else CHANNEL_ID_MINIMAL
        val titleText = if (showDetailed) "Minglo Host" else "Minglo"
        val bodyText = if (showDetailed) "You're online and ready to receive calls" else "Online"

        val smallIcon = getNotificationIcon(this)
        val mainActivityClass = getMainActivityClass(this)

        val openIntent = Intent(this, mainActivityClass).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            pendingIntentFlags
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setSound(null)
            .setPriority(if (showDetailed) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(if (showDetailed) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentPendingIntent)

        return builder.build()
    }

    private fun getNotificationIcon(context: Context): Int {
        val res = context.resources
        val pkg = context.packageName
        val customIcon = res.getIdentifier("ic_stat_notify", "drawable", pkg)
        if (customIcon != 0) return customIcon
        return android.R.drawable.stat_notify_chat
    }

    private fun getMainActivityClass(context: Context): Class<*> {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(context.packageName)
        if (launchIntent?.component != null) {
            try {
                return Class.forName(launchIntent.component!!.className)
            } catch (_: ClassNotFoundException) {}
        }
        return try {
            Class.forName("com.geekbros.minglo.MainActivity")
        } catch (_: ClassNotFoundException) {
            Class.forName("${context.packageName}.MainActivity")
        }
    }
}

