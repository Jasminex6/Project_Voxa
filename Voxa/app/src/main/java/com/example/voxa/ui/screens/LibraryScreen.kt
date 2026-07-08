package com.example.voxa.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.R
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.LogEvent
import com.example.voxa.ui.theme.*

@Composable
fun LibraryScreen(viewModel: IVoxaViewModel, onNavigateToEnrollment: () -> Unit) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val intents by viewModel.enrolledIntents.collectAsState()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.library_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isRtl) TextAlign.End else TextAlign.Start
            )
            Text(
                text = activeProfile?.let { stringResource(R.string.library_desc, it.name) }
                    ?: stringResource(R.string.library_desc, "the active profile"),
                fontSize = 13.sp,
                color = Slate400,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                textAlign = if (isRtl) TextAlign.End else TextAlign.Start
            )

            if (activeProfile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.library_no_profile),
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
                        text = stringResource(R.string.library_empty),
                        color = Slate400,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(intents, key = { it.id }) { intent ->
                        LibraryIntentItem(
                            intent = intent,
                            onPlayPreview = { viewModel.playRecordedSample(intent) },
                            onDelete = { viewModel.deleteIntent(intent) }
                        )
                    }
                }
            }
        }

        // Floating "+ Add Sound" button at bottom center
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
                    Text(stringResource(R.string.library_add_sound_btn), color = Slate900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun LibraryIntentItem(intent: EnrolledIntent, onPlayPreview: () -> Unit, onDelete: () -> Unit) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.library_intent_prefix, intent.intentName),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.library_phrase_prefix, intent.outputPhrase),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Sky400,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.library_audio_prefix, intent.audioAssetPath.substringAfterLast("/")),
                    fontSize = 11.sp,
                    color = Slate400,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                )
            }

            IconButton(
                onClick = onPlayPreview,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "🔊",
                    fontSize = 18.sp
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

private class MockLibraryViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true))
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(
        listOf(
            EnrolledIntent(id = 1, profileId = 1, intentName = "Water", outputPhrase = "أنا عايز ميّه", audioAssetPath = "water.mp3"),
            EnrolledIntent(id = 2, profileId = 1, intentName = "Milk", outputPhrase = "أنا عايز لبن", audioAssetPath = "milk.mp3")
        )
    )
    override val practiceStats = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.PracticeStats>())
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
    override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
    override val appLanguage = kotlinx.coroutines.flow.MutableStateFlow("en")
    override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
    override fun selectActiveProfile(profileId: Long) {}
    override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
    override fun exportProfileData(context: Context) {}
    override fun importProfileData(context: Context, uri: android.net.Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {}
    override fun deleteIntent(intent: EnrolledIntent) {}
    override fun toggleListening() {}
    override fun updateListeningState() {}
    override fun addLogSystemEvent(message: String) {}
    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
    override fun clearLogs() {}
    override fun playRecordedSample(intent: EnrolledIntent) {}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Library Screen Preview")
@Composable
fun LibraryScreenPreview() {
    VoxaTheme {
        LibraryScreen(viewModel = MockLibraryViewModel(), onNavigateToEnrollment = {})
    }
}
