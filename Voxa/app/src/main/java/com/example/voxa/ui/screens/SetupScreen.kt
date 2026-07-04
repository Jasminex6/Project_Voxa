package com.example.voxa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.EmergencyContactPrefs
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*

/**
 * 🚀 SetupScreen
 * Mandatory first-launch onboarding screen shown before the main app.
 * Collects:
 * 1. Child Info — name, avatar emoji, voice pack gender (mirrors ProfileScreen form)
 * 2. Emergency Contact — name, relation to child, phone number
 * 3. Emergency Message — pre-filled default, editable
 *
 * All fields are required. The "Complete Setup" button is only enabled when every field is filled.
 * On submit: creates the child profile via ViewModel, saves emergency data to SharedPreferences,
 * and marks setup as complete so this screen won't show again.
 */
@Composable
fun SetupScreen(
    viewModel: IVoxaViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ── Child Info State ──
    var childName by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
    var selectedEmoji by remember { mutableStateOf("👦") }
    var isEmojiSelectorExpanded by remember { mutableStateOf(false) }

    // ── Emergency Contact State ──
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactRelation by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }

    // ── Emergency Message State ──
    var emergencyMessage by remember { mutableStateOf("") }

    // Validation: all fields must be non-blank
    val isFormValid = childName.isNotBlank() &&
            emergencyContactName.isNotBlank() &&
            emergencyContactRelation.isNotBlank() &&
            emergencyContactPhone.isNotBlank() &&
            emergencyMessage.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── HEADER ──
            Text(
                text = "🚀",
                fontSize = 48.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Welcome to Voxa",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Let's set up your child's profile and\nemergency contact information.",
                fontSize = 14.sp,
                color = Slate400,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ══════════════════════════════════════
            // SECTION 1: CHILD INFO
            // ══════════════════════════════════════
            SectionHeader(emoji = "👦", title = "Child Information")
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Child Name
                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it },
                        label = { Text("Child's Name *", color = Slate400) },
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
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
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════
            // SECTION 2: EMERGENCY CONTACT
            // ══════════════════════════════════════
            SectionHeader(emoji = "🆘", title = "Emergency Contact")
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = emergencyContactName,
                        onValueChange = { emergencyContactName = it },
                        label = { Text("Contact Name *", color = Slate400) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate600
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = emergencyContactRelation,
                        onValueChange = { emergencyContactRelation = it },
                        label = { Text("Relation with Child *", color = Slate400) },
                        placeholder = { Text("e.g. Mother, Father, Guardian", color = Slate600) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate600
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = emergencyContactPhone,
                        onValueChange = { emergencyContactPhone = it },
                        label = { Text("Phone Number *", color = Slate400) },
                        placeholder = { Text("0xxx xxx xxxx", color = Slate600) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate600
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════
            // SECTION 3: EMERGENCY MESSAGE
            // ══════════════════════════════════════
            SectionHeader(emoji = "✉️", title = "Emergency Message")
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This message will be sent when you trigger the emergency SMS action.",
                        fontSize = 12.sp,
                        color = Slate400,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = emergencyMessage,
                        onValueChange = { emergencyMessage = it },
                        label = { Text("Emergency SMS Message *", color = Slate400) },
                        minLines = 1,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate600
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── SUBMIT BUTTON ──
            Button(
                onClick = {
                    // 1. Create child profile via ViewModel
                    viewModel.createProfile(childName.trim(), selectedGender, selectedEmoji)

                    // 2. Save emergency contact to SharedPreferences
                    EmergencyContactPrefs.saveEmergencyContact(
                        context = context,
                        name = emergencyContactName.trim(),
                        relation = emergencyContactRelation.trim(),
                        phone = emergencyContactPhone.trim(),
                        message = emergencyMessage.trim()
                    )

                    // 3. Mark first-time setup as complete
                    EmergencyContactPrefs.markSetupComplete(context)

                    // 4. Navigate to main app
                    onSetupComplete()
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Sky400,
                    disabledContainerColor = Slate700
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (isFormValid) "Complete Setup ✓" else "Fill all fields to continue",
                    color = if (isFormValid) Slate900 else Slate400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "All fields are required. You can edit these later from the Voxa Hub menu.",
                fontSize = 11.sp,
                color = Slate400,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Reusable section header with emoji icon and title text, matching VoxaHub styling.
 */
@Composable
private fun SectionHeader(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = emoji,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Sky400
        )
    }
}
