package com.geekbros.plugins.callservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_HANGUP = "com.geekbros.plugins.callservice.ACTION_HANGUP"
        const val ACTION_MUTE = "com.geekbros.plugins.callservice.ACTION_MUTE"
        const val ACTION_SPEAKER = "com.geekbros.plugins.callservice.ACTION_SPEAKER"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_HANGUP -> CallServicePlugin.onActionPressed("hangup_pressed")
            ACTION_MUTE -> CallServicePlugin.onActionPressed("mute_pressed")
            ACTION_SPEAKER -> CallServicePlugin.onActionPressed("speaker_pressed")
        }
    }
}
