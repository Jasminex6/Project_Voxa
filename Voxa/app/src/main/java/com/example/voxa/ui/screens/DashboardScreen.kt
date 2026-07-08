package com.example.voxa.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.R
import com.example.voxa.ui.LogEvent
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: IVoxaViewModel, onNavigateToProfile: () -> Unit) {
    val isListening by viewModel.isListening.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()
    val volumeLevel by viewModel.volumeLevel.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

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
    val defaultCaregiverName = stringResource(R.string.dashboard_default_caregiver_name)
    var caregiverName by remember { mutableStateOf("") }
    LaunchedEffect(defaultCaregiverName) {
        if (caregiverName.isEmpty() || caregiverName == "Parent / Caregiver" || caregiverName == "ولي الأمر / مقدم الرعاية") {
            caregiverName = defaultCaregiverName
        }
    }
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
                    val msg = context.getString(R.string.dashboard_profile_imported)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    val msg = context.getString(R.string.dashboard_profile_import_failed, error)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                    Text(
                        text = activeProfile?.avatarEmoji ?: "👦",
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeProfile?.name ?: stringResource(R.string.dashboard_active_profile_none),
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

            // ── PULSING LISTENING CONTROL (Remains Centered) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(rippleScale1)
                            .clip(CircleShape)
                            .background(Color(0xFF00F2FE).copy(alpha = rippleAlpha1))
                    )
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
                text = stringResource(R.string.dashboard_live_translation),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Sky400,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = if (isRtl) TextAlign.End else TextAlign.Start
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
                    horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (lastVocalEvent == null || !isListening) {
                        Text(
                            text = if (isListening) stringResource(R.string.dashboard_listening) else stringResource(R.string.dashboard_ready),
                            color = Slate300,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
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
                            text = if (isListening) stringResource(R.string.dashboard_waiting) else stringResource(R.string.dashboard_tap_to_start),
                            color = Slate400,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                        )
                    } else if (lastVocalEvent.isMatch) {
                        Text(
                            text = lastVocalEvent.phrase,
                            color = SuccessGreen,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dashboard_translated_label, lastVocalEvent.word),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_confidence_label, (lastVocalEvent.confidence * 100).toInt()),
                            color = Slate300,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.dashboard_unrecognized),
                            color = ErrorRed,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastVocalEvent.detail,
                            color = Slate300,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
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
                    text = stringResource(R.string.dashboard_activity_timeline),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
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
                        text = stringResource(R.string.dashboard_no_logs),
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

        // ── SIDEBAR DRAWER OVERLAY (Aligns dynamically for LTR/RTL) ──
        if (isSidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { isSidebarOpen = false }
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .align(if (isRtl) Alignment.CenterStart else Alignment.CenterEnd)
                    .background(Slate800)
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_menu_title),
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
                            text = "👦 " + stringResource(R.string.dashboard_child_info),
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
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                                ) {
                                    Text(
                                        text = activeProfile?.name ?: stringResource(R.string.dashboard_active_profile_none),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                                    )
                                    val genderText = when (activeProfile?.gender) {
                                        "Male" -> stringResource(R.string.male)
                                        "Female" -> stringResource(R.string.female)
                                        else -> activeProfile?.gender ?: ""
                                    }
                                    Text(
                                        text = if (activeProfile != null) stringResource(R.string.voice_pack_format, genderText) else "",
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = if (isRtl) TextAlign.End else TextAlign.Start
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
                            Text(stringResource(R.string.dashboard_manage_profiles), color = Slate900, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 👨‍👩‍👧 Caregivers Info Section
                        Text(
                            text = "👨‍👩‍👧 " + stringResource(R.string.dashboard_caregivers_info),
                            color = Sky400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate900),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                            ) {
                                Text(
                                    text = caregiverName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.caregiver_phone_label) + ": $caregiverPhone",
                                    color = Slate300,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_edit_caregiver),
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
                            text = stringResource(R.string.dashboard_settings),
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
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_app_lang, ""),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isEn = appLanguage == "en"
                                    FilterChip(
                                        selected = isEn,
                                        onClick = {
                                            viewModel.setAppLanguage("en")
                                            (context as? android.app.Activity)?.recreate()
                                        },
                                        label = { Text("English", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Sky400,
                                            selectedLabelColor = Slate900,
                                            containerColor = Slate800,
                                            labelColor = Slate400
                                        )
                                    )
                                    FilterChip(
                                        selected = !isEn,
                                        onClick = {
                                            viewModel.setAppLanguage("ar")
                                            (context as? android.app.Activity)?.recreate()
                                        },
                                        label = { Text("العربية", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Sky400,
                                            selectedLabelColor = Slate900,
                                            containerColor = Slate800,
                                            labelColor = Slate400
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_listening_status, if (isListening) stringResource(R.string.status_on) else stringResource(R.string.status_off)),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 💾 Data Management Section
                        Text(
                            text = stringResource(R.string.dashboard_data_mgmt),
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
                                    text = stringResource(R.string.dashboard_export),
                                    color = Sky400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        viewModel.exportProfileData(context)
                                    }
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_import),
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        importLauncher.launch("application/json")
                                    }
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_clear_logs),
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        viewModel.clearLogs()
                                        val msg = context.getString(R.string.dashboard_timeline_cleared)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // Version Tag
                    Text(
                        text = stringResource(R.string.dashboard_version, "v1.12.0"),
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
                title = { Text(stringResource(R.string.dashboard_edit_caregiver_title), color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = Slate800,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text(stringResource(R.string.dashboard_name), color = Slate400) },
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
                            label = { Text(stringResource(R.string.dashboard_phone), color = Slate400) },
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
                        Text(stringResource(R.string.save_btn), color = Slate900, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCaregiverEditDialog = false }) {
                        Text(stringResource(R.string.cancel_btn), color = Slate400)
                    }
                }
            )
        }
    }
}

