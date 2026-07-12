package com.example.voxa.ui.screens

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val pagerState = rememberPagerState(pageCount = { 6 })

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

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
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
                    0 -> OnboardingLanguagePage(onLanguageSelected = { lang ->
                        val prefs = context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
                        prefs.edit().putString("app_language", lang).apply()
                        val intent = android.content.Intent(context, com.example.voxa.MainActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    })
                    1 -> OnboardingWelcomePage()
                    2 -> OnboardingConceptPage()
                    3 -> OnboardingPermissionsPage(
                        isMicGranted = isMicGranted,
                        isNotifGranted = isNotifGranted,
                        isLocationGranted = isLocationGranted,
                        onMicClick = { if (!isMicGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onNotifClick = { if (!isNotifGranted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onLocationClick = { if (!isLocationGranted) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                    4 -> OnboardingProfilePage(
                        childName = childName,
                        onNameChange = { childName = it },
                        childGender = childGender,
                        onGenderChange = { childGender = it },
                        selectedAvatar = selectedAvatar,
                        onAvatarSelect = { selectedAvatar = it },
                        avatars = avatars
                    )
                    5 -> OnboardingEmergencyPage(
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
                    repeat(6) { index ->
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
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
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
                                Text(stringResource(com.example.voxa.R.string.onboarding_back), color = Slate400, fontSize = 16.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(60.dp))
                        }


                    // Next/Get Started Button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 5) {
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
                        enabled = (pagerState.currentPage < 3) || 
                                 (pagerState.currentPage == 3 && allPermissionsGranted) || 
                                 (pagerState.currentPage == 4 && childName.isNotBlank()) || 
                                 (pagerState.currentPage == 5 && isEmergencyValid),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 5) stringResource(com.example.voxa.R.string.onboarding_get_started) else stringResource(com.example.voxa.R.string.onboarding_next),
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
            text = stringResource(com.example.voxa.R.string.onboarding_title_1),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.onboarding_desc_1),
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val layoutDirection = if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
        androidx.compose.ui.unit.LayoutDirection.Rtl
    } else {
        androidx.compose.ui.unit.LayoutDirection.Ltr
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(com.example.voxa.R.string.onboarding_title_2),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            val descLines = stringResource(com.example.voxa.R.string.onboarding_desc_2).split("\n")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                descLines.forEach { line ->
                    val prefixMatch = Regex("^[0-9١-٩]+\\.\\s").find(line)
                    if (prefixMatch != null) {
                        val prefix = prefixMatch.value
                        val rest = line.substring(prefix.length)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = prefix,
                                fontSize = 15.sp,
                                color = Slate300,
                                lineHeight = 26.sp
                            )
                            Text(
                                text = rest,
                                fontSize = 15.sp,
                                color = Slate300,
                                textAlign = TextAlign.Start,
                                lineHeight = 26.sp
                            )
                        }
                    } else {
                        Text(
                            text = line,
                            fontSize = 15.sp,
                            color = Slate300,
                            textAlign = TextAlign.Start,
                            lineHeight = 26.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
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
            text = stringResource(com.example.voxa.R.string.onboarding_title_3),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.onboarding_desc_3),
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
                title = stringResource(com.example.voxa.R.string.onboarding_mic_title),
                description = stringResource(com.example.voxa.R.string.onboarding_mic_desc),
                isGranted = isMicGranted,
                onClick = onMicClick
            )

            // Notifications Permission Item (Tiramisu+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = stringResource(com.example.voxa.R.string.onboarding_notif_title),
                    description = stringResource(com.example.voxa.R.string.onboarding_notif_desc),
                    isGranted = isNotifGranted,
                    onClick = onNotifClick
                )
            }

            // Location Permission Item
            PermissionItem(
                title = stringResource(com.example.voxa.R.string.onboarding_gps_title),
                description = stringResource(com.example.voxa.R.string.onboarding_gps_desc),
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
            text = stringResource(com.example.voxa.R.string.onboarding_title_4),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.onboarding_desc_4),
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Name input
        OutlinedTextField(
            value = childName,
            onValueChange = onNameChange,
            label = { Text(stringResource(com.example.voxa.R.string.onboarding_child_name), color = Slate400) },
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
                        text = if (gender == "Male") "👦 ${stringResource(com.example.voxa.R.string.profile_gender_male)}" else "👧 ${stringResource(com.example.voxa.R.string.profile_gender_female)}",
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
            text = stringResource(com.example.voxa.R.string.onboarding_avatar_title),
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

@OptIn(ExperimentalMaterial3Api::class)
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
            text = stringResource(com.example.voxa.R.string.onboarding_emergency_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.onboarding_emergency_desc),
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
                    label = { Text(stringResource(com.example.voxa.R.string.onboarding_contact_name), color = Slate400) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Sky400,
                        unfocusedBorderColor = Slate600
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                var expanded by remember { mutableStateOf(false) }
                val isCustomMode = remember { mutableStateOf(false) }
                val options = listOf("Mother", "Father", "Teacher", "Therapist", "Other")

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = contactRelation,
                        onValueChange = onRelationChange,
                        readOnly = !isCustomMode.value,
                        label = { Text(stringResource(com.example.voxa.R.string.onboarding_relation), color = Slate400) },
                        placeholder = { Text(stringResource(com.example.voxa.R.string.onboarding_relation_hint), color = Slate600) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate600
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Slate800)
                    ) {
                        options.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption, color = Color.White) },
                                onClick = {
                                    if (selectionOption == "Other") {
                                        isCustomMode.value = true
                                        onRelationChange("")
                                    } else {
                                        isCustomMode.value = false
                                        onRelationChange(selectionOption)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { 
                        val digits = it.filter { char -> char.isDigit() }
                        if (digits.length <= 11) {
                            onPhoneChange(digits)
                        }
                    },
                    label = { Text(stringResource(com.example.voxa.R.string.onboarding_phone_number), color = Slate400) },
                    placeholder = { Text(stringResource(com.example.voxa.R.string.onboarding_phone_hint), color = Slate600) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = contactPhone.isNotEmpty() && contactPhone.length < 11,
                    supportingText = {
                        if (contactPhone.isNotEmpty() && contactPhone.length < 11) {
                            Text(stringResource(com.example.voxa.R.string.dashboard_phone_error), color = MaterialTheme.colorScheme.error)
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
                    text = stringResource(com.example.voxa.R.string.onboarding_emergency_msg_desc),
                    fontSize = 12.sp,
                    color = Slate400,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = { Text(stringResource(com.example.voxa.R.string.onboarding_emergency_sms), color = Slate400) },
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

@Composable
fun OnboardingLanguagePage(onLanguageSelected: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
    val currentLang = prefs.getString("app_language", "en") ?: "en"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Globe icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Sky400.copy(alpha = 0.1f))
                .border(2.dp, Sky400.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🌍",
                fontSize = 56.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // English title
        Text(
            text = stringResource(com.example.voxa.R.string.onboarding_choose_lang),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Arabic title
        Text(
            text = stringResource(com.example.voxa.R.string.onboarding_choose_lang_ar),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Slate300,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable { onLanguageSelected("en") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (currentLang == "en") Slate700 else Slate800),
            border = if (currentLang == "en") BorderStroke(2.dp, Sky400) else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🇬🇧", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(com.example.voxa.R.string.onboarding_english), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLanguageSelected("ar") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (currentLang == "ar") Slate700 else Slate800),
            border = if (currentLang == "ar") BorderStroke(2.dp, Sky400) else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🇸🇦", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(com.example.voxa.R.string.onboarding_arabic), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
