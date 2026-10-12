package com.geekbros.plugins.callservice

import android.util.Log
import androidx.annotation.NonNull
import com.capacitorjs.plugins.pushnotifications.PushNotificationsPlugin
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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

        Log.d(TAG, "FCM message received: type=$type, messageId=${remoteMessage.messageId}")

        when (type) {
            "incoming_call" -> {
                handleIncomingCall(remoteMessage)
            }
            "call_cancelled" -> {
                handleCallCancelled(remoteMessage)
            }
            else -> {
                // Delegate every other push (chat, gifts, admin broadcasts, etc.) to Capacitor
                delegateToCapacitorPush(remoteMessage)
            }
        }
    }

    private fun handleIncomingCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"]
        if (callId.isNullOrBlank()) {
            Log.w(TAG, "incoming_call push received without callId. Delegating to Capacitor.")
            delegateToCapacitorPush(remoteMessage)
            return
        }

        // Suppress native notification when app is in foreground (Socket.IO modal handles it)
        if (CallServicePlugin.isAppForeground(this)) {
            Log.d(TAG, "App is in foreground. Suppressing native notification for callId=$callId.")
            delegateToCapacitorPush(remoteMessage)
            return
        }

        val callType = data["callType"] ?: "voice"
        val callerName = data["callerName"] ?: data["userName"] ?: "Incoming Call"
        val callerPhoto = data["callerPhoto"] ?: data["userPhoto"]
        val rejectToken = data["rejectToken"] ?: ""
        val expiresAt = data["expiresAt"]

        Log.d(TAG, "Displaying native CallStyle incoming call notification for callId=$callId")
        IncomingCallNotificationManager.showIncomingCallNotification(
            context = this,
            callId = callId,
            callType = callType,
            callerName = callerName,
            callerPhoto = callerPhoto,
            rejectToken = rejectToken,
            expiresAt = expiresAt
        )
    }

    private fun handleCallCancelled(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"]
        if (!callId.isNullOrBlank()) {
            Log.d(TAG, "call_cancelled push received. Cancelling notification for callId=$callId")
            IncomingCallNotificationManager.cancelIncomingCall(this, callId)
        }
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
