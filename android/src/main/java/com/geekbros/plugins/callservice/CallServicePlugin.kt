package com.geekbros.plugins.callservice

import android.content.Intent
import android.os.Handler
import android.os.Looper
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
        private var pendingStartCall: PluginCall? = null
        private val timeoutHandler = Handler(Looper.getMainLooper())
        private var timeoutRunnable: Runnable? = null

        fun onActionPressed(eventName: String) {
            staticBridge?.let { bridge ->
                val handle: PluginHandle? = bridge.getPlugin("CallService")
                if (handle != null) {
                    val instance = handle.instance as? CallServicePlugin
                    instance?.notifyListeners(eventName, JSObject())
                }
            }
        }

        fun onForegroundServiceStarted(success: Boolean, errorMsg: String? = null, grantedType: String = "none") {
            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            timeoutRunnable = null

            val call = pendingStartCall
            pendingStartCall = null

            call?.let {
                val ret = JSObject().apply {
                    put("started", success)
                    put("grantedType", grantedType)
                    if (!success) {
                        put("error", errorMsg ?: "Failed to start foreground service")
                    }
                }
                it.resolve(ret)
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
            pendingStartCall = call
            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            val timeout = Runnable {
                onForegroundServiceStarted(false, "Service start timed out", "none")
            }
            timeoutRunnable = timeout
            timeoutHandler.postDelayed(timeout, 5000)

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
                    timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
                    pendingStartCall = null
                    call.resolve(JSObject().apply {
                        put("started", false)
                        put("grantedType", "none")
                        put("error", e.message)
                    })
                    return
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            pendingStartCall = null
            call.resolve(JSObject().apply {
                put("started", false)
                put("grantedType", "none")
                put("error", e.message)
            })
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
