package com.geekbros.plugins.callservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import com.capacitorjs.plugins.pushnotifications.PushNotificationsPlugin
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.time.Instant

class CallMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CallMessagingService"
    }

    override fun onNewToken(@NonNull token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken received in CallMessagingService")
        try {
            // Forward new FCM token to Capacitor PushNotificationsPlugin
            PushNotificationsPlugin.onNewToken(token)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delegate token to PushNotificationsPlugin: ${e.message}", e)
        }
    }

    override fun onMessageReceived(@NonNull remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val type = data["type"]
        val callId = data["callId"] ?: "unknown"

        Log.d(TAG, "FCM message received: type=$type, messageId=${remoteMessage.messageId}")
        Log.d("CALLDBG", "FCM onMessageReceived: type=$type, callId=$callId, messageId=${remoteMessage.messageId}")

        when (type) {
            "incoming_call" -> {
                handleIncomingCall(remoteMessage)
            }
            "call_cancelled" -> {
                handleCallCancelled(remoteMessage)
            }
            "missed_call" -> {
                handleMissedCall(remoteMessage)
            }
            "auto_offline" -> {
                handleAutoOffline(remoteMessage)
            }
            else -> {
                // Delegate every other push (chat, gifts, admin broadcasts, etc.) to Capacitor
                delegateToCapacitorPush(remoteMessage)
            }
        }
    }

    private fun handleAutoOffline(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        try {
            Log.d(TAG, "auto_offline push received. Stopping HostAvailabilityService.")
            stopService(Intent(this, HostAvailabilityService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop HostAvailabilityService on auto_offline push: ${e.message}", e)
        }

        // Suppress notification if app is in foreground (socket event shows toast)
        if (CallServicePlugin.isAppForeground(this)) {
            Log.d(TAG, "App is in foreground. Suppressing auto_offline system notification.")
            return
        }

        try {
            val title = data["title"] ?: "Availability Paused"
            val body = data["body"] ?: "You missed 3 calls, so you were set offline."
            val channelId = "host_status_channel"

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Host Status Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when host availability status changes"
                    setShowBadge(true)
                }
                notificationManager?.createNotificationChannel(channel)
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    20003,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val iconRes = resources.getIdentifier("ic_stat_notify", "drawable", packageName).let {
                if (it != 0) it else applicationInfo.icon
            }

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            if (contentIntent != null) {
                builder.setContentIntent(contentIntent)
            }

            notificationManager?.notify(20003, builder.build())
            Log.d(TAG, "Posted auto_offline local notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post auto_offline notification: ${e.message}", e)
        }
    }

    private fun handleIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"]
        val isForeground = CallServicePlugin.isAppForeground(this)

        Log.d("CALLDBG", "handleIncomingCall entry: callId=$callId, callType=${data["callType"]}, foreground=$isForeground")
        if (callId.isNullOrBlank()) {
            Log.w(TAG, "incoming_call push received without callId. Delegating to Capacitor.")
            delegateToCapacitorPush(remoteMessage)
            return
        }

        // Stale push guard: If expiresAt has already elapsed by more than 3s, drop it
        val expiresAt = data["expiresAt"]
        if (!expiresAt.isNullOrBlank()) {
            try {
                val expiresAtMs = if (expiresAt.contains("T")) {
                    Instant.parse(expiresAt).toEpochMilli()
                } else {
                    expiresAt.toLong()
                }
                val now = System.currentTimeMillis()
                if (now > expiresAtMs + 3000L) {
                    Log.w(TAG, "Dropping stale incoming_call push for callId=$callId: expired at $expiresAt (now=$now, skew margin=3000ms)")
                    return
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse expiresAt='$expiresAt': ${e.message}")
            }
        }

        // Suppress native notification when app is in foreground (Socket.IO in-app modal handles it)
        if (isForeground) {
            Log.d(TAG, "App is in foreground. Suppressing native notification for callId=$callId.")
            delegateToCapacitorPush(remoteMessage)
            return
        }

        val callType = data["callType"] ?: "voice"
        val callerName = data["callerName"] ?: data["userName"] ?: "Incoming Call"
        val callerPhoto = data["callerPhoto"] ?: data["userPhoto"]
        val rejectToken = data["rejectToken"] ?: ""

        Log.d(TAG, "Displaying native incoming call notification and launching IncomingCallActivity for callId=$callId")
        IncomingCallNotificationManager.showIncomingCallNotification(
            context = this,
            callId = callId,
            callType = callType,
            callerName = callerName,
            callerPhoto = callerPhoto,
            rejectToken = rejectToken,
            expiresAt = expiresAt
        )

        // Attempt direct launch of IncomingCallActivity over the lockscreen/app
        try {
            val activityIntent = Intent(this, IncomingCallActivity::class.java).apply {
                action = IncomingCallNotificationManager.ACTION_OPEN_INCOMING_CALL
                putExtra("action", "OPEN_INCOMING_CALL")
                putExtra("callId", callId)
                putExtra("callType", callType)
                putExtra("callerName", callerName)
                putExtra("callerPhoto", callerPhoto)
                putExtra("rejectToken", rejectToken)
                putExtra("expiresAt", expiresAt)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(activityIntent)
        } catch (e: Throwable) {
            Log.d(TAG, "Direct startActivity deferred to full-screen intent: ${e.message}")
        }
    }

    private fun handleMissedCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"] ?: "unknown"
        val callType = data["callType"] ?: "voice"
        val callerName = data["callerName"] ?: "Caller"
        Log.d("CALLDBG", "handleMissedCall entry: callId=$callId, callerName=$callerName, callType=$callType, foreground=${CallServicePlugin.isAppForeground(this)}")

        // 1. Cancel the ringing notification, stop ringtone, and dismiss IncomingCallActivity
        if (callId != "unknown") {
            IncomingCallNotificationManager.cancelIncomingCall(this, callId)
        }
        RingtonePlayer.stop()
        IncomingCallActivity.dismiss(if (callId != "unknown") callId else null)

        // 2. Forward to Capacitor push plugin
        delegateToCapacitorPush(remoteMessage)

        // 3. Skip posting system tray notification if app is actively in foreground
        if (CallServicePlugin.isAppForeground(this)) {
            Log.d(TAG, "App is in foreground. Suppressing missed_call system notification.")
            return
        }

        // 4. Post missed call notification
        IncomingCallNotificationManager.showMissedCallNotification(
            context = this,
            callId = callId,
            callType = callType,
            callerName = callerName
        )
    }

    private fun handleCallCancelled(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"]
        Log.d("CALLDBG", "handleCallCancelled entry: callId=$callId")
        if (!callId.isNullOrBlank()) {
            Log.d(TAG, "call_cancelled push received. Cancelling notification for callId=$callId")
            IncomingCallNotificationManager.cancelIncomingCall(this, callId)
            IncomingCallActivity.dismiss(callId)
        }
        RingtonePlayer.stop()
        // Always forward call_cancelled to Capacitor push plugin so JS listener can dismiss incoming modal
        delegateToCapacitorPush(remoteMessage)
    }

    private fun delegateToCapacitorPush(remoteMessage: RemoteMessage) {
        try {
            PushNotificationsPlugin.sendRemoteMessage(remoteMessage)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delegate push to PushNotificationsPlugin: ${e.message}", e)
        }
    }
}
