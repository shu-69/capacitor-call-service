package com.geekbros.plugins.callservice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

data class AudioOutputDeviceItem(val type: String, val name: String)

data class AudioOutputsResultData(
    val available: List<AudioOutputDeviceItem>,
    val active: String?,
    val hasBluetoothPermission: Boolean
)

class AudioRouteManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioRouteManager"

        const val TYPE_EARPIECE = "earpiece"
        const val TYPE_SPEAKER = "speaker"
        const val TYPE_WIRED_HEADSET = "wired_headset"
        const val TYPE_BLUETOOTH = "bluetooth"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRoutingStarted = false
    private var callType = "voice" // "voice" or "video"
    private var userSelectedRoute: String? = null
    private var currentActiveRoute: String? = null
    private var previousAvailableTypes = setOf<String>()

    private var audioFocusRequest: AudioFocusRequest? = null
    private var onOutputsChangedListener: ((AudioOutputsResultData) -> Unit)? = null

    // API 31+ Listeners
    private var communicationDeviceChangedListener: Any? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    // API < 31 Legacy State & Receivers
    private var isWiredHeadsetConnectedLegacy = false
    private var isBluetoothConnectedLegacy = false
    private var legacyReceiver: BroadcastReceiver? = null

    fun setOnOutputsChangedListener(listener: (AudioOutputsResultData) -> Unit) {
        onOutputsChangedListener = listener
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Inspects currently available audio output devices.
     */
    fun getAvailableOutputs(): List<AudioOutputDeviceItem> {
        val list = mutableListOf<AudioOutputDeviceItem>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val commDevices = audioManager.availableCommunicationDevices
                val hasBtPermission = hasBluetoothPermission()

                var hasEarpiece = false
                var hasSpeaker = false
                var hasWired = false
                var hasBt = false
                var btDeviceName = "Bluetooth"

                for (dev in commDevices) {
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> hasEarpiece = true
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> hasSpeaker = true
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_USB_DEVICE -> hasWired = true
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                            if (hasBtPermission) {
                                hasBt = true
                                val name = dev.productName?.toString()
                                if (!name.isNullOrBlank()) {
                                    btDeviceName = name
                                }
                            }
                        }
                    }
                }

                // If device has earpiece (phones)
                if (hasEarpiece) {
                    list.add(AudioOutputDeviceItem(TYPE_EARPIECE, "Phone Earpiece"))
                }
                // Speaker is always available on all Android devices
                if (hasSpeaker || list.isEmpty()) {
                    list.add(AudioOutputDeviceItem(TYPE_SPEAKER, "Speaker"))
                }
                // Wired headphones
                if (hasWired) {
                    list.add(AudioOutputDeviceItem(TYPE_WIRED_HEADSET, "Wired Headphones"))
                }
                // Bluetooth
                if (hasBt && hasBtPermission) {
                    list.add(AudioOutputDeviceItem(TYPE_BLUETOOTH, btDeviceName))
                }

                return list
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching availableCommunicationDevices (API 31+): ${e.message}", e)
            }
        }

        // Fallback for API < 31 or if API 31+ failed
        list.add(AudioOutputDeviceItem(TYPE_EARPIECE, "Phone Earpiece"))
        list.add(AudioOutputDeviceItem(TYPE_SPEAKER, "Speaker"))

        if (isWiredHeadsetConnectedLegacy || isWiredHeadsetPluggedInLegacy()) {
            list.add(AudioOutputDeviceItem(TYPE_WIRED_HEADSET, "Wired Headphones"))
        }

        if (hasBluetoothPermission() && (isBluetoothConnectedLegacy || isBluetoothHeadsetConnectedLegacy())) {
            list.add(AudioOutputDeviceItem(TYPE_BLUETOOTH, "Bluetooth"))
        }

        return list
    }

    /**
     * Returns full output state bundle (available list, active route, bt permission).
     */
    fun getOutputsResult(): AudioOutputsResultData {
        val available = getAvailableOutputs()
        val availableTypes = available.map { it.type }.toSet()

        // Validate active route is still among available
        val active = if (currentActiveRoute != null && availableTypes.contains(currentActiveRoute)) {
            currentActiveRoute
        } else {
            resolvePriorityRoute(available)
        }

        return AudioOutputsResultData(
            available = available,
            active = active,
            hasBluetoothPermission = hasBluetoothPermission()
        )
    }

    /**
     * Starts call audio routing in MODE_IN_COMMUNICATION with audio focus.
     */
    fun startAudioRouting(type: String = "voice"): AudioOutputsResultData {
        if (isRoutingStarted) {
            callType = type
            return getOutputsResult()
        }

        isRoutingStarted = true
        callType = type
        userSelectedRoute = null

        requestCallAudioFocus()
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set MODE_IN_COMMUNICATION: ${e.message}", e)
        }

        registerListeners()

        val available = getAvailableOutputs()
        previousAvailableTypes = available.map { it.type }.toSet()

        // Resolve default route based on priority
        val targetRoute = resolvePriorityRoute(available)
        applyRoute(targetRoute)

        val result = getOutputsResult()
        Log.i(TAG, "Audio routing started for callType=$callType. Active route=$targetRoute, available=${result.available.map { it.type }}")
        return result
    }

    /**
     * Stops call audio routing, resets audio focus and mode.
     */
    fun stopAudioRouting() {
        if (!isRoutingStarted) return

        Log.i(TAG, "Stopping audio routing and restoring system audio state")
        isRoutingStarted = false
        userSelectedRoute = null
        currentActiveRoute = null

        unregisterListeners()

        // Clear communication device (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing communication device: ${e.message}", e)
            }
        } else {
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = false
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping legacy sco/speakerphone: ${e.message}", e)
            }
        }

        abandonCallAudioFocus()

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore MODE_NORMAL: ${e.message}", e)
        }
    }

    /**
     * Manually picks audio output route.
     * Retained until the device disconnects or the call terminates.
     */
    fun setAudioOutput(targetType: String): Boolean {
        val available = getAvailableOutputs()
        val match = available.find { it.type == targetType }
        if (match == null) {
            Log.w(TAG, "Cannot set audio output to $targetType: not in available outputs")
            return false
        }

        userSelectedRoute = targetType
        applyRoute(targetType)
        notifyOutputsChanged()
        return true
    }

    // --- Private Priority & Routing Logic ---

    /**
     * Determines the active route based on priority rules:
     * 1. Manual user pick (if still available).
     * 2. Connected external device (Bluetooth if permission granted > Wired Headset).
     * 3. For video calls: Speaker.
     * 4. For voice calls: Earpiece (or Speaker if no earpiece).
     */
    private fun resolvePriorityRoute(available: List<AudioOutputDeviceItem>): String {
        val availableTypes = available.map { it.type }.toSet()

        // 1. Manual user selection
        if (userSelectedRoute != null && availableTypes.contains(userSelectedRoute)) {
            return userSelectedRoute!!
        }

        // 2. External connected devices take priority over built-ins
        if (availableTypes.contains(TYPE_BLUETOOTH) && hasBluetoothPermission()) {
            return TYPE_BLUETOOTH
        }
        if (availableTypes.contains(TYPE_WIRED_HEADSET)) {
            return TYPE_WIRED_HEADSET
        }

        // 3. Built-in defaults: Video -> Speaker, Voice -> Earpiece
        if (callType == "video" && availableTypes.contains(TYPE_SPEAKER)) {
            return TYPE_SPEAKER
        }
        if (availableTypes.contains(TYPE_EARPIECE)) {
            return TYPE_EARPIECE
        }

        return if (availableTypes.contains(TYPE_SPEAKER)) TYPE_SPEAKER else (available.firstOrNull()?.type ?: TYPE_SPEAKER)
    }

    private fun applyRoute(targetType: String) {
        currentActiveRoute = targetType

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val commDevices = audioManager.availableCommunicationDevices
                val targetDevice = when (targetType) {
                    TYPE_EARPIECE -> commDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    TYPE_SPEAKER -> commDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    TYPE_WIRED_HEADSET -> commDevices.find {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                    }
                    TYPE_BLUETOOTH -> commDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    }
                    else -> null
                }

                if (targetDevice != null) {
                    val success = audioManager.setCommunicationDevice(targetDevice)
                    Log.i(TAG, "setCommunicationDevice($targetType - ${targetDevice.productName}) result=$success")
                } else if (targetType == TYPE_EARPIECE) {
                    // Clearing communication device defaults back to standard earpiece in Android
                    audioManager.clearCommunicationDevice()
                    Log.i(TAG, "clearCommunicationDevice() for earpiece fallback")
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed setCommunicationDevice (API 31+): ${e.message}", e)
            }
        }

        // API < 31 Legacy fallback
        try {
            when (targetType) {
                TYPE_SPEAKER -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                    Log.i(TAG, "Legacy speakerphone enabled")
                }
                TYPE_BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    Log.i(TAG, "Legacy bluetooth SCO enabled")
                }
                TYPE_EARPIECE, TYPE_WIRED_HEADSET -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                    Log.i(TAG, "Legacy earpiece / wired enabled (speaker & bt off)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed legacy route switch: ${e.message}", e)
        }
    }

    private fun handleDevicesChanged() {
        if (!isRoutingStarted) return

        val currentAvailable = getAvailableOutputs()
        val currentTypes = currentAvailable.map { it.type }.toSet()

        val newlyConnectedExternal = (currentTypes - previousAvailableTypes).filter {
            it == TYPE_BLUETOOTH || it == TYPE_WIRED_HEADSET
        }

        val disconnected = previousAvailableTypes - currentTypes
        previousAvailableTypes = currentTypes

        // 1. Newly connected external device (Bluetooth or Wired) gets active priority!
        if (newlyConnectedExternal.isNotEmpty()) {
            val autoNewRoute = if (newlyConnectedExternal.contains(TYPE_BLUETOOTH) && hasBluetoothPermission()) {
                TYPE_BLUETOOTH
            } else {
                newlyConnectedExternal.first()
            }
            Log.i(TAG, "External device connected mid-call: $autoNewRoute. Auto-routing to it.")
            userSelectedRoute = null // Auto-priority overrides previous manual pick
            applyRoute(autoNewRoute)
            notifyOutputsChanged()
            return
        }

        // 2. Active device disconnected: fallback
        if (currentActiveRoute != null && disconnected.contains(currentActiveRoute)) {
            Log.i(TAG, "Active device '$currentActiveRoute' disconnected mid-call. Falling back.")
            if (userSelectedRoute == currentActiveRoute) {
                userSelectedRoute = null
            }
            val fallbackRoute = resolvePriorityRoute(currentAvailable)
            applyRoute(fallbackRoute)
            notifyOutputsChanged()
            return
        }

        // Otherwise notify if available devices changed
        notifyOutputsChanged()
    }

    private fun notifyOutputsChanged() {
        val result = getOutputsResult()
        mainHandler.post {
            onOutputsChangedListener?.invoke(result)
        }
    }

    // --- Audio Focus ---

    private fun requestCallAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                    }
                    .build()

                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting audio focus: ${e.message}", e)
        }
    }

    private fun abandonCallAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}", e)
        }
    }

    // --- Listeners & Receivers Registration ---

    private fun registerListeners() {
        // API 31+ OnCommunicationDeviceChangedListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val listener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                    Log.d(TAG, "OnCommunicationDeviceChangedListener triggered: device=${device?.productName}")
                    mainHandler.post { handleDevicesChanged() }
                }
                communicationDeviceChangedListener = listener
                audioManager.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error adding OnCommunicationDeviceChangedListener: ${e.message}", e)
            }

            try {
                val callback = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        Log.d(TAG, "Audio devices added: ${addedDevices?.size}")
                        mainHandler.post { handleDevicesChanged() }
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        Log.d(TAG, "Audio devices removed: ${removedDevices?.size}")
                        mainHandler.post { handleDevicesChanged() }
                    }
                }
                audioDeviceCallback = callback
                audioManager.registerAudioDeviceCallback(callback, mainHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Error registering AudioDeviceCallback: ${e.message}", e)
            }
        }

        // Always register BroadcastReceivers for headset and bluetooth broadcast intents
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_HEADSET_PLUG -> {
                            val state = intent.getIntExtra("state", -1)
                            isWiredHeadsetConnectedLegacy = (state == 1)
                            Log.d(TAG, "Headset plug broadcast: state=$state")
                            handleDevicesChanged()
                        }
                        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                            isBluetoothConnectedLegacy = (state == BluetoothProfile.STATE_CONNECTED)
                            Log.d(TAG, "Bluetooth connection state broadcast: state=$state")
                            handleDevicesChanged()
                        }
                        AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                            Log.d(TAG, "SCO audio state broadcast: state=$state")
                        }
                    }
                }
            }
            legacyReceiver = receiver
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering broadcast receiver: ${e.message}", e)
        }
    }

    private fun unregisterListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                (communicationDeviceChangedListener as? AudioManager.OnCommunicationDeviceChangedListener)?.let {
                    audioManager.removeOnCommunicationDeviceChangedListener(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing OnCommunicationDeviceChangedListener: ${e.message}", e)
            }
            communicationDeviceChangedListener = null

            try {
                audioDeviceCallback?.let {
                    audioManager.unregisterAudioDeviceCallback(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering AudioDeviceCallback: ${e.message}", e)
            }
            audioDeviceCallback = null
        }

        try {
            legacyReceiver?.let {
                context.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering legacyReceiver: ${e.message}", e)
        }
        legacyReceiver = null
    }

    @Suppress("DEPRECATION")
    private fun isWiredHeadsetPluggedInLegacy(): Boolean {
        return try {
            audioManager.isWiredHeadsetOn
        } catch (e: Exception) {
            false
        }
    }

    private fun isBluetoothHeadsetConnectedLegacy(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        } catch (e: Exception) {
            false
        }
    }
}
