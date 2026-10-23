package com.example.voxa.utils

import android.Manifest

/** Nearby runtime permissions; Bluetooth permissions did not exist before Android 12. */
object SharingPermissions {
    fun forSdk(sdk: Int): List<String> = buildList {
        if (sdk < 33) {
            // Android 12 requires coarse and fine location in the same request.
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (sdk >= 31) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (sdk >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}
