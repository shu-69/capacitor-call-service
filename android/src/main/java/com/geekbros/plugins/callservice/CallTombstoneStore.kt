package com.geekbros.plugins.callservice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object CallTombstoneStore {
    private const val TAG = "CallTombstoneStore"
    private const val PREFS_NAME = "call_tombstones"
    private const val TTL_MS = 60_000L // 60 seconds TTL
    private const val MAX_ENTRIES = 100

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun recordTerminal(context: Context, callId: String) {
        if (callId.isBlank()) return
        try {
            val prefs = getPrefs(context)
            pruneExpiredAndCap(prefs)
            prefs.edit().putLong(callId, System.currentTimeMillis()).apply()
            Log.d(TAG, "Recorded terminal tombstone for callId=$callId")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to record tombstone for callId=$callId: ${e.message}")
        }
    }

    @Synchronized
    fun isTerminal(context: Context, callId: String): Boolean {
        if (callId.isBlank()) return false
        return try {
            val prefs = getPrefs(context)
            pruneExpiredAndCap(prefs)
            val timestamp = prefs.getLong(callId, 0L)
            if (timestamp > 0L) {
                val age = System.currentTimeMillis() - timestamp
                age in 0..TTL_MS
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to check tombstone for callId=$callId: ${e.message}")
            false
        }
    }

    private fun pruneExpiredAndCap(prefs: SharedPreferences) {
        try {
            val all = prefs.all
            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            val validEntries = mutableListOf<Pair<String, Long>>()

            for ((key, value) in all) {
                val ts = (value as? Long) ?: (value as? Number)?.toLong() ?: 0L
                if (now - ts > TTL_MS || ts > now) {
                    editor.remove(key)
                } else {
                    validEntries.add(Pair(key, ts))
                }
            }

            // Cap at MAX_ENTRIES by removing the oldest entries
            if (validEntries.size > MAX_ENTRIES) {
                validEntries.sortBy { it.second } // ascending by timestamp
                val toRemoveCount = validEntries.size - MAX_ENTRIES
                for (i in 0 until toRemoveCount) {
                    editor.remove(validEntries[i].first)
                }
            }

            editor.apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Error pruning tombstones: ${e.message}")
        }
    }
}
