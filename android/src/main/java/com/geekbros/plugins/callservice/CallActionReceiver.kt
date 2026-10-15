package com.geekbros.plugins.callservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REJECT_CALL = "com.geekbros.plugins.callservice.ACTION_REJECT_CALL"
        private const val TAG = "CallActionReceiver"
        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_REJECT_CALL) return

        val callId = intent.getStringExtra("callId") ?: return
        val rejectToken = intent.getStringExtra("rejectToken") ?: return
        val notificationId = intent.getIntExtra("notificationId", IncomingCallNotificationManager.getNotificationId(callId))

        Log.d(TAG, "Notification Reject action received for callId=$callId")
        Log.d("CALLDBG", "Notification Decline/Reject action received: callId=$callId")

        val pendingResult = goAsync()

        // 1. Immediately cancel the notification and stop ringtone
        IncomingCallNotificationManager.cancelIncomingCall(context, callId, notificationId)

        // 2. Perform background HTTP POST /api/calls/:id/reject on background thread
        executor.execute {
            try {
                executeRejectCall(callId, rejectToken)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun executeRejectCall(callId: String, rejectToken: String) {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        val urlString = "$baseUrl/calls/$callId/reject"

        var attempts = 0
        val maxAttempts = 2 // 1 initial attempt + 1 retry on network failure

        while (attempts < maxAttempts) {
            attempts++
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Authorization", "Bearer $rejectToken")
                }

                val body = "{\"rejectToken\":\"$rejectToken\"}"
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "Reject call response code=$responseCode for callId=$callId (attempt $attempts)")
                // Terminate on any response (success 200 or 4xx rejection)
                if (responseCode in 200..499) {
                    break
                }
            } catch (e: IOException) {
                Log.w(TAG, "Network failure rejecting call $callId (attempt $attempts): ${e.message}")
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected error rejecting call $callId: ${e.message}", e)
                break
            } finally {
                conn?.disconnect()
            }
        }
    }
}
