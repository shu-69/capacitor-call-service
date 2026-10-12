package com.geekbros.plugins.callservice

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.getcapacitor.Bridge
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginHandle
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

@CapacitorPlugin(
    name = "CallService",
    permissions = [
        Permission(
            alias = "bluetooth",
            strings = [
                Manifest.permission.BLUETOOTH_CONNECT
            ]
        )
    ]
)
class CallServicePlugin : Plugin() {

    private lateinit var audioRouteManager: AudioRouteManager

    companion object {
        private var staticBridge: Bridge? = null
        private var pendingStartCall: PluginCall? = null
        private val timeoutHandler = Handler(Looper.getMainLooper())
        private var timeoutRunnable: Runnable? = null
        @Volatile
        private var resumedActivityCount: Int = 0
        private var pendingCallIntent: JSObject? = null

        fun isAppForeground(context: Context): Boolean {
            if (resumedActivityCount > 0) return true
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
                val processes = am.runningAppProcesses ?: return false
                for (p in processes) {
                    if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        p.processName == context.packageName) {
                        return true
                    }
                }
            } catch (_: Throwable) {}
            return false
        }

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
        audioRouteManager = AudioRouteManager(context)
        audioRouteManager.setOnOutputsChangedListener { resultData ->
            notifyListeners("audioOutputsChanged", formatOutputsResult(resultData))
        }

        try {
            activity.application?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(act: Activity) {
                    if (act.packageName == context.packageName) {
                        resumedActivityCount++
                    }
                }
                override fun onActivityPaused(act: Activity) {
                    if (act.packageName == context.packageName) {
                        resumedActivityCount = maxOf(0, resumedActivityCount - 1)
                    }
                }
                override fun onActivityCreated(act: Activity, b: Bundle?) {}
                override fun onActivityStarted(act: Activity) {}
                override fun onActivityStopped(act: Activity) {}
                override fun onActivitySaveInstanceState(act: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(act: Activity) {}
            })
            resumedActivityCount = 1
        } catch (e: Throwable) {
            Log.w("CallServicePlugin", "Error registering activity lifecycle callbacks: ${e.message}")
        }

        // Handle cold start intent
        activity?.intent?.let { intent ->
            handleIntent(intent)
        }
    }

    override fun handleOnNewIntent(intent: Intent) {
        super.handleOnNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val rawAction = intent.action
        val action = when (rawAction) {
            IncomingCallNotificationManager.ACTION_OPEN_INCOMING_CALL, "OPEN_INCOMING_CALL" -> "OPEN_INCOMING_CALL"
            IncomingCallNotificationManager.ACTION_ACCEPT_CALL, "ACCEPT_CALL" -> "ACCEPT_CALL"
            else -> intent.getStringExtra("action")?.takeIf { it == "OPEN_INCOMING_CALL" || it == "ACCEPT_CALL" }
        } ?: return

        val callId = intent.getStringExtra("callId") ?: return
        val callType = intent.getStringExtra("callType") ?: "voice"
        val callerName = intent.getStringExtra("callerName") ?: ""

        val payload = JSObject().apply {
            put("action", action)
            put("callId", callId)
            put("callType", callType)
            put("callerName", callerName)
        }

        pendingCallIntent = payload

        // Ensure activity wakes/shows over lockscreen when tapped or launched from call notification
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        } catch (_: Throwable) {}

        // Clear intent action/extras so it is not re-handled on orientation or config changes
        intent.action = null
        intent.removeExtra("action")
        intent.removeExtra("callId")

        notifyListeners("callIntent", payload, true)
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        if (staticBridge == bridge) {
            staticBridge = null
        }
        pendingStartCall = null
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
        if (::audioRouteManager.isInitialized) {
            audioRouteManager.stopAudioRouting()
        }
    }

    private fun formatOutputsResult(data: AudioOutputsResultData): JSObject {
        val ret = JSObject()
        val arr = JSArray()
        for (item in data.available) {
            arr.put(JSObject().apply {
                put("type", item.type)
                put("name", item.name)
            })
        }
        ret.put("available", arr)
        ret.put("active", data.active)
        ret.put("hasBluetoothPermission", data.hasBluetoothPermission)
        return ret
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
            if (::audioRouteManager.isInitialized) {
                audioRouteManager.stopAudioRouting()
            }
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_STOP
            }
            context.startService(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to stop CallForegroundService: ${e.message}", e)
        }
    }

    // --- Audio Routing Plugin Methods ---

    @PluginMethod
    fun getAudioOutputs(call: PluginCall) {
        val result = audioRouteManager.getOutputsResult()
        call.resolve(formatOutputsResult(result))
    }

    @PluginMethod
    fun setAudioOutput(call: PluginCall) {
        val type = call.getString("type")
        if (type == null) {
            call.reject("Audio output 'type' parameter is required")
            return
        }
        val success = audioRouteManager.setAudioOutput(type)
        if (success) {
            call.resolve()
        } else {
            call.reject("Failed to set audio output to '$type': not currently available")
        }
    }

    @PluginMethod
    fun startAudioRouting(call: PluginCall) {
        val callType = call.getString("callType", "voice") ?: "voice"
        val result = audioRouteManager.startAudioRouting(callType)
        call.resolve(formatOutputsResult(result))
    }

    @PluginMethod
    fun stopAudioRouting(call: PluginCall) {
        audioRouteManager.stopAudioRouting()
        call.resolve()
    }

    @PluginMethod
    fun checkBluetoothPermission(call: PluginCall) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioRouteManager.hasBluetoothPermission()
        } else {
            true
        }
        call.resolve(JSObject().apply { put("granted", granted) })
    }

    @PluginMethod
    fun openAppSettings(call: PluginCall) {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to open app settings: ${e.message}", e)
        }
    }

    @PluginMethod
    fun requestBluetoothPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (audioRouteManager.hasBluetoothPermission()) {
                call.resolve(JSObject().apply { put("granted", true) })
            } else {
                requestPermissionForAlias("bluetooth", call, "bluetoothPermCallback")
            }
        } else {
            call.resolve(JSObject().apply { put("granted", true) })
        }
    }

    @PermissionCallback
    private fun bluetoothPermCallback(call: PluginCall) {
        val granted = audioRouteManager.hasBluetoothPermission()
        call.resolve(JSObject().apply { put("granted", granted) })
        notifyListeners("audioOutputsChanged", formatOutputsResult(audioRouteManager.getOutputsResult()))
    }

    @PluginMethod
    fun getPendingCallIntent(call: PluginCall) {
        val ret = JSObject()
        if (pendingCallIntent != null) {
            ret.put("hasIntent", true)
            ret.put("intent", pendingCallIntent)
        } else {
            ret.put("hasIntent", false)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun clearPendingCallIntent(call: PluginCall) {
        pendingCallIntent = null
        call.resolve()
    }
}
