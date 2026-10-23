package com.example.voxa

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.microphoneGesture
import com.example.voxa.ui.screens.DashboardScreen
import com.example.voxa.ui.theme.VoxaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fake listening state and isolated preferences; never opens the microphone or family data. */
@RunWith(AndroidJUnit4::class)
class MicrophoneGestureDeviceTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dashboardHoldStopsAfterReleaseAndRepeatedQuickTaps() {
        dashboard("hold") { model ->
            repeat(2) {
                ui.onNodeWithContentDescription("Listening Paused").performTouchInput { down(center) }
                ui.onNodeWithContentDescription("Listening Active").assertIsDisplayed()
                ui.onNodeWithContentDescription("Listening Active").performTouchInput { advanceEventTime(450); up() }
                ui.onNodeWithContentDescription("Listening Paused").assertIsDisplayed()
                ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
                ui.onNodeWithContentDescription("Listening Paused").assertIsDisplayed()
            }
            assertEquals(8, model.toggles)
        }
    }

    @Test fun dashboardContinuousStaysOnUntilNextTap() {
        dashboard("continuous") { model ->
            ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
            ui.onNodeWithContentDescription("Listening Active").assertIsDisplayed()
            ui.onNodeWithContentDescription("Listening Active").performTouchInput { click() }
            ui.onNodeWithContentDescription("Listening Paused").assertIsDisplayed()
            assertEquals(2, model.toggles)
        }
    }

    @Test fun cancelledHoldStopsAndUsesLatestCallback() {
        var listening by mutableStateOf(false)
        var revision by mutableIntStateOf(0)
        var stoppedRevision = -1
        ui.setContent {
            val currentRevision = revision
            Box(Modifier.size(100.dp).testTag("mic").microphoneGesture(
                holdMode = true,
                onStart = { listening = true },
                onStop = { listening = false; stoppedRevision = currentRevision },
                onToggle = { error("Hold must never toggle") },
            ))
        }
        ui.onNodeWithTag("mic").performTouchInput { down(center) }
        ui.runOnIdle { assertTrue(listening); revision = 1 }
        ui.onNodeWithTag("mic").performTouchInput { cancel() }
        ui.runOnIdle { assertFalse(listening); assertEquals(1, stoppedRevision) }
    }

    @Test fun switchingModeWhileHoldingStopsBeforeContinuousCanStart() {
        var hold by mutableStateOf(true)
        var listening by mutableStateOf(false)
        ui.setContent {
            Box(Modifier.size(100.dp).testTag("mic").microphoneGesture(
                holdMode = hold,
                onStart = { listening = true },
                onStop = { listening = false },
                onToggle = { listening = !listening },
            ))
        }
        ui.onNodeWithTag("mic").performTouchInput { down(center) }
        ui.runOnIdle { assertTrue(listening); hold = false }
        ui.runOnIdle { assertFalse(listening) }
        ui.onNodeWithTag("mic").performTouchInput { up(); click() }
        ui.runOnIdle { assertTrue(listening) }
        ui.onNodeWithTag("mic").performTouchInput { click() }
        ui.runOnIdle { assertFalse(listening) }
    }

    private fun dashboard(mode: String, check: (MicModel) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = context.getSharedPreferences("voxa_mic_gesture_test", Context.MODE_PRIVATE)
        val model = MicModel()
        settings.edit().clear().putString("mic_mode", mode).commit()
        try {
            ui.setContent { VoxaTheme { DashboardScreen(model, {}, settings = settings) } }
            check(model)
        } finally { settings.edit().clear().commit() }
    }

    private class MicModel : IVoxaViewModel by ThemeIntegrationTest.TestModel() {
        override val isListening = MutableStateFlow(false)
        var toggles = 0
        override fun toggleListening() { toggles++; isListening.value = !isListening.value }
    }
}
