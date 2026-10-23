package com.example.voxa

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.data.*
import com.example.voxa.data.license.*
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.LogEvent
import com.example.voxa.ui.screens.*
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses a fake child and a separate preference file, never the family's data or PIN. */
@RunWith(AndroidJUnit4::class)
class ThemeIntegrationTest {
    @get:Rule val ui = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = context.getSharedPreferences("voxa_theme_integration_test", Context.MODE_PRIVATE)

    @Before fun prepare() { settings.edit().clear().putString("theme_mode", "dark").commit() }
    @After fun cleanUp() { settings.edit().clear().commit() }

    @Test fun dashboardSwitchPersistsAcrossCompositionRecreationAndKeepsPlan() {
        val model = TestModel()
        var instance by mutableIntStateOf(0)
        ui.setContent {
            key(instance) {
                val theme = rememberThemePreference(settings)
                VoxaTheme(darkTheme = theme.isDark) {
                    Surface {
                        DashboardScreen(model, {}, isDarkTheme = theme.isDark, onToggleTheme = theme.toggle)
                    }
                }
            }
        }
        ui.onNodeWithContentDescription(context.getString(R.string.theme_switch_light)).performClick()
        ui.onNodeWithContentDescription(context.getString(R.string.theme_switch_dark)).assertIsDisplayed()
        assertEquals("light", settings.getString("theme_mode", null))
        saveScreen("theme-dashboard-light.png")
        ui.runOnIdle { instance++ }
        ui.onNodeWithContentDescription(context.getString(R.string.theme_switch_dark)).assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription(context.getString(R.string.theme_switch_light)).assertIsDisplayed()
        assertEquals("dark", settings.getString("theme_mode", null))
        assertEquals(LicenseTier.FAMILY_PRO, model.license.value.effectiveTier)
        saveScreen("theme-dashboard-dark.png")
    }

    @Test fun planCodeEntryAndReportsSurviveThemeSwitch() {
        val model = TestModel()
        var dark by mutableStateOf(true)
        ui.setContent {
            VoxaTheme(darkTheme = dark) {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SubscriptionCard(model)
                        PracticeProgressCard(model)
                    }
                }
            }
        }
        ui.onNodeWithText(context.getString(R.string.license_enter_code)).performClick()
        val label = context.getString(R.string.license_purchase_code)
        ui.onNodeWithText(label).performScrollTo().performTextInput("VOXA-F-001-TEST")
        ui.runOnIdle { dark = false }
        ui.onNodeWithText(label).assertTextContains("VOXA-F-001-TEST")
        ui.onNodeWithText(context.getString(R.string.license_scan)).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(context.getString(R.string.license_activate)).performScrollTo().performClick()
        assertEquals("VOXA-F-001-TEST", model.pendingActivation.value)
        ui.onNodeWithText(context.getString(R.string.license_progress_title)).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("JSON").assertIsDisplayed()
    }

    @Test fun profileTextHasReadableContrastInBothPalettes() {
        var dark by mutableStateOf(false)
        var palette: VoxaExtendedColors? = null
        ui.setContent { VoxaTheme(darkTheme = dark) { palette = LocalVoxaColors.current } }
        for (mode in listOf(false, true)) {
            ui.runOnIdle { dark = mode }
            ui.runOnIdle {
                val colors = palette!!
                assertEquals(mode, colors.isDark)
                val card = colors.normalCard.compositeOver(colors.background).toArgb()
                assertTrue(ColorUtils.calculateContrast(colors.primaryText.toArgb(), card) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(colors.secondaryText.toArgb(), card) >= 4.5)
            }
        }
    }

    private fun saveScreen(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class TestModel : IVoxaViewModel {
        override val license = MutableStateFlow(ActiveLicense(tier = LicenseTier.FAMILY_PRO, status = LicenseStatus.ACTIVE))
        override val pendingActivation = MutableStateFlow<String?>(null)
        override fun requestActivation(value: String?) { pendingActivation.value = value }
        private val child = ChildProfile(id = 101, name = "Demo child", gender = "Male", isActive = true)
        override val allProfiles = MutableStateFlow(listOf(child))
        override val activeProfile = MutableStateFlow<ChildProfile?>(child)
        override val enrolledIntents = MutableStateFlow(emptyList<EnrolledIntent>())
        override val practiceStats = MutableStateFlow(emptyList<PracticeStats>())
        override val isListening = MutableStateFlow(false)
        override val recentEvents = MutableStateFlow(emptyList<LogEvent>())
        override val volumeLevel = MutableStateFlow(0f)
        override fun createProfile(name: String, gender: String, avatarEmoji: String) = Unit
        override fun selectActiveProfile(profileId: Long) = Unit
        override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) = Unit
        override fun exportProfileData(context: Context) = Unit
        override fun importProfileData(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) = Unit
        override fun deleteIntent(intent: EnrolledIntent) = Unit
        override fun toggleListening() = Unit
        override fun updateListeningState() = Unit
        override fun addLogSystemEvent(message: String) = Unit
        override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) = Unit
        override fun clearLogs() = Unit
        override fun playRecordedSample(intent: EnrolledIntent) = Unit
    }
}
