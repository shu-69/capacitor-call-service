package com.geekbros.plugins.callservice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

object RingtonePlayer {

    private const val TAG = "RingtonePlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    @Volatile
    private var isPlaying = false

    @Synchronized
    fun start(context: Context) {
        if (isPlaying) return
        isPlaying = true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        // 1. Handle Silent Mode: Neither sound nor vibration
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.d(TAG, "Ringer mode is SILENT. Skipping ringtone and vibration.")
            return
        }

        // 2. Start Vibration (for VIBRATE and NORMAL modes)
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 1) // 1 = repeat from index 1
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 1)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start vibration: ${e.message}")
        }

        // 3. Start Ringtone Audio (only for NORMAL mode)
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_RINGTONE_URI

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, ringtoneUri)
                    setAudioAttributes(attributes)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Started looping ringtone")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to play ringtone with MediaPlayer: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!isPlaying) return
        isPlaying = false

        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping ringtone MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Throwable) {
            Log.w(TAG, "Error cancelling vibrator: ${e.message}")
        } finally {
            vibrator = null
        }

        Log.d(TAG, "Ringtone and vibration stopped")
    }
}

