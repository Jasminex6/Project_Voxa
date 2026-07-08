package com.example.voxa.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object LocationHelper {
    private const val TAG = "LocationHelper"

    /**
     * Fetches the current location using FusedLocationProviderClient.
     * Checks permissions internally but caller should ensure they are granted.
     */
    @SuppressLint("MissingPermission")
    suspend fun getFreshLocation(context: Context): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        return suspendCancellableCoroutine { continuation ->
            // First try last known location for rapid response
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null && (System.currentTimeMillis() - location.time) < 60_000) {
                        // Location is fresh enough (under 1 minute old), resolve immediately
                        Log.d(TAG, "Using fresh last known location: ${location.latitude}, ${location.longitude}")
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    } else {
                        // Request active single update
                        val cts = CancellationTokenSource()
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cts.token
                        ).addOnSuccessListener { freshLocation: Location? ->
                            Log.d(TAG, "Fresh location retrieved: ${freshLocation?.latitude}, ${freshLocation?.longitude}")
                            if (continuation.isActive) {
                                continuation.resume(freshLocation)
                            }
                        }.addOnFailureListener { exception ->
                            Log.e(TAG, "Failed to get current location: ${exception.message}")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                        
                        continuation.invokeOnCancellation {
                            cts.cancel()
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get last location: ${exception.message}")
                    // Proceed to try active single update as fallback
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).addOnSuccessListener { freshLocation: Location? ->
                        if (continuation.isActive) {
                            continuation.resume(freshLocation)
                        }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed active update fallback: ${e.message}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    continuation.invokeOnCancellation {
                        cts.cancel()
                    }
                }
        }
    }

    /**
     * Formats latitude and longitude coordinates into a shareable Google Maps link.
     */
    fun getGoogleMapsUrl(location: Location?): String {
        return if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "https://maps.google.com/?q=0.0,0.0"
        }
    }

    /**
     * Dispatches location updates to the registered caregivers chain via WhatsApp and SMS fallbacks.
     */
    suspend fun dispatchEmergencyAlert(
        context: Context,
        profile: com.example.voxa.data.ChildProfile,
        addLog: (String) -> Unit
    ) {
        val location = getFreshLocation(context)
        val mapsUrl = getGoogleMapsUrl(location)
        val message = "🚨 VOXA EMERGENCY ALERT! ${profile.name} needs help! Current Location: $mapsUrl"

        // Parse caregiver phone numbers from Youmna's database structure
        val phones = parseCaregiverPhones(profile.caregiverContactsJson)

        if (phones.isEmpty()) {
            addLog("🆘 SOS triggered but no caregiver phone numbers configured")
            return
        }

        addLog("🆘 SOS Alert sequence started. Contacts to notify: ${phones.size}")

        for ((index, phone) in phones.withIndex()) {
            addLog("Alerting Caregiver ${index + 1}: $phone")
            var sentSuccess = false

            // 1. Try WhatsApp
            try {
                val encodedMsg = java.net.URLEncoder.encode(message, "UTF-8")
                val whatsappIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(whatsappIntent)
                addLog("Opened WhatsApp interface for Caregiver ${index + 1}")
                sentSuccess = true
            } catch (e: Exception) {
                addLog("WhatsApp intent failed for Caregiver ${index + 1}: ${e.message}")
            }

            // 2. Fallback to Background SMS
            if (!sentSuccess) {
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }
                    
                    val parts = smsManager?.divideMessage(message) ?: listOf(message)
                    smsManager?.sendMultipartTextMessage(phone, null, ArrayList(parts), null, null)
                    addLog("Background SMS sent to Caregiver ${index + 1}")
                } catch (e: Exception) {
                    addLog("SMS send failed for Caregiver ${index + 1}: ${e.message}")
                }
            }
            
            // Wait 1 second before notifying the next contact
            kotlinx.coroutines.delay(1000)
        }
    }

    private fun parseCaregiverPhones(jsonStr: String?): List<String> {
        val list = mutableListOf<String>()
        if (jsonStr.isNullOrBlank()) return list
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(obj.getString("phone"))
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Failed to parse caregiver phones: ${e.message}")
        }
        return list
    }
}
