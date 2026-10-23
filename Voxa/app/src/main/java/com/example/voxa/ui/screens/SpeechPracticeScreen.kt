package com.example.voxa.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.VoxaTheme

// ── Accent colors for the Practice tab (used in navigation bar) ──
val PracticeViolet = Color(0xFFa855f7)
val PracticeVioletDark = Color(0xFF7c3aed)
val PracticeVioletBg = Color(0xFF581c87)

/**
 * 🎮 SpeechPracticeScreen — Main speech practice screen.
 * Displays the Say & Earn gamified pronunciation practice system.
 * The old Pronunciation Lab has been removed per user specification.
 */
@Composable
fun SpeechPracticeScreen(viewModel: IVoxaViewModel, onUpgrade: () -> Unit = {}) {
    SayAndEarnScreen(viewModel = viewModel, onUpgrade = onUpgrade)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Speech Practice Screen")
@Composable
fun SpeechPracticeScreenPreview() {
    VoxaTheme {
        SpeechPracticeScreen(viewModel = object : IVoxaViewModel {
            override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
            override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(
                com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true)
            )
            override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
            override val practiceStats = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.PracticeStats>())
            override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.ui.LogEvent>())
            override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
            override val childPoints = kotlinx.coroutines.flow.MutableStateFlow(42)

            override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
            override fun selectActiveProfile(profileId: Long) {}
            override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
            override fun exportProfileData(context: android.content.Context) {}
            override fun importProfileData(context: android.content.Context, uri: android.net.Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {}
            override fun deleteIntent(intent: com.example.voxa.data.EnrolledIntent) {}
            override fun toggleListening() {}
            override fun updateListeningState() {}
            override fun addLogSystemEvent(message: String) {}
            override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
            override fun clearLogs() {}
            override fun playRecordedSample(intent: com.example.voxa.data.EnrolledIntent) {}
        })
    }
}
