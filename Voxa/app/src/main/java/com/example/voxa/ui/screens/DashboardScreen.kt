package com.example.voxa.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close

import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import android.widget.Toast
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// The dance floor (The main monitoring screen where the action happens)
/**
 * 🏠 DashboardScreen
 * The main monitoring cockpit for caregivers.
 * Displays:
 * 1. Active child profile info card.
 * 2. Large pulsing Microphone controller to toggle listening.
 * 3. Match Simulation card for live demos.
 * 4. Recent vocalization log events timeline.
 */
@Composable
fun DashboardScreen(viewModel: IVoxaViewModel, onNavigateToProfile: () -> Unit) {
    // Observing state flows from the ViewModel. Think of these as subscribing to digital notice boards.
    // Compose automatically recomposes (re-renders) this screen whenever these values change.
    val isListening by viewModel.isListening.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()
    val volumeLevel by viewModel.volumeLevel.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Ripple 1
    val rippleScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale1"
    )
    val rippleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha1"
    )
    
    // Ripple 2
    val rippleScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale2"
    )
    val rippleAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha2"
    )

    var isSidebarOpen by remember { mutableStateOf(false) }
    var showCaregiverEditDialog by remember { mutableStateOf(false) }
    var caregiverName by remember { mutableStateOf("Parent / Caregiver") }
    var caregiverPhone by remember { mutableStateOf("+1 234 567 890") }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importProfileData(
                context = context,
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, "Profile imported successfully!", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    Toast.makeText(context, "Import failed: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val lastVocalEvent = remember(recentEvents) {
        recentEvents.firstOrNull { it.word != "SYSTEM" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Slate900)
                .padding(16.dp)
        ) {
            // ── HEADER: Child emoji + name + menu ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile emoji placeholder
                    Text(
                        text = activeProfile?.avatarEmoji ?: "👦",
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeProfile?.name ?: "No Profile",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Box {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Sidebar",
                        tint = Slate300,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isSidebarOpen = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── PULSING LISTENING CONTROL ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    // Pulsing ripple 1
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(rippleScale1)
                            .clip(CircleShape)
                            .background(Color(0xFF00F2FE).copy(alpha = rippleAlpha1))
                    )
                    // Pulsing ripple 2
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(rippleScale2)
                            .clip(CircleShape)
                            .background(Color(0xFF00F2FE).copy(alpha = rippleAlpha2))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) Color(0xFF0F1E2E) else Slate800
                        )
                        .border(
                            width = 2.dp,
                            color = if (isListening) Color(0xFF00F2FE) else Slate700,
                            shape = CircleShape
                        )
                        .clickable { viewModel.toggleListening() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (isListening) "Listening Active" else "Listening Paused",
                        tint = if (isListening) Color(0xFF00F2FE) else Slate400,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // ── LIVE TRANSLATION TRANSCRIPT CARD ──
            Text(
                text = "Live Translation",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Sky400,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (lastVocalEvent == null || !isListening) Slate800 else if (lastVocalEvent.isMatch) Color(0xFF0C2417) else Color(0xFF2C1919)
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (lastVocalEvent == null || !isListening) Slate700 else if (lastVocalEvent.isMatch) SuccessGreen else ErrorRed
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (lastVocalEvent == null || !isListening) {
                        Text(
                            text = if (isListening) "🎙️ Listening..." else "🎙️ Ready to Translate",
                            color = Slate300,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        if (isListening) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Slate900)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(volumeLevel.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF00F2FE), Sky400)
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isListening) "Waiting for child vocalizations..." else "Tap the microphone to start monitoring",
                            color = Slate400,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (lastVocalEvent.isMatch) {
                        Text(
                            text = lastVocalEvent.phrase, // Arabic phrase
                            color = SuccessGreen,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Translated: ${lastVocalEvent.word}", // English meaning
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Confidence: ${(lastVocalEvent.confidence * 100).toInt()}%",
                            color = Slate300,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Unrecognized Sound",
                            color = ErrorRed,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastVocalEvent.detail, // Reason
                            color = Slate300,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── ACTIVITY LOG TIMELINE ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 Activity Log Timeline",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (recentEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent sounds detected.\nActivate monitoring to start translating.",
                        color = Slate300,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(recentEvents.size) {
                    if (recentEvents.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentEvents, key = { it.id }) { event ->
                        TimelineItem(event)
                    }
                }
            }
        }

        // ── RIGHT SIDEBAR DRAWER OVERLAY ──
        if (isSidebarOpen) {
            // Dim backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { isSidebarOpen = false }
            )

            // Right Slide Panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .align(Alignment.CenterEnd)
                    .background(Slate800)
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Title & Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voxa Hub Menu",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { isSidebarOpen = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Menu",
                                    tint = Slate300
                                )
                            }
                        }

                        Divider(color = Slate700, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                        // 👦 Child Info Section
                        Text(
                            text = "👦 Child Info",
                            color = Sky400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeProfile?.avatarEmoji ?: "👦",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = activeProfile?.name ?: "No profile active",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Voice Pack: ${activeProfile?.gender ?: "None"}",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                isSidebarOpen = false
                                onNavigateToProfile()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Sky400),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Manage Profiles", color = Slate900, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 👨‍👩‍👧 Caregivers Info Section
                        Text(
                            text = "👨‍👩‍👧 Caregivers Info",
                            color = Sky400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = caregiverName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Phone: $caregiverPhone",
                                    color = Slate300,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "✏️ Edit Caregiver Details",
                                    color = Sky400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { showCaregiverEditDialog = true }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ⚙️ Settings Section
                        Text(
                            text = "⚙️ Settings",
                            color = Sky400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "App Language: English",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Continuous Listening: ON",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 💾 Data Management Section
                        Text(
                            text = "💾 Data Management",
                            color = Sky400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "📤 Export Profile & Intents",
                                    color = Sky400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        viewModel.exportProfileData(context)
                                    }
                                )
                                Text(
                                    text = "📥 Import Profile & Intents",
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        importLauncher.launch("application/json")
                                    }
                                )
                                Text(
                                    text = "🧹 Clear Timeline Logs",
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        viewModel.clearLogs()
                                        Toast.makeText(context, "Timeline cleared!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // Version Tag
                    Text(
                        text = "Voxa Version v1.12.0",
                        color = Slate400,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Caregiver Info Edit Dialog
        if (showCaregiverEditDialog) {
            var tempName by remember { mutableStateOf(caregiverName) }
            var tempPhone by remember { mutableStateOf(caregiverPhone) }
            AlertDialog(
                onDismissRequest = { showCaregiverEditDialog = false },
                title = { Text("Edit Caregiver Info", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = Slate800,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Name", color = Slate400) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Sky400,
                                unfocusedBorderColor = Slate600
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tempPhone,
                            onValueChange = { tempPhone = it },
                            label = { Text("Phone Number", color = Slate400) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Sky400,
                                unfocusedBorderColor = Slate600
                            ),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempName.isNotBlank() && tempPhone.isNotBlank()) {
                                caregiverName = tempName
                                caregiverPhone = tempPhone
                            }
                            showCaregiverEditDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky400)
                    ) {
                        Text("Save", color = Slate900, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCaregiverEditDialog = false }) {
                        Text("Cancel", color = Slate400)
                    }
                }
            )
        }
    }
}

