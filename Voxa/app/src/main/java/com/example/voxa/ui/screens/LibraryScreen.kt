package com.example.voxa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*

/**
 * 📚 LibraryScreen
 * Lists all caregiver-enrolled words/intents for the active profile.
 * Allows caregivers to delete intents cleanly via the database.
 * 
 * Analogy: This acts as the custom dictionary binder. It collects live state updates
 * from the database and redraws the cards whenever the active child profile or vocabulary changes.
 */
@Composable
fun LibraryScreen(viewModel: IVoxaViewModel, onNavigateToEnrollment: () -> Unit) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val intents by viewModel.enrolledIntents.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "📚 Word Dictionary Library",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Manage custom sound meanings for ${activeProfile?.name ?: "the active profile"}.",
                fontSize = 13.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (activeProfile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Please create or activate a child profile first\nto view or manage their dictionary.",
                        color = Slate400,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (intents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom words enrolled yet.\nTap + Add Sound below to record the first word!",
                        color = Slate400,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp) // Space for floating button
                ) {
                    items(intents, key = { it.id }) { intent ->
                        LibraryIntentItem(intent = intent, onDelete = { viewModel.deleteIntent(intent) })
                    }
                }
            }
        }

        // ── Floating "+ Add Sound" button at bottom center with gradient fade ──
        if (activeProfile != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Slate900.copy(alpha = 0.95f)),
                            startY = 0f,
                            endY = 200f
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = onNavigateToEnrollment,
                    colors = ButtonDefaults.buttonColors(containerColor = Sky400),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Text("+ Add Sound", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun LibraryIntentItem(intent: EnrolledIntent, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Intent: ${intent.intentName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Phrase: ${intent.outputPhrase}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Sky400 // Highlighting translation phrase in cyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audio: ${intent.audioAssetPath.substringAfterLast("/")}",
                    fontSize = 11.sp,
                    color = Slate400
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Text(
                    text = "❌",
                    fontSize = 18.sp
                )
            }
        }
    }
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──

private class MockLibraryViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true))
    // Design Note: Storing only gender-neutral filenames here as per the updated playback structure.
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(
        listOf(
            EnrolledIntent(id = 1, profileId = 1, intentName = "Water", outputPhrase = "أنا عايز ميّه", audioAssetPath = "water.mp3"),
            EnrolledIntent(id = 2, profileId = 1, intentName = "Milk", outputPhrase = "أنا عايز لبن", audioAssetPath = "milk.mp3")
        )
    )
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
    override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
    override fun selectActiveProfile(profileId: Long) {}
    override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
    override fun deleteIntent(intent: EnrolledIntent) {}
    override fun toggleListening() {}
    override fun updateListeningState() {}
    override fun addLogSystemEvent(message: String) {}
    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
    override fun clearLogs() {}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Library Screen Preview")
@Composable
fun LibraryScreenPreview() {
    VoxaTheme {
        LibraryScreen(viewModel = MockLibraryViewModel(), onNavigateToEnrollment = {})
    }
}

