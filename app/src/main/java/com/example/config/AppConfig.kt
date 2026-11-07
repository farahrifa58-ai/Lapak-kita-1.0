package com.example.config

import android.content.Context
import android.content.SharedPreferences

object AppConfig {
    // Configured for build: false for Full Version, true for Trial Version
    var IS_TRIAL: Boolean = false

    const val TRIAL_DURATION_MINUTES = 60L
}

class TrialManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lapak_trial_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_START_TIME = "trial_start_time"
        private const val DURATION_MILLIS = 60 * 60 * 1000L // 60 minutes
    }

    init {
        if (!prefs.contains(KEY_START_TIME)) {
            prefs.edit().putLong(KEY_START_TIME, System.currentTimeMillis()).apply()
        }
    }

    fun getStartTime(): Long {
        return prefs.getLong(KEY_START_TIME, System.currentTimeMillis())
    }

    fun getRemainingSeconds(): Long {
        val start = getStartTime()
        val elapsed = System.currentTimeMillis() - start
        val remaining = (DURATION_MILLIS - elapsed) / 1000L
        return remaining.coerceAtLeast(0)
    }

    fun getRemainingMinutes(): Long {
        return (getRemainingSeconds() + 59) / 60
    }

    fun isExpired(): Boolean {
        return getRemainingSeconds() <= 0
    }
}
