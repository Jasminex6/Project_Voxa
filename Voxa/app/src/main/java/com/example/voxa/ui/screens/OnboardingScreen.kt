package com.example.voxa.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voxa.data.EmergencyContactPrefs
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: IVoxaViewModel,
    onOnboardingCompleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    // Permission states
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    var isMicGranted by remember { mutableStateOf(isPermissionGranted(Manifest.permission.RECORD_AUDIO)) }
    var isNotifGranted by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        })
    }
    var isLocationGranted by remember { mutableStateOf(isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) }

    val allPermissionsGranted = isMicGranted && isNotifGranted && isLocationGranted

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isMicGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isNotifGranted = it }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isLocationGranted = it }

    // Child profile setup state
    var childName by remember { mutableStateOf("") }
    var childGender by remember { mutableStateOf("Male") }
    var selectedAvatar by remember { mutableStateOf("👦") }

    val avatars = listOf("👦", "👧", "👶", "🦸", "🥷", "🦄", "🐼", "🦊", "🦁", "🦖")

    // Emergency setup state
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactRelation by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var emergencyMessage by remember { mutableStateOf("") }

    val isEmergencyValid = emergencyContactName.isNotBlank() &&
            emergencyContactRelation.isNotBlank() &&
            emergencyContactPhone.length == 11 && emergencyContactPhone.all { it.isDigit() } &&
            emergencyMessage.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager Content
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingConceptPage()
                    2 -> OnboardingPermissionsPage(
                        isMicGranted = isMicGranted,
                        isNotifGranted = isNotifGranted,
                        isLocationGranted = isLocationGranted,
                        onMicClick = { if (!isMicGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onNotifClick = { if (!isNotifGranted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onLocationClick = { if (!isLocationGranted) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                    3 -> OnboardingProfilePage(
                        childName = childName,
                        onNameChange = { childName = it },
                        childGender = childGender,
                        onGenderChange = { childGender = it },
                        selectedAvatar = selectedAvatar,
                        onAvatarSelect = { selectedAvatar = it },
                        avatars = avatars
                    )
                    4 -> OnboardingEmergencyPage(
                        contactName = emergencyContactName,
                        onNameChange = { emergencyContactName = it },
                        contactRelation = emergencyContactRelation,
                        onRelationChange = { emergencyContactRelation = it },
                        contactPhone = emergencyContactPhone,
                        onPhoneChange = { emergencyContactPhone = it },
                        message = emergencyMessage,
                        onMessageChange = { emergencyMessage = it }
                    )
                }
            }

            // Bottom controls: Indicators and Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    repeat(5) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isSelected) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Sky400 else Slate600)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    if (pagerState.currentPage > 0) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Back", color = Slate400, fontSize = 16.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(60.dp))
                    }

                    // Next/Get Started Button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 4) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                // Final Page: Submit profile and complete onboarding
                                if (childName.isNotBlank() && isEmergencyValid) {
                                    viewModel.createProfile(
                                        name = childName.trim(),
                                        gender = childGender,
                                        avatarEmoji = selectedAvatar
                                    )
                                    EmergencyContactPrefs.saveEmergencyContact(
                                        context = context,
                                        name = emergencyContactName.trim(),
                                        relation = emergencyContactRelation.trim(),
                                        phone = emergencyContactPhone.trim(),
                                        message = emergencyMessage.trim()
                                    )
                                    EmergencyContactPrefs.markSetupComplete(context)
                                    onOnboardingCompleted()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Sky400,
                            disabledContainerColor = Sky400.copy(alpha = 0.5f)
                        ),
                        enabled = (pagerState.currentPage < 2) || 
                                 (pagerState.currentPage == 2 && allPermissionsGranted) || 
                                 (pagerState.currentPage == 3 && childName.isNotBlank()) || 
                                 (pagerState.currentPage == 4 && isEmergencyValid),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 4) "Get Started" else "Next",
                            color = Slate900,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👋 Welcome to Voxa",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "An inclusive, AI-powered communication tool designed to translate your child's unique vocalizations into clear spoken words and notifications.",
            fontSize = 16.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Sky400.copy(alpha = 0.1f))
                .border(2.dp, Sky400.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗣️",
                fontSize = 80.sp
            )
        }
    }
}

