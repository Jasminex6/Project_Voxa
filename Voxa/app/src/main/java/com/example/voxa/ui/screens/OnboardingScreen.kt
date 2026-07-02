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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    val pagerState = rememberPagerState(pageCount = { 4 })

    // Child profile setup state
    var childName by remember { mutableStateOf("") }
    var childGender by remember { mutableStateOf("Male") }
    var selectedAvatar by remember { mutableStateOf("👦") }

    val avatars = listOf("👦", "👧", "👶", "🦁", "🐼", "🦊", "🤖", "⭐")

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
            // Top Bar / Skip (Optional)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 3) {
                    Text(
                        text = "Skip",
                        color = Slate400,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(3)
                                }
                            }
                            .padding(8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingConceptPage()
                    2 -> OnboardingPermissionsPage()
                    3 -> OnboardingProfilePage(
                        childName = childName,
                        onNameChange = { childName = it },
                        childGender = childGender,
                        onGenderChange = { childGender = it },
                        selectedAvatar = selectedAvatar,
                        onAvatarSelect = { selectedAvatar = it },
                        avatars = avatars
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
                    repeat(4) { index ->
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
                            if (pagerState.currentPage < 3) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                // Final Page: Submit profile and complete onboarding
                                if (childName.isNotBlank()) {
                                    viewModel.createProfile(
                                        name = childName,
                                        gender = childGender,
                                        avatarEmoji = selectedAvatar
                                    )
                                    onOnboardingCompleted()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Sky400,
                            disabledContainerColor = Sky400.copy(alpha = 0.5f)
                        ),
                        enabled = pagerState.currentPage < 3 || childName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 3) "Get Started" else "Next",
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
fun OnboardingPermissionsPage() {
    val context = LocalContext.current

    // Helper functions to check active permission states
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
    var isSmsGranted by remember { mutableStateOf(isPermissionGranted(Manifest.permission.SEND_SMS)) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isMicGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isNotifGranted = it }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isLocationGranted = it }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isSmsGranted = it }

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
                onClick = { if (!isMicGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            // Notifications Permission Item (Tiramisu+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Notifications (Required)",
                    description = "To run background speech listening service",
                    isGranted = isNotifGranted,
                    onClick = { if (!isNotifGranted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            // Location Permission Item
            PermissionItem(
                title = "GPS Location (Optional)",
                description = "To find child and send caregiver coordinates",
                isGranted = isLocationGranted,
                onClick = { if (!isLocationGranted) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
            )

            // SMS Permission Item
            PermissionItem(
                title = "SMS Fallback (Optional)",
                description = "To send emergency alerts via SMS text",
                isGranted = isSmsGranted,
                onClick = { if (!isSmsGranted) smsLauncher.launch(Manifest.permission.SEND_SMS) }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            avatars.forEach { avatar ->
                val isSelected = selectedAvatar == avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
