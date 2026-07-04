package com.example.voxa.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    /**
     * Retrieves the current device location coordinates using Google Play Services Location API.
     * Requires ACCESS_FINE_LOCATION permission to be granted beforehand.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val tokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
                .addOnCanceledListener {
                    continuation.resume(null)
                }

            continuation.invokeOnCancellation {
                tokenSource.cancel()
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
        val location = getCurrentLocation(context)
        val mapsUrl = getGoogleMapsUrl(location)
        val message = "🚨 VOXA EMERGENCY ALERT! ${profile.name} needs help! Current Location: $mapsUrl"

        // Collect caregiver phone numbers
        val phones = listOf(profile.caregiverPhone1, profile.caregiverPhone2, profile.caregiverPhone3)
            .filter { it.isNotBlank() }

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
}
