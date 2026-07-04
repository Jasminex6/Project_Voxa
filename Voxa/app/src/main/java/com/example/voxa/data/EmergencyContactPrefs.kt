package com.example.voxa.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 🆘 EmergencyContactPrefs
 * SharedPreferences wrapper for storing emergency contact information
 * and the first-time setup completion flag.
 *
 * Emergency contact data is app-wide (not per-child), so SharedPreferences
 * is a simpler fit than adding a new Room entity + migration.
 */
object EmergencyContactPrefs {

    private const val PREFS_NAME = "voxa_emergency_prefs"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_CONTACT_NAME = "emergency_contact_name"
    private const val KEY_CONTACT_RELATION = "emergency_contact_relation"
    private const val KEY_CONTACT_PHONE = "emergency_contact_phone"
    private const val KEY_EMERGENCY_MESSAGE = "emergency_message"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Setup Completion Flag ──

    fun isSetupComplete(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun markSetupComplete(context: Context) {
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
    }

    // ── Emergency Contact Getters ──

    fun getContactName(context: Context): String {
        return prefs(context).getString(KEY_CONTACT_NAME, "") ?: ""
    }

    fun getContactRelation(context: Context): String {
        return prefs(context).getString(KEY_CONTACT_RELATION, "") ?: ""
    }

    fun getContactPhone(context: Context): String {
        return prefs(context).getString(KEY_CONTACT_PHONE, "") ?: ""
    }

    fun getEmergencyMessage(context: Context): String {
        return prefs(context).getString(KEY_EMERGENCY_MESSAGE, "") ?: ""
    }

    // ── Emergency Contact Setter (batch save) ──

    fun saveEmergencyContact(
        context: Context,
        name: String,
        relation: String,
        phone: String,
        message: String
    ) {
        prefs(context).edit().apply {
            putString(KEY_CONTACT_NAME, name)
            putString(KEY_CONTACT_RELATION, relation)
            putString(KEY_CONTACT_PHONE, phone)
            putString(KEY_EMERGENCY_MESSAGE, message)
            apply()
        }
    }
}
