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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.*
import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import com.example.voxa.R

@Composable
fun ProfileScreen(viewModel: IVoxaViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val profiles by viewModel.allProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    var newName by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
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
            val isRtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = if (isRtl) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.profile_settings_title),
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
                    text = stringResource(R.string.add_profile_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.child_name_label), color = Slate400) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Sky400,
                        unfocusedBorderColor = Slate600
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Avatar Emoji Selector
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
                                text = stringResource(R.string.selected_avatar_label),
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
                            text = if (isEmojiSelectorExpanded) stringResource(R.string.hide_emoji_btn) else stringResource(R.string.choose_emoji_btn),
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
                                    text = stringResource(R.string.tap_avatar_to_select),
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
                                                            isEmojiSelectorExpanded = false
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

                // Gender Selection
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.voice_pack_gender_label),
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
                            label = { Text(stringResource(R.string.male)) },
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
                            label = { Text(stringResource(R.string.female)) },
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
                    Text(stringResource(R.string.add_child_profile_btn), color = Slate900, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── PROFILE LIST ──
        Text(
            text = stringResource(R.string.enrolled_profiles_title),
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
                    text = stringResource(R.string.no_profiles_created),
                    color = Slate400,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isActive = activeProfile?.id == profile.id
                    ProfileItem(
                        profile = profile,
                        isActive = isActive,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onToggleGender = { viewModel.updateProfileGender(profile, if (profile.gender == "Male") "Female" else "Male") },
                        onTestVoice = { viewModel.testTtsVoice(profile.gender) }
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
    onToggleGender: () -> Unit,
    onTestVoice: () -> Unit
) {
    val context = LocalContext.current
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
                                    text = stringResource(R.string.active_badge),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    val genderText = when (profile.gender) {
                        "Male" -> stringResource(R.string.male)
                        "Female" -> stringResource(R.string.female)
                        else -> profile.gender
                    }
                    Text(
                        text = stringResource(R.string.voice_pack_format, genderText),
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onToggleGender) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.toggle_voice_pack_desc),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onTestVoice) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test Voice",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_profile_desc),
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
