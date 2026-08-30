package com.geekbros.plugins.callservice

import android.content.Intent
import android.util.Log
import com.getcapacitor.Bridge
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginHandle
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "CallService")
class CallServicePlugin : Plugin() {

    companion object {
        private var staticBridge: Bridge? = null

        fun onActionPressed(eventName: String) {
            staticBridge?.let { bridge ->
                val handle: PluginHandle? = bridge.getPlugin("CallService")
                if (handle != null) {
                    val instance = handle.instance as? CallServicePlugin
                    instance?.notifyListeners(eventName, JSObject())
                }
            }
        }
    }

    override fun load() {
        super.load()
        staticBridge = bridge
    }

    @PluginMethod
    fun startCallService(call: PluginCall) {
        val title = call.getString("title", "Minglo Call")
        val body = call.getString("body", "Active call")
        val partnerName = call.getString("partnerName", "Minglo User")
        val partnerPhoto = call.getString("partnerPhoto", null)
        val callType = call.getString("callType", "voice")
        val durationSeconds = call.getInt("durationSeconds", 0)
        val isMuted = call.getBoolean("isMuted", false)
        val isSpeakerOn = call.getBoolean("isSpeakerOn", false)

        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START
                putExtra(CallForegroundService.EXTRA_TITLE, title)
                putExtra(CallForegroundService.EXTRA_BODY, body)
                putExtra(CallForegroundService.EXTRA_PARTNER_NAME, partnerName)
                putExtra(CallForegroundService.EXTRA_PARTNER_PHOTO, partnerPhoto)
                putExtra(CallForegroundService.EXTRA_CALL_TYPE, callType)
                putExtra(CallForegroundService.EXTRA_DURATION, durationSeconds)
                putExtra(CallForegroundService.EXTRA_IS_MUTED, isMuted)
                putExtra(CallForegroundService.EXTRA_IS_SPEAKER_ON, isSpeakerOn)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    Log.w("CallServicePlugin", "startForegroundService failed: ${e.message}", e)
                    call.reject("Failed to start CallForegroundService: ${e.message}", e)
                    return
                }
            } else {
                context.startService(intent)
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to start CallForegroundService: ${e.message}", e)
        }
    }

    @PluginMethod
    fun updateCallService(call: PluginCall) {
        val title = call.getString("title", "Minglo Call")
        val body = call.getString("body", "Active call")
        val partnerName = call.getString("partnerName", "Minglo User")
        val partnerPhoto = call.getString("partnerPhoto", null)
        val callType = call.getString("callType", "voice")
        val durationSeconds = call.getInt("durationSeconds", 0)
        val isMuted = call.getBoolean("isMuted", false)
        val isSpeakerOn = call.getBoolean("isSpeakerOn", false)

        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_UPDATE
                putExtra(CallForegroundService.EXTRA_TITLE, title)
                putExtra(CallForegroundService.EXTRA_BODY, body)
                putExtra(CallForegroundService.EXTRA_PARTNER_NAME, partnerName)
                putExtra(CallForegroundService.EXTRA_PARTNER_PHOTO, partnerPhoto)
                putExtra(CallForegroundService.EXTRA_CALL_TYPE, callType)
                putExtra(CallForegroundService.EXTRA_DURATION, durationSeconds)
                putExtra(CallForegroundService.EXTRA_IS_MUTED, isMuted)
                putExtra(CallForegroundService.EXTRA_IS_SPEAKER_ON, isSpeakerOn)
            }
            context.startService(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to update CallForegroundService: ${e.message}", e)
        }
    }

    @PluginMethod
    fun stopCallService(call: PluginCall) {
        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_STOP
            }
            context.startService(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to stop CallForegroundService: ${e.message}", e)
        }
    }
}
