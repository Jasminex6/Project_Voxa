package com.example.voxa

import android.util.Base64
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
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
            fun post(path: String, body: JSONObject, withOrigin: Boolean): JSONObject {
                val connection = URL("${BuildConfig.VOXA_WEB_URL}/api/$path").openConnection() as HttpURLConnection
                return try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                if (withOrigin) connection.setRequestProperty("Origin", BuildConfig.VOXA_WEB_URL)
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                assertEquals(200, connection.responseCode)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally { connection.disconnect() }
            }
            val generated = post("demo/activate", JSONObject().put("tier", tier.name), true)
            val code = generated.getString("code")
            assertEquals(19, code.length)
            assertEquals(code, ActivationClient.parse("${BuildConfig.VOXA_WEB_URL}/activate?code=$code"))
            assertEquals(code, ActivationClient.parse(code.lowercase()))
            val bitmap = InstrumentationRegistry.getInstrumentation().context.assets
                .open("qr-${tier.name.lowercase()}-demo.png").use { BitmapFactory.decodeStream(it) }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
            bitmap.recycle()
            val scannedCode = ActivationClient.parse(decoded)
            assertTrue(scannedCode.startsWith(if (tier == LicenseTier.FAMILY_PRO) "VOXA-F-" else "VOXA-C-"))
            val result = post("license/activate", JSONObject().put("code", scannedCode).put("deviceId", deviceId), false)
            val token = result.getString("token")
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
