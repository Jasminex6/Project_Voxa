package com.example.voxa

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.voxa.data.license.ActivationClient
import com.example.voxa.data.license.CryptoLicenseVerifier
import com.example.voxa.data.license.LicenseStatus
import com.example.voxa.data.license.LicenseTier
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Live interoperability only. Never touches the installed family's data, PIN, or license prefs. */
@RunWith(AndroidJUnit4::class)
class HostedDemoDeviceTest {
    @Test fun liveWebsiteCodesVerifyOnAndroidWithTheRequestedPlan() {
        assertTrue(BuildConfig.DEBUG)
        val deviceId = UUID.randomUUID().toString()
        val verifier = CryptoLicenseVerifier(mapOf(BuildConfig.HOSTED_DEMO_KEY_ID to
            Base64.decode(BuildConfig.HOSTED_DEMO_PUBLIC_KEY, Base64.DEFAULT)))
        for (tier in listOf(LicenseTier.FAMILY_PRO, LicenseTier.CLINICAL_SLP)) {
            val connection = URL("${BuildConfig.VOXA_WEB_URL}/api/demo/activate").openConnection() as HttpURLConnection
            val result = try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Origin", BuildConfig.VOXA_WEB_URL)
                connection.outputStream.use { it.write(JSONObject().put("tier", tier.name).put("deviceId", deviceId).toString().toByteArray()) }
                assertEquals(200, connection.responseCode)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
            val token = ActivationClient.parse(result.getString("deepLink"))
            assertEquals(result.getString("token"), token)
            val license = verifier.verify(token, deviceId, System.currentTimeMillis() / 1000)
            assertEquals(tier, license.effectiveTier)
            assertEquals(LicenseStatus.ACTIVE, license.status)
            assertTrue(license.canEnrollIntent(40))
            assertTrue(license.hasFullPractice)
            assertTrue(license.canExportProgress)
            assertThrows(IllegalArgumentException::class.java) {
                verifier.verify(token, UUID.randomUUID().toString(), System.currentTimeMillis() / 1000)
            }
        }
    }
}
