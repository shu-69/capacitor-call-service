package com.geekbros.plugins.callservice

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

object RingbackPlayer {

    private const val TAG = "RingbackPlayer"
    private var toneGenerator: ToneGenerator? = null
    @Volatile
    private var isPlaying = false

    @Synchronized
    fun start() {
        if (isPlaying) return
        isPlaying = true

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            Log.d(TAG, "Started continuous supervisory ringback tone (TONE_SUP_RINGTONE)")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start ToneGenerator ringback: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        if (!isPlaying) return
        isPlaying = false

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            Log.d(TAG, "Stopped supervisory ringback tone")
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping ToneGenerator: ${e.message}")
        } finally {
            toneGenerator = null
        }
    }
}
