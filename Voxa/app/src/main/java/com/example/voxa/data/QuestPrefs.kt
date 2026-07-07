package com.example.voxa.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 🔒 QuestPrefs
 * Manages the SharedPreferences for the Quests feature, specifically the Caregiver PIN.
 */
object QuestPrefs {
    private const val PREFS_NAME = "voxa_quest_prefs"
    private const val KEY_CAREGIVER_PIN = "caregiver_pin"
    private const val KEY_CHILD_POINTS = "child_points_" // appended with profileId

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves the Caregiver PIN. Returns null if not set.
     */
    fun getCaregiverPin(context: Context): String? {
        return getPrefs(context).getString(KEY_CAREGIVER_PIN, null)
    }

    /**
     * Sets a new Caregiver PIN.
     */
    fun setCaregiverPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_CAREGIVER_PIN, pin).apply()
    }

    /**
     * Clears the Caregiver PIN.
     */
    fun clearCaregiverPin(context: Context) {
        getPrefs(context).edit().remove(KEY_CAREGIVER_PIN).apply()
    }

    /**
     * Checks if a PIN has been set up.
     */
    fun isPinSetup(context: Context): Boolean {
        return getCaregiverPin(context) != null
    }

    /**
     * Validates an entered PIN against the stored PIN.
     */
    fun validatePin(context: Context, enteredPin: String): Boolean {
        return getCaregiverPin(context) == enteredPin
    }

    /**
     * Gets the total points for a specific child profile.
     */
    fun getChildPoints(context: Context, profileId: Long): Int {
        return getPrefs(context).getInt(KEY_CHILD_POINTS + profileId, 0)
    }

    /**
     * Adds points to a child profile.
     */
    fun addPoints(context: Context, profileId: Long, points: Int) {
        val currentPoints = getChildPoints(context, profileId)
        getPrefs(context).edit().putInt(KEY_CHILD_POINTS + profileId, currentPoints + points).apply()
    }

    /**
     * Deducts points from a child profile. Returns true if successful, false if insufficient points.
     */
    fun deductPoints(context: Context, profileId: Long, points: Int): Boolean {
        val currentPoints = getChildPoints(context, profileId)
        if (currentPoints >= points) {
            getPrefs(context).edit().putInt(KEY_CHILD_POINTS + profileId, currentPoints - points).apply()
            return true
        }
        return false
    }
}
