package com.example.voxa

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.ui.screens.OnboardingScreen
import com.example.voxa.ui.theme.VoxaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class OnboardingLanguageTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun languageSelectionRefreshesResourcesPersistsAndKeepsOnboardingStep() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=context.getSharedPreferences(LocaleTestActivity.SETTINGS, Context.MODE_PRIVATE)
        val originalLocale=Locale.getDefault()
        settings.edit().clear().putString("app_language","en").commit()
        val model=ThemeIntegrationTest.TestModel()
        LocaleTestActivity.content={ VoxaTheme { OnboardingScreen(model) { error("This test must not finish setup") } } }
        try {
            ActivityScenario.launch(LocaleTestActivity::class.java).use { scenario ->
                ui.onNodeWithText("Arabic").performClick()
                waitForText("التالي")
                ui.onNodeWithText("العربية").assertIsSelected()
                assertEquals("ar", settings.getString("app_language",null))
                scenario.onActivity { assertEquals("ar",it.resources.configuration.locales[0].language) }
                save(context,"onboarding-ar.png")
                ui.onNodeWithText("التالي").performClick()
                waitForText("👋 مرحباً بك في فوكسا")
                scenario.recreate()
                waitForText("👋 مرحباً بك في فوكسا")
                ui.onNodeWithText(context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ar")) }).getString(R.string.onboarding_back)).performClick()
                waitForText("English")
                ui.onNodeWithText("English").performClick()
                waitForText("Next")
                ui.onNodeWithText("English").assertIsSelected()
                assertEquals("en", settings.getString("app_language",null))
                save(context,"onboarding-en.png")
                ui.onNodeWithText("Next").performClick()
                waitForText("👋 Welcome to Voxa")
            }
        } finally {
            LocaleTestActivity.content=null
            settings.edit().clear().commit()
            Locale.setDefault(originalLocale)
        }
    }
    private fun waitForText(text:String) {
        ui.waitUntil(10_000) { runCatching { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    private fun save(context:Context,name:String) {
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
