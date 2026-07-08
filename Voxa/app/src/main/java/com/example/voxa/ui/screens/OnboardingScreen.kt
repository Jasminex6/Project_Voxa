package com.example.voxa.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voxa.R
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: IVoxaViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    // Keep track of permission states
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var notificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var locationGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var smsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }

    // Permission launchers
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = it
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsGranted = it
    }

    // Profile Setup States
    var childName by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
    var selectedEmoji by remember { mutableStateOf("👦") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Slate900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal Pager for slides
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val appLanguage by viewModel.appLanguage.collectAsState()
                when (page) {
                    0 -> LanguageSelectionSlide(
                        currentLanguage = appLanguage,
                        onLanguageSelected = { lang ->
                            viewModel.setAppLanguage(lang)
                            (context as? android.app.Activity)?.recreate()
                        }
                    )
                    1 -> WelcomeSlide()
                    2 -> ConceptSlide()
                    3 -> PermissionsSlide(
                        micGranted = micGranted,
                        notificationGranted = notificationGranted,
                        locationGranted = locationGranted,
                        smsGranted = smsGranted,
                        onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onRequestNotif = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestLoc = { locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onRequestSms = { smsLauncher.launch(Manifest.permission.SEND_SMS) }
                    )
                    4 -> SetupSlide(
                        name = childName,
                        onNameChange = { childName = it },
                        gender = selectedGender,
                        onGenderChange = { selectedGender = it },
                        emoji = selectedEmoji,
                        onEmojiChange = { selectedEmoji = it }
                    )
                }
            }

            // Bottom Navigation indicators and controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Text(stringResource(R.string.back), color = Slate400, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(60.dp))
                }

                // Page Indicator Dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(5) { idx ->
                        val isSelected = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Sky400 else Slate700)
                        )
                    }
                }

                // Next / Finish Button
                if (pagerState.currentPage < 4) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky400)
                    ) {
                        Text(stringResource(R.string.next), color = Slate900, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (childName.isNotBlank()) {
                                // Save profile
                                viewModel.createProfile(childName.trim(), selectedGender, selectedEmoji)
                                // Save onboarding completed flag
                                val prefs = context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("is_first_boot_completed", true).apply()
                                onFinished()
                            }
                        },
                        enabled = childName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Sky400)
                    ) {
                        Text(stringResource(R.string.finish), color = Slate900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeSlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎙️", fontSize = 72.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            fontSize = 14.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun ConceptSlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🧠", fontSize = 72.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            text = stringResource(R.string.onboarding_concept_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_concept_desc),
            fontSize = 14.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun PermissionsSlide(
    micGranted: Boolean,
    notificationGranted: Boolean,
    locationGranted: Boolean,
    smsGranted: Boolean,
    onRequestMic: () -> Unit,
    onRequestNotif: () -> Unit,
    onRequestLoc: () -> Unit,
    onRequestSms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_permissions_desc),
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PermissionCard(
                title = stringResource(R.string.grant_mic_permission),
                isGranted = micGranted,
                onRequest = onRequestMic
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = stringResource(R.string.grant_notification_permission),
                    isGranted = notificationGranted,
                    onRequest = onRequestNotif
                )
            }
            PermissionCard(
                title = stringResource(R.string.grant_location_permission),
                isGranted = locationGranted,
                onRequest = onRequestLoc
            )
            PermissionCard(
                title = stringResource(R.string.grant_sms_permission),
                isGranted = smsGranted,
                onRequest = onRequestSms
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = BorderStroke(1.dp, if (isGranted) SuccessGreen.copy(alpha = 0.5f) else Slate700),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.granted),
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Sky400),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(stringResource(R.string.grant), color = Slate900, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SetupSlide(
    name: String,
    onNameChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    emoji: String,
    onEmojiChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_setup_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_setup_desc),
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Child name input
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar select
        var isEmojiSelectorExpanded by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate800)
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
                        text = emoji,
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
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val emojis = listOf("👦", "👧", "👶", "🦸", "🥷", "🦄", "🐼", "🦊", "🦁", "🦖")
                        val rows = emojis.chunked(5)
                        rows.forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowEmojis.forEach { item ->
                                    val isSelected = emoji == item
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Sky400.copy(alpha = 0.2f) else Color.Transparent)
                                            .border(width = 1.dp, color = if (isSelected) Sky400 else Slate700, shape = CircleShape)
                                            .clickable { 
                                                onEmojiChange(item)
                                                isEmojiSelectorExpanded = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = item, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gender/voice select
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = gender == "Male",
                onClick = { onGenderChange("Male") },
                label = { Text(stringResource(R.string.male)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Sky400,
                    selectedLabelColor = Slate900,
                    containerColor = Slate800,
                    labelColor = Slate400
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = gender == "Female",
                onClick = { onGenderChange("Female") },
                label = { Text(stringResource(R.string.female)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Sky400,
                    selectedLabelColor = Slate900,
                    containerColor = Slate800,
                    labelColor = Slate400
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LanguageSelectionSlide(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🌐", fontSize = 72.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            text = "Choose Your Language\nاختر اللغة",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can change this later in settings\nيمكنك تغيير هذا لاحقاً من الإعدادات",
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            LanguageButton(
                title = "English (US)",
                flag = "🇺🇸",
                isSelected = currentLanguage == "en",
                onClick = { onLanguageSelected("en") }
            )
            LanguageButton(
                title = "العربية (مصر)",
                flag = "🇪🇬",
                isSelected = currentLanguage == "ar",
                onClick = { onLanguageSelected("ar") }
            )
        }
    }
}

@Composable
fun LanguageButton(
    title: String,
    flag: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SelectedActiveBlue else Slate800
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Sky400 else Slate700
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = flag, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Sky400),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Slate900,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