private val systemEventResourceMap = mapOf(
    "log_listening_paused" to R.string.log_listening_paused,
    "log_listening_active" to R.string.log_listening_active,
    "log_profile_imported" to R.string.log_profile_imported,
    "log_siren_activated" to R.string.log_siren_activated,
    "log_siren_paused" to R.string.log_siren_paused,
    "log_sms_sent" to R.string.log_sms_sent,
    "log_whatsapp_shared" to R.string.log_whatsapp_shared,
    "log_dialer_launched" to R.string.log_dialer_launched,
    "log_intent_enrolled" to R.string.log_intent_enrolled
)

fun resolveSystemEventText(context: Context, rawPhrase: String): String {
    val parts = rawPhrase.split("|")
    if (parts.isEmpty()) return rawPhrase
    val key = parts[0]
    val resId = systemEventResourceMap[key] ?: 0
    if (resId == 0) return rawPhrase

    return try {
        when (parts.size) {
            1 -> context.getString(resId)
            2 -> context.getString(resId, parts[1])
            3 -> {
                val arg2 = parts[2].toIntOrNull() ?: parts[2]
                context.getString(resId, parts[1], arg2)
            }
            else -> context.getString(resId)
        }
    } catch (e: Exception) {
        rawPhrase
    }
}

@Composable
fun TimelineItem(event: LogEvent) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = formatter.format(Date(event.timestamp))

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = BorderStroke(1.dp, Slate700),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // High-end vertical accent status bar
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
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isRtl) Arrangement.End else Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (event.word == "SYSTEM") stringResource(R.string.dashboard_system_update) else stringResource(R.string.dashboard_detected_vocal),
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
                                    text = stringResource(R.string.dashboard_confidence_label, (event.confidence * 100).toInt()),
                                    fontSize = 10.sp,
                                    color = if (event.isMatch) SuccessGreen else WarningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (event.word == "SYSTEM") {
                            resolveSystemEventText(androidx.compose.ui.platform.LocalContext.current, event.phrase)
                        } else {
                            event.phrase
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isRtl) TextAlign.End else TextAlign.Start
                    )
                    if (event.word != "SYSTEM") {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.dashboard_log_format, event.word, event.detail),
                            color = Slate400,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
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
    override val appLanguage = kotlinx.coroutines.flow.MutableStateFlow("en")
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
