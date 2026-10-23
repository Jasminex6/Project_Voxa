package com.example.voxa

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.ui.screens.ProgressReviewContent
import com.example.voxa.ui.theme.VoxaTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Fictional report only; never touches the family's database, preferences or PIN. */
class ProgressReviewUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun bilingualReviewIsReadableInBothThemes() {
        var dark by mutableStateOf(false)
        var ar by mutableStateOf(false)
        val report = ReportSamples.review()
        ui.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale(if (ar) "ar" else "en")) }
            CompositionLocalProvider(LocalConfiguration provides config, LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                VoxaTheme(darkTheme = dark) { Surface { key(ar,dark) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { ProgressReviewContent(report) }
                } } }
            }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(language in listOf(false,true)) for(mode in listOf(false,true)) {
            ui.runOnIdle { ar=language;dark=mode }
            ui.onNodeWithText(if(language) "أيام الممارسة" else "Practice days").assertIsDisplayed()
            ui.onNodeWithText(report.subjectId).assertIsDisplayed()
            val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null),"report-ui-${if(language) "ar" else "en"}-${if(mode) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            ui.onNodeWithText(if(language) "التفاصيل" else "Details").performScrollTo().performClick()
            ui.onNodeWithText(if(language) "إخفاء التفاصيل" else "Hide details").assertIsDisplayed()
        }
    }
}
