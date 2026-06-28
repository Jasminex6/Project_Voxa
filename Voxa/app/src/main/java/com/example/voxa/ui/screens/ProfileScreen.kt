package com.example.voxa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.*
import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ArrowBack


/**
 * 👤 ProfileScreen
 * Manages child profiles stored in the database.
 * Caregivers can:
 * 1. Create a new child profile (Name, Gender/Voice Pack).
 * 2. View all enrolled children profiles.
 * 3. Swap the active child profile atomically.
 * 
 * Analogy: This is the user accounts switchboard. It enables managing separate children database spaces
 * and dynamic voice packs.
 */
@Composable
fun ProfileScreen(viewModel: IVoxaViewModel, onBack: () -> Unit) {
    // Observing database profile states via ViewModel live StateFlow broadcasts.
    val profiles by viewModel.allProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var newName by remember { mutableStateOf("") }
    
    // Arabic voice pack gender: determines whether translation audio clips playBoy (Male) or Girl (Female) pre-recorded samples.
    var selectedGender by remember { mutableStateOf("Male") } // Defaults to Male child voice pack
    var selectedEmoji by remember { mutableStateOf("👦") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = Color.White
                )
            }
            Text(
                text = "Child Profile Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── PROFILE CREATION FORM ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add New Child Profile",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Child's Name", color = Slate400) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Sky400,
                        unfocusedBorderColor = Slate600
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Avatar Emoji Selector (Expandable & Minimizable Grid Panel)
                var isEmojiSelectorExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate700)
                            .clickable { isEmojiSelectorExpanded = !isEmojiSelectorExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Selected Avatar: ",
                                fontSize = 13.sp,
                                color = Slate300
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedEmoji,
                                fontSize = 20.sp
                            )
                        }
                        Text(
                            text = if (isEmojiSelectorExpanded) "▲ Hide" else "▼ Choose Emoji",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Sky400
                        )
                    }

                    if (isEmojiSelectorExpanded) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Tap an avatar below to select:",
                                    fontSize = 11.sp,
                                    color = Slate400
                                )
                                val emojis = listOf("👦", "👧", "👶", "🦸", "🥷", "🦄", "🐼", "🦊", "🦁", "🦖")
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val rows = emojis.chunked(5)
                                    rows.forEach { rowEmojis ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            rowEmojis.forEach { emoji ->
                                                val isEmojiSelected = selectedEmoji == emoji
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isEmojiSelected) Sky400.copy(alpha = 0.2f) else Color.Transparent)
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isEmojiSelected) Sky400 else Slate700,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { 
                                                            selectedEmoji = emoji
                                                            isEmojiSelectorExpanded = false // Auto-minimize on choice
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = emoji,
                                                        fontSize = 22.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Gender Selection (swaps pre-recorded male/female voice packs)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Arabic Voice Pack Gender:",
                        fontSize = 13.sp,
                        color = Slate400
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = selectedGender == "Male",
                            onClick = { selectedGender = "Male" },
                            label = { Text("Male") },
                            leadingIcon = if (selectedGender == "Male") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Sky400,
                                selectedLabelColor = Slate900,
                                selectedLeadingIconColor = Slate900,
                                containerColor = Slate800,
                                labelColor = Slate400
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedGender == "Female",
                            onClick = { selectedGender = "Female" },
                            label = { Text("Female") },
                            leadingIcon = if (selectedGender == "Female") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Sky400,
                                selectedLabelColor = Slate900,
                                selectedLeadingIconColor = Slate900,
                                containerColor = Slate800,
                                labelColor = Slate400
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.createProfile(newName.trim(), selectedGender, selectedEmoji)
                            newName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Sky400),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Child Profile", color = Slate900, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── PROFILE LIST ──
        Text(
            text = "Enrolled Profiles (Tap to activate)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No child profiles created yet.\nUse the form above to add a profile.",
                    color = Slate400,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isActive = activeProfile?.id == profile.id
                    // Tapping a profile card triggers an atomic database query to clear other active profiles
                    // and select this specific profile ID, immediately shifting flatMapLatest queries for intents.
                    ProfileItem(
                        profile = profile,
                        isActive = isActive,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onToggleGender = { viewModel.updateProfileGender(profile, if (profile.gender == "Male") "Female" else "Male") }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileItem(
    profile: ChildProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onToggleGender: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) SelectedActiveBlue else Slate800
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular avatar icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Sky400.copy(alpha = 0.15f) else Slate700),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.avatarEmoji,
                        fontSize = 24.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SuccessGreen)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Voice Pack: ${profile.gender}",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Swap Voice Pack button
                IconButton(onClick = onToggleGender) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Toggle Voice Pack",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete profile button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Profile",
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──

private class MockProfileViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(
        listOf(
            ChildProfile(id = 1, name = "Adam", gender = "Male", isActive = true),
            ChildProfile(id = 2, name = "Jasmine", gender = "Female", isActive = false)
        )
    )
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(ChildProfile(id = 1, name = "Adam", gender = "Male", isActive = true))
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Profile Screen Preview")
@Composable
fun ProfileScreenPreview() {
    VoxaTheme {
        ProfileScreen(viewModel = MockProfileViewModel(), onBack = {})
    }
}