@Composable
fun TimelineItem(event: LogEvent) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = formatter.format(Date(event.timestamp))

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = BorderStroke(1.dp, Slate700),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // High-end vertical accent status bar on the left edge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(
                        when {
                            event.word == "SYSTEM" -> Slate600
                            event.isMatch -> SuccessGreen
                            else -> WarningAmber
                        }
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (event.word == "SYSTEM") "System Update" else "Detected Vocalization",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        if (event.word != "SYSTEM" && event.isMatch) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (event.isMatch) SuccessGreen.copy(alpha = 0.15f)
                                        else WarningAmber.copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${(event.confidence * 100).toInt()}% Conf",
                                    fontSize = 10.sp,
                                    color = if (event.isMatch) SuccessGreen else WarningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (event.word == "SYSTEM") event.detail else event.phrase,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (event.word != "SYSTEM") {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sound: '${event.word}' • ${event.detail}",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = timeStr,
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──

private class MockDashboardViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true))
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(
        listOf(
            LogEvent(word = "Water", phrase = "أنا عايز ميّه", confidence = 0.89f, isMatch = true, detail = "Passed absolute and margin thresholds"),
            LogEvent(word = "SYSTEM", phrase = "Listening session active — monitoring background sounds", confidence = 1.0f, isMatch = true, detail = "System Action")
        )
    )
    override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
    override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
    override fun selectActiveProfile(profileId: Long) {}
    override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
    override fun exportProfileData(context: Context) {}
    override fun importProfileData(context: Context, uri: android.net.Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {}
    override fun deleteIntent(intent: com.example.voxa.data.EnrolledIntent) {}
    override fun toggleListening() {}
    override fun updateListeningState() {}
    override fun addLogSystemEvent(message: String) {}
    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
    override fun clearLogs() {}
    override fun playRecordedSample(intent: com.example.voxa.data.EnrolledIntent) {}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Dashboard Screen Preview")
@Composable
fun DashboardScreenPreview() {
    VoxaTheme {
        DashboardScreen(viewModel = MockDashboardViewModel(), onNavigateToProfile = {})
    }
}

