package com.geekbros.plugins.callservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object IncomingCallNotificationManager {

    private const val TAG = "IncomingCallNotif"
    const val CHANNEL_ID = "incoming_calls_v2"
    const val MISSED_CALLS_CHANNEL_ID = "missed_calls"
    const val ACTION_OPEN_INCOMING_CALL = "com.geekbros.plugins.callservice.OPEN_INCOMING_CALL"
    const val ACTION_ACCEPT_CALL = "com.geekbros.plugins.callservice.ACCEPT_CALL"
    const val ACTION_OPEN_MISSED_CALL = "com.geekbros.plugins.callservice.OPEN_MISSED_CALL"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeAutoDismissRunnables = ConcurrentHashMap<String, Runnable>()

    /**
     * Creates or updates the dedicated high-priority Incoming Calls notification channel.
     * Notification sound is muted in this channel because RingtonePlayer natively plays
     * the looping ringtone to prevent double sound.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            // Delete legacy channels so previous ringtone sound settings do not echo
            try {
                nm?.deleteNotificationChannel("incoming_calls")
                nm?.deleteNotificationChannel("incoming_calls_channel")
            } catch (_: Throwable) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming voice and video calls"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Creates or updates the dedicated Missed Calls notification channel.
     */
    fun createMissedCallNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MISSED_CALLS_CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for missed voice and video calls"
                enableVibration(true)
                setShowBadge(true)
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Posts a native incoming call notification with CallStyle on API 31+ or action buttons on <31.
     */
    fun showIncomingCallNotification(
        context: Context,
        callId: String,
        callType: String,
        callerName: String,
        callerPhoto: String?,
        rejectToken: String,
        expiresAt: String?
    ) {
        createNotificationChannel(context)

        val notificationId = getNotificationId(callId)
        val mainActivityClass = getMainActivityClass(context)

        // 1. PendingIntent for body tap and full-screen intent -> IncomingCallActivity
        val incomingCallIntent = Intent(context, IncomingCallActivity::class.java).apply {
            action = ACTION_OPEN_INCOMING_CALL
            putExtra("action", "OPEN_INCOMING_CALL")
            putExtra("callId", callId)
            putExtra("callType", callType)
            putExtra("callerName", callerName)
            putExtra("callerPhoto", callerPhoto)
            putExtra("rejectToken", rejectToken)
            putExtra("expiresAt", expiresAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            incomingCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. PendingIntent for Accept button -> ACCEPT_CALL on MainActivity
        val acceptIntent = Intent(context, mainActivityClass).apply {
            action = ACTION_ACCEPT_CALL
            putExtra("action", "ACCEPT_CALL")
            putExtra("callId", callId)
            putExtra("callType", callType)
            putExtra("callerName", callerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. PendingIntent for Reject button -> CallActionReceiver (BroadcastReceiver)
        val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_REJECT_CALL
            putExtra("callId", callId)
            putExtra("rejectToken", rejectToken)
            putExtra("notificationId", notificationId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification content text
        val isVideo = callType.equals("video", ignoreCase = true)
        val titleText = callerName.ifBlank { "Minglo User" }
        val bodyText = if (isVideo) "Incoming video call" else "Incoming audio call"

        val smallIcon = getNotificationIcon(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(30000L)
            .setContentIntent(fullScreenPendingIntent)

        // Full-screen intent with Android 14 permission check
        val canUseFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val allowed = nm?.canUseFullScreenIntent() ?: true
            if (!allowed) {
                Log.w(TAG, "canUseFullScreenIntent is false on Android 14+. Falling back to heads-up notification.")
            }
            allowed
        } else {
            true
        }
        if (canUseFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        // Notification.CallStyle on API 31+ vs action buttons below 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callerPerson = Person.Builder()
                .setName(titleText)
                .setImportant(true)
                .build()

            val callStyle = NotificationCompat.CallStyle.forIncomingCall(
                callerPerson,
                rejectPendingIntent,
                acceptPendingIntent
            )
            builder.setStyle(callStyle)
        } else {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                rejectPendingIntent
            )
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "Accept",
                acceptPendingIntent
            )
        }

        try {
            val notification = builder.build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Incoming call notification posted for callId=$callId (id=$notificationId)")
        } catch (e: SecurityException) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to display incoming call notification: ${e.message}", e)
        }

        // Schedule auto-dismiss at expiresAt
        scheduleAutoDismiss(context, callId, notificationId, expiresAt)
    }

    /**
     * Cancels the notification and stops the ringtone for the specified call.
     */
    fun cancelIncomingCall(context: Context, callId: String, notificationId: Int = getNotificationId(callId)) {
        try {
            // Cancel any scheduled auto-dismiss runnable
            activeAutoDismissRunnables.remove(callId)?.let { runnable ->
                mainHandler.removeCallbacks(runnable)
            }

            NotificationManagerCompat.from(context).cancel(notificationId)
            RingtonePlayer.stop()
            IncomingCallActivity.dismiss(callId)
            Log.d(TAG, "Incoming call notification cancelled for callId=$callId (id=$notificationId)")
        } catch (e: Throwable) {
            Log.e(TAG, "Error cancelling notification for callId=$callId: ${e.message}", e)
        }
    }

    private fun scheduleAutoDismiss(context: Context, callId: String, notificationId: Int, expiresAt: String?) {
        // Cancel existing runnable if any
        activeAutoDismissRunnables.remove(callId)?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
        }

        var delayMs = 30000L // 30s default ringing timeout
        if (!expiresAt.isNullOrBlank()) {
            try {
                val expiresAtMs = if (expiresAt.contains("T")) {
                    Instant.parse(expiresAt).toEpochMilli()
                } else {
                    expiresAt.toLong()
                }
                val remainingMs = expiresAtMs - System.currentTimeMillis()
                if (remainingMs in 1000..60000) {
                    delayMs = remainingMs
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse expiresAt='$expiresAt': ${e.message}")
            }
        }

        val autoDismissRunnable = Runnable {
            Log.d(TAG, "Auto-dismiss timer expired for callId=$callId")
            cancelIncomingCall(context, callId, notificationId)
            RingtonePlayer.stop()
            IncomingCallActivity.dismiss(callId)
        }

        activeAutoDismissRunnables[callId] = autoDismissRunnable
        mainHandler.postDelayed(autoDismissRunnable, delayMs)
    }

    fun stopRingtone() {
        RingtonePlayer.stop()
    }

    fun getNotificationId(callId: String): Int {
        return kotlin.math.abs(callId.hashCode())
    }

    fun getMissedCallNotificationId(callId: String): Int {
        return kotlin.math.abs(callId.hashCode()) + 10000
    }

    /**
     * Posts a missed-call notification on the dedicated missed_calls channel.
     * Tapping opens the app and routes to the call log.
     */
    fun showMissedCallNotification(
        context: Context,
        callId: String,
        callType: String,
        callerName: String
    ) {
        createMissedCallNotificationChannel(context)

        val notificationId = getMissedCallNotificationId(callId)
        val mainActivityClass = getMainActivityClass(context)

        val openIntent = Intent(context, mainActivityClass).apply {
            action = ACTION_OPEN_MISSED_CALL
            putExtra("action", "OPEN_MISSED_CALL")
            putExtra("type", "missed_call")
            putExtra("callId", callId)
            putExtra("callType", callType)
            putExtra("callerName", callerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isVideo = callType.equals("video", ignoreCase = true)
        val titleText = "Missed call"
        val displayName = callerName.ifBlank { "User" }
        val bodyText = if (isVideo) "Missed video call from $displayName" else "Missed voice call from $displayName"

        val smallIcon = getNotificationIcon(context)

        val builder = NotificationCompat.Builder(context, MISSED_CALLS_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        try {
            val notification = builder.build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Missed call notification posted for callId=$callId (id=$notificationId)")
        } catch (e: SecurityException) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted for missed call: ${e.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to display missed call notification: ${e.message}", e)
        }
    }

    private fun getNotificationIcon(context: Context): Int {
        val res = context.resources
        val pkg = context.packageName
        val customIcon = res.getIdentifier("ic_stat_notify", "drawable", pkg)
        if (customIcon != 0) return customIcon
        return android.R.drawable.sym_call_incoming
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
