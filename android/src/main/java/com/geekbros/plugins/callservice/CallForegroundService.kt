package com.geekbros.plugins.callservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.Nullable

class CallForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 10001
        const val CHANNEL_ID = "call_channel"
        const val CHANNEL_NAME = "Active Call Continuity"

        const val ACTION_START = "com.geekbros.plugins.callservice.ACTION_START"
        const val ACTION_UPDATE = "com.geekbros.plugins.callservice.ACTION_UPDATE"
        const val ACTION_STOP = "com.geekbros.plugins.callservice.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_PARTNER_NAME = "extra_partner_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_IS_MUTED = "extra_is_muted"
        const val EXTRA_IS_SPEAKER_ON = "extra_is_speaker_on"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val extras = intent.extras ?: Bundle()
                val title = extras.getString(EXTRA_TITLE, "Minglo Call")
                val body = extras.getString(EXTRA_BODY, "Active call")
                val partnerName = extras.getString(EXTRA_PARTNER_NAME, "Minglo User")
                val callType = extras.getString(EXTRA_CALL_TYPE, "voice")
                val isMuted = extras.getBoolean(EXTRA_IS_MUTED, false)
                val isSpeakerOn = extras.getBoolean(EXTRA_IS_SPEAKER_ON, false)

                createNotificationChannel()
                val notification = buildCallNotification(title, body, partnerName, callType, isMuted, isSpeakerOn)

                if (intent.action == ACTION_UPDATE) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                        } else {
                            0
                        }
                        if (fgsType != 0) {
                            startForeground(NOTIFICATION_ID, notification, fgsType)
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            var channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel == null) {
                channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps your Minglo video/voice call active when app is backgrounded"
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildCallNotification(
        title: String,
        body: String,
        partnerName: String,
        callType: String,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingIntentFlags)

        // Resolving notification small icon
        val iconResId = resources.getIdentifier("ic_stat_notify", "drawable", packageName).let {
            if (it != 0) it else applicationInfo.icon
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(iconResId)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setPriority(Notification.PRIORITY_LOW)
        }

        // Build Action Intents
        val hangupIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getBroadcast(this, 1, hangupIntent, pendingIntentFlags)

        val muteIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_MUTE
        }
        val mutePendingIntent = PendingIntent.getBroadcast(this, 2, muteIntent, pendingIntentFlags)

        val speakerIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_SPEAKER
        }
        val speakerPendingIntent = PendingIntent.getBroadcast(this, 3, speakerIntent, pendingIntentFlags)

        val muteTitle = if (isMuted) "Unmute" else "Mute"
        val speakerTitle = if (isSpeakerOn) "Earpiece" else "Speaker"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callerPerson = android.app.Person.Builder()
                .setName(partnerName)
                .build()

            val callStyle = Notification.CallStyle.forOngoingCall(callerPerson, hangupPendingIntent)
            if (callType == "video") {
                callStyle.setIsVideo(true)
            }

            val muteAction = Notification.Action.Builder(null, muteTitle, mutePendingIntent).build()
            val speakerAction = Notification.Action.Builder(null, speakerTitle, speakerPendingIntent).build()

            builder.addAction(muteAction)
            builder.addAction(speakerAction)

            builder.setStyle(callStyle)
        } else {
            // Fallback for API < 31
            val hangupAction = Notification.Action.Builder(null, "Hang Up", hangupPendingIntent).build()
            val muteAction = Notification.Action.Builder(null, muteTitle, mutePendingIntent).build()
            val speakerAction = Notification.Action.Builder(null, speakerTitle, speakerPendingIntent).build()

            builder.addAction(hangupAction)
            builder.addAction(muteAction)
            builder.addAction(speakerAction)
        }

        return builder.build()
    }
}
