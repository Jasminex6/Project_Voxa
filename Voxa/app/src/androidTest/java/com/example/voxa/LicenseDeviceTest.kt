package com.example.voxa

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.voxa.data.license.LicenseManager
import com.example.voxa.data.license.LicenseStatus
import com.example.voxa.data.license.LicenseTier
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicenseDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun encryptedLicenseRemainsUsableOfflineAndExternalLinksNeedConfirmation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = JSONObject(instrumentation.context.assets.open("license-test-fixture.json").bufferedReader().use { it.readText() })
        val prefs = context.getSharedPreferences("voxa_license", android.content.Context.MODE_PRIVATE)
        // Runs only on the disposable/read-only QA emulator, not a family installation.
        prefs.edit().clear().putString("device_id", fixture.getString("deviceId")).commit()
        val manager = LicenseManager.get(context)
        val token = fixture.getString("token")
        manager.activate(token)
        val ciphertext = prefs.getString("token", null)!!
        assertNotEquals(token, ciphertext)
        assertFalse(ciphertext.contains("FAMILY_PRO"))
        instrumentation.uiAutomation.executeShellCommand("cmd connectivity airplane-mode enable").close()
        try {
            manager.refresh()
            assertEquals(LicenseTier.FAMILY_PRO, manager.license.value.effectiveTier)
            assertThrows(RuntimeException::class.java) { manager.activate("unsigned.invalid.token") }
            assertEquals(LicenseTier.FAMILY_PRO, manager.license.value.effectiveTier)
            prefs.edit().putLong("max_seen_time", System.currentTimeMillis() + 2 * 86400_000L).commit()
            manager.refresh()
            assertEquals(LicenseStatus.CLOCK_INVALID, manager.license.value.status)
            assertEquals(ciphertext, prefs.getString("token", null))
            prefs.edit().putLong("max_seen_time", System.currentTimeMillis()).commit()
            manager.refresh()
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("voxa://activate?code=invalid")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                compose.onNodeWithText(context.getString(R.string.license_activate)).assertIsDisplayed()
                assertEquals(LicenseTier.FAMILY_PRO, manager.license.value.effectiveTier)
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("cmd connectivity airplane-mode disable").close()
        }
    }
}