@Composable
fun OnboardingConceptPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✨ How It Works",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "1. Enroll custom vocal sounds or words that your child uses.\n2. Translate them into text and speech in real-time.\n3. Monitor voice signals locally & securely without internet.",
            fontSize = 15.sp,
            color = Slate300,
            textAlign = TextAlign.Start,
            lineHeight = 26.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Sky400.copy(alpha = 0.1f))
                .border(2.dp, Sky400.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🤖",
                fontSize = 80.sp
            )
        }
    }
}

@Composable
fun OnboardingPermissionsPage(
    isMicGranted: Boolean,
    isNotifGranted: Boolean,
    isLocationGranted: Boolean,
    onMicClick: () -> Unit,
    onNotifClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔒 Permissions Gate",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Please enable the following permissions to activate Voxa features. All calculations are performed on-device.",
            fontSize = 14.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mic Permission Item
            PermissionItem(
                title = "Microphone (Required)",
                description = "To capture and recognize child speech",
                isGranted = isMicGranted,
                onClick = onMicClick
            )

            // Notifications Permission Item (Tiramisu+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Notifications (Required)",
                    description = "To run background speech listening service",
                    isGranted = isNotifGranted,
                    onClick = onNotifClick
                )
            }

            // Location Permission Item
            PermissionItem(
                title = "GPS Location (Required)",
                description = "To find child and send caregiver coordinates",
                isGranted = isLocationGranted,
                onClick = onLocationClick
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isGranted) Sky400.copy(alpha = 0.3f) else Slate700,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Slate400
                )
            }
            Switch(
                checked = isGranted,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Slate900,
                    checkedTrackColor = Sky400,
                    uncheckedThumbColor = Slate400,
                    uncheckedTrackColor = Slate700
                )
            )
        }
    }
}

@Composable
fun OnboardingProfilePage(
    childName: String,
    onNameChange: (String) -> Unit,
    childGender: String,
    onGenderChange: (String) -> Unit,
    selectedAvatar: String,
    onAvatarSelect: (String) -> Unit,
    avatars: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👶 Child Setup",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set up your child's profile. Selecting the correct gender adapts the voice feedback pitch.",
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Name input
        OutlinedTextField(
            value = childName,
            onValueChange = onNameChange,
            label = { Text("Child's Name", color = Slate400) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Sky400,
                unfocusedBorderColor = Slate700
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Gender Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("Male", "Female").forEach { gender ->
                val isSelected = childGender == gender
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Sky400.copy(alpha = 0.2f) else Slate800)
                        .border(
                            1.dp,
                            if (isSelected) Sky400 else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onGenderChange(gender) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (gender == "Male") "👦 Boy" else "👧 Girl",
                        color = if (isSelected) Sky400 else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Avatar selector
        Text(
            text = "Pick an Avatar",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            avatars.chunked(5).forEach { rowAvatars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowAvatars.forEach { avatar ->
                        val isSelected = selectedAvatar == avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Sky400 else Slate800)
                                .clickable { onAvatarSelect(avatar) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = avatar, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingEmergencyPage(
    contactName: String,
    onNameChange: (String) -> Unit,
    contactRelation: String,
    onRelationChange: (String) -> Unit,
    contactPhone: String,
    onPhoneChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🆘 Emergency Contact",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set up who to contact in case of an emergency.",
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

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
                    value = contactName,
                    onValueChange = onNameChange,
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
                    value = contactRelation,
                    onValueChange = onRelationChange,
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
                    value = contactPhone,
                    onValueChange = { 
                        val digits = it.filter { char -> char.isDigit() }
                        if (digits.length <= 11) {
                            onPhoneChange(digits)
                        }
                    },
                    label = { Text("Phone Number *", color = Slate400) },
                    placeholder = { Text("0xxx xxx xxxx", color = Slate600) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = contactPhone.isNotEmpty() && contactPhone.length < 11,
                    supportingText = {
                        if (contactPhone.isNotEmpty() && contactPhone.length < 11) {
                            Text("Phone number must be exactly 11 digits", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Sky400,
                        unfocusedBorderColor = Slate600,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

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
                    value = message,
                    onValueChange = onMessageChange,
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
    }
}
