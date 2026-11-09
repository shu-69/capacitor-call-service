package com.geekbros.plugins.callservice

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.Executors

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"
        @Volatile
        var currentCallId: String? = null
        private var activeActivityRef: WeakReference<IncomingCallActivity>? = null

        fun dismiss(callId: String? = null) {
            val act = activeActivityRef?.get()
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                if (callId == null || callId == currentCallId) {
                    act.runOnUiThread {
                        act.finish()
                    }
                }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private var autoDismissRunnable: Runnable? = null

    private var callId: String = ""
    private var callType: String = "voice"
    private var callerName: String = "Caller"
    private var callerPhoto: String? = null
    private var rejectToken: String = ""
    private var expiresAt: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CALLDBG", "IncomingCallActivity onCreate entry")

        // 1. Configure Window flags for Lockscreen & Screen-On presentation
        setupLockscreenWindowFlags()

        setContentView(R.layout.activity_incoming_call)

        // 2. Extract Intent Extras
        callId = intent.getStringExtra("callId") ?: ""
        callType = intent.getStringExtra("callType") ?: "voice"
        callerName = intent.getStringExtra("callerName") ?: "Incoming Call"
        callerPhoto = intent.getStringExtra("callerPhoto")
        rejectToken = intent.getStringExtra("rejectToken") ?: ""
        expiresAt = intent.getStringExtra("expiresAt")

        currentCallId = callId
        activeActivityRef = WeakReference(this)

        // 3. Bind UI Components
        val tvCallerName = findViewById<TextView>(R.id.tvCallerName)
        val tvCallType = findViewById<TextView>(R.id.tvCallType)
        val tvInitials = findViewById<TextView>(R.id.tvInitialsFallback)
        val ivPhoto = findViewById<ImageView>(R.id.ivCallerPhoto)
        val btnAccept = findViewById<ImageButton>(R.id.btnAccept)
        val btnDecline = findViewById<ImageButton>(R.id.btnDecline)

        tvCallerName.text = callerName.ifBlank { "Minglo User" }
        val isVideo = callType.equals("video", ignoreCase = true)
        tvCallType.text = if (isVideo) "Incoming video call" else "Incoming voice call"

        val initialLetter = callerName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
        tvInitials.text = initialLetter

        if (!callerPhoto.isNullOrBlank()) {
            loadCallerPhotoAsync(callerPhoto!!, ivPhoto, tvInitials)
        }

        btnAccept.setOnClickListener {
            handleAccept()
        }

        btnDecline.setOnClickListener {
            handleDecline()
        }

        // 4. Start Unified Ringtone & Vibration
        RingtonePlayer.start(this)

        // 5. Schedule Ring Window Auto-Dismiss
        scheduleAutoDismiss()
    }

    private fun setupLockscreenWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun loadCallerPhotoAsync(photoUrl: String, imageView: ImageView, fallbackView: TextView) {
        imageExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(photoUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    doInput = true
                }
                conn.connect()
                val bitmap: Bitmap? = BitmapFactory.decodeStream(conn.inputStream)
                if (bitmap != null) {
                    mainHandler.post {
                        if (!isFinishing && !isDestroyed) {
                            imageView.setImageBitmap(bitmap)
                            imageView.visibility = View.VISIBLE
                            fallbackView.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to load caller photo async: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun scheduleAutoDismiss() {
        var delayMs = 30000L
        if (!expiresAt.isNullOrBlank()) {
            try {
                val expiresAtMs = if (expiresAt!!.contains("T")) {
                    Instant.parse(expiresAt).toEpochMilli()
                } else {
                    expiresAt!!.toLong()
                }
                val remainingMs = expiresAtMs - System.currentTimeMillis()
                if (remainingMs in 1000..60000) {
                    delayMs = remainingMs
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse expiresAt='$expiresAt': ${e.message}")
            }
        }

        autoDismissRunnable = Runnable {
            Log.d(TAG, "Ringing deadline expired for callId=$callId. Finishing IncomingCallActivity.")
            IncomingCallNotificationManager.cancelIncomingCall(this, callId)
            RingtonePlayer.stop()
            finish()
        }
        mainHandler.postDelayed(autoDismissRunnable!!, delayMs)
    }

    private fun handleAccept() {
        Log.d("CALLDBG", "IncomingCallActivity: Accept clicked for callId=$callId")
        RingtonePlayer.stop()
        IncomingCallNotificationManager.cancelIncomingCall(this, callId)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km?.isKeyguardLocked == true) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    launchMainActivityForAccept()
                }

                override fun onDismissCancelled() {
                    launchMainActivityForAccept()
                }

                override fun onDismissError() {
                    launchMainActivityForAccept()
                }
            })
        } else {
            launchMainActivityForAccept()
        }
    }

    private fun launchMainActivityForAccept() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = IncomingCallNotificationManager.ACTION_ACCEPT_CALL
            putExtra("action", "ACCEPT_CALL")
            putExtra("callId", callId)
            putExtra("callType", callType)
            putExtra("callerName", callerName)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finish()
    }

    private fun handleDecline() {
        Log.d("CALLDBG", "IncomingCallActivity: Decline clicked for callId=$callId")
        RingtonePlayer.stop()
        IncomingCallNotificationManager.cancelIncomingCall(this, callId)

        if (callId.isNotBlank() && rejectToken.isNotBlank()) {
            CallActionReceiver.rejectCall(this, callId, rejectToken)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        RingtonePlayer.stop()
        autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        autoDismissRunnable = null
        if (currentCallId == callId) {
            currentCallId = null
        }
        if (activeActivityRef?.get() == this) {
            activeActivityRef = null
        }
    }
}

