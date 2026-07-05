package com.example.voxa.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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
}
