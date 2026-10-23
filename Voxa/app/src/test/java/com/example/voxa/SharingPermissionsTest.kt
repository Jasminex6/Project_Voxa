package com.example.voxa

import android.Manifest.permission.*
import com.example.voxa.utils.SharingPermissions
import org.junit.Assert.*
import org.junit.Test

class SharingPermissionsTest {
    @Test fun legacyDevicesNeverRequestNonexistentNearbyPermissions() {
        for (sdk in 26..30) {
            val permissions = SharingPermissions.forSdk(sdk)
            assertTrue(permissions.contains(ACCESS_FINE_LOCATION))
            assertFalse(permissions.contains(BLUETOOTH_SCAN))
            assertFalse(permissions.contains(NEARBY_WIFI_DEVICES))
        }
    }

    @Test fun androidTwelveRequestsBluetoothAndLocationTogether() {
        for (sdk in 31..32) {
            val permissions = SharingPermissions.forSdk(sdk)
            assertTrue(permissions.containsAll(listOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT,
                BLUETOOTH_SCAN, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)))
            assertFalse(permissions.contains(NEARBY_WIFI_DEVICES))
        }
    }

    @Test fun modernNearbySharingDoesNotRequireLocationPermission() {
        for (sdk in 33..36) {
            val permissions = SharingPermissions.forSdk(sdk)
            assertTrue(permissions.containsAll(listOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT,
                BLUETOOTH_SCAN, NEARBY_WIFI_DEVICES)))
            assertFalse(permissions.contains(ACCESS_FINE_LOCATION))
        }
    }
}
