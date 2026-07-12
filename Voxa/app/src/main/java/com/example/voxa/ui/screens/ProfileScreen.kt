package com.example.voxa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.voxa.R
import com.example.voxa.data.*
import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share


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
    val enrolledIntents by viewModel.enrolledIntents.collectAsState()
    var showShareSheet by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    
    // Arabic voice pack gender: determines whether translation audio clips playBoy (Male) or Girl (Female) pre-recorded samples.
    var selectedGender by remember { mutableStateOf("Male") } // Defaults to Male child voice pack
    var selectedEmoji by remember { mutableStateOf("👦") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
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
                text = stringResource(id = R.string.profile_title),
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
                    text = stringResource(id = R.string.profile_add_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(id = R.string.profile_name_label), color = Slate400) },
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
                                text = stringResource(id = R.string.profile_avatar_selected),
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
                            text = if (isEmojiSelectorExpanded) stringResource(id = R.string.profile_emoji_hide) else stringResource(id = R.string.profile_emoji_show),
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
                                    text = stringResource(id = R.string.profile_emoji_grid_title),
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
                        text = stringResource(id = R.string.profile_voice_pack_gender),
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
                            label = { Text(stringResource(id = R.string.profile_male)) },
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
                            label = { Text(stringResource(id = R.string.profile_female)) },
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
                            viewModel.createProfile(newName.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }, selectedGender, selectedEmoji)
                            newName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Sky400),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(id = R.string.btn_add_profile), color = Slate900, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── PROFILE LIST ──
        Text(
            text = stringResource(id = R.string.profile_enrolled_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.profile_empty),
                    color = Slate400,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { profile ->
                    val isActive = activeProfile?.id == profile.id
                    // Tapping a profile card triggers an atomic database query to clear other active profiles
                    // and select this specific profile ID, immediately shifting flatMapLatest queries for intents.
                    ProfileItem(
                        profile = profile,
                        isActive = isActive,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onToggleGender = { viewModel.updateProfileGender(profile, if (profile.gender == "Male") "Female" else "Male") },
                        onShare = { showShareSheet = true }
                    )
                }
            }
        }
    }

    if (showShareSheet && activeProfile != null) {
        ShareProfileBottomSheet(
            profile = activeProfile!!,
            intents = enrolledIntents,
            onDismiss = { showShareSheet = false }
        )
    }
}

@Composable
fun ProfileItem(
    profile: ChildProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onToggleGender: () -> Unit,
    onShare: () -> Unit
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
                        .background(if (isActive) Sky400.copy(alpha = 0.15f) else Slate700)
                        .then(
                            if (isActive) Modifier.border(2.dp, SuccessGreen, CircleShape)
                            else Modifier
                        ),
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
                            text = profile.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.voice_pack_label, if (profile.gender == "Male") stringResource(R.string.profile_male) else stringResource(R.string.profile_female)),
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Share Profile button (Only for active profile to keep it clean)
                if (isActive) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Profile",
                            tint = Sky400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Swap Voice Pack button
                IconButton(onClick = onToggleGender) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = stringResource(id = R.string.btn_toggle_voice_pack),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete profile button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.btn_delete_profile),
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
    override val practiceStats = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.PracticeStats>())
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
    override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Profile Screen Preview")
@Composable
fun ProfileScreenPreview() {
    VoxaTheme {
        ProfileScreen(viewModel = MockProfileViewModel(), onBack = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareProfileBottomSheet(
    profile: ChildProfile,
    intents: List<com.example.voxa.data.EnrolledIntent>,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharingManager = remember { com.example.voxa.utils.ProfileSharingManager(context) }
    val sharingState by sharingManager.sharingState.collectAsState()

    val permissions = mutableListOf(
        android.Manifest.permission.BLUETOOTH_ADVERTISE,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            sharingManager.startDiscovering()
        } else {
            android.widget.Toast.makeText(context, "Permissions required for sharing", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Start discovering as soon as the sheet opens
    LaunchedEffect(Unit) {
        val hasAllPermissions = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (hasAllPermissions) {
            sharingManager.startDiscovering()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Stop discovering when dismissed
    DisposableEffect(Unit) {
        onDispose {
            sharingManager.disconnect()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Share ${profile.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }}'s Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.example.voxa.R.string.profile_looking_for_nearby),
                fontSize = 14.sp,
                color = Slate400,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (val state = sharingState) {
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Idle,
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Discovering -> {
                    CircularProgressIndicator(color = Sky400)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.example.voxa.R.string.profile_searching), color = Sky400)
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.EndpointFound -> {
                    Text(stringResource(com.example.voxa.R.string.profile_found, state.name), color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(color = SuccessGreen)
                    Text(stringResource(com.example.voxa.R.string.profile_connecting_auto), color = Slate400)
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Connecting -> {
                    CircularProgressIndicator(color = Sky400)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.example.voxa.R.string.profile_connecting_to, state.name), color = Slate400)
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Connected -> {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.example.voxa.R.string.profile_connected_sending), color = SuccessGreen)
                    
                    // Trigger send
                    LaunchedEffect(Unit) {
                        val gson = com.google.gson.Gson()
                        val caregiverName = com.example.voxa.data.EmergencyContactPrefs.getContactName(context)
                        val caregiverPhone = com.example.voxa.data.EmergencyContactPrefs.getContactPhone(context)
                        val caregiverRelation = com.example.voxa.data.EmergencyContactPrefs.getContactRelation(context)
                        val dto = mapOf(
                            "profile" to profile,
                            "intents" to intents,
                            "caregiverName" to caregiverName,
                            "caregiverPhone" to caregiverPhone,
                            "caregiverRelation" to caregiverRelation
                        )
                        val json = gson.toJson(dto)
                        val pcmFiles = intents.map { java.io.File(context.cacheDir, it.audioAssetPath) }
                        sharingManager.sendProfileData(json, pcmFiles)
                    }
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Transferring -> {
                    CircularProgressIndicator(color = Sky400)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.example.voxa.R.string.profile_transferring), color = Sky400)
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.TransferComplete -> {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.example.voxa.R.string.profile_shared_success), color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Error -> {
                    Text(stringResource(com.example.voxa.R.string.profile_error, state.message), color = ErrorRed)
                }
                is com.example.voxa.utils.ProfileSharingManager.SharingState.Advertising -> {
                    // Receiver mode, not used here
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800)
            ) {
                Text(stringResource(com.example.voxa.R.string.profile_cancel), color = Color.White)
            }
        }
    }
}

