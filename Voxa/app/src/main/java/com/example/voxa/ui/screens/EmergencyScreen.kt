package com.example.voxa.ui.screens

import android.content.Intent
import android.net.Uri
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.math.PI
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import com.example.voxa.utils.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 🆘 EmergencyScreen
 * Bilingual emergency operations screen. Features quick dial triggers,
 * SMS launch configurations, caregiver configurations, and a hold-to-activate siren alarm.
 */
@Composable
fun EmergencyScreen(viewModel: IVoxaViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var isHoldingAlarm by remember { mutableStateOf(false) }
    var alarmProgress by remember { mutableStateOf(0f) }
    var isAlarmActive by remember { mutableStateOf(false) }
    var ringtonePlayer: MediaPlayer? by remember { mutableStateOf(null) }

    var showEditDialog by remember { mutableStateOf(false) }

    // Siren alarm holder coroutine loop
    LaunchedEffect(isHoldingAlarm) {
        if (isHoldingAlarm) {
            val startTime = System.currentTimeMillis()
            while (isHoldingAlarm && alarmProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                alarmProgress = (elapsed.toFloat() / 2000f).coerceIn(0f, 1f)
                delay(16) // ~60 FPS update rhythm
            }
            if (alarmProgress >= 1f) {
                isAlarmActive = true
                Toast.makeText(context, "Emergency Siren Activated!", Toast.LENGTH_SHORT).show()
                viewModel.addLogSystemEvent("🆘 Emergency Siren Activated")
                
                val resId = context.resources.getIdentifier("emergency_siren", "raw", context.packageName)
                if (resId != 0) {
                    try {
                        ringtonePlayer = MediaPlayer.create(context, resId)?.apply {
                            isLooping = true
                            start()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        SirenPlayer.start()
                    }
                } else {
                    SirenPlayer.start()
                }
            }
        } else {
            alarmProgress = 0f
            if (isAlarmActive) {
                isAlarmActive = false
                viewModel.addLogSystemEvent("🆘 Emergency Siren Paused")
                
                if (ringtonePlayer != null) {
                    try {
                        ringtonePlayer?.stop()
                        ringtonePlayer?.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    ringtonePlayer = null
                } else {
                    SirenPlayer.stop()
                }
                Toast.makeText(context, "Siren Stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SirenPlayer.stop()
            try {
                ringtonePlayer?.stop()
                ringtonePlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Redesigned Header section
        Text(
            text = "Emergency Actions",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Quick access to distress triggers and caregiver alerts.",
            fontSize = 13.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        // ⚙️ Caregiver Setup Card
        val profile = activeProfile
        val hasCaregivers = profile != null && (profile.caregiverPhone1.isNotBlank() || profile.caregiverPhone2.isNotBlank() || profile.caregiverPhone3.isNotBlank())
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, if (hasCaregivers) Sky400.copy(alpha = 0.2f) else Slate700, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 Registered Caregivers",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Caregiver Contacts",
                            tint = Sky400
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (profile == null) {
                    Text(
                        text = "Please create a child profile first.",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CaregiverPhoneRow(label = "Primary Caregiver", phone = profile.caregiverPhone1)
                        CaregiverPhoneRow(label = "Backup Caregiver 1", phone = profile.caregiverPhone2)
                        CaregiverPhoneRow(label = "Backup Caregiver 2", phone = profile.caregiverPhone3)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 📍 Send Location Alert Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (profile != null) {
                            coroutineScope.launch {
                                Toast.makeText(context, "Locating child and sending alerts...", Toast.LENGTH_SHORT).show()
                                LocationHelper.dispatchEmergencyAlert(context, profile) { log ->
                                    viewModel.addLogSystemEvent(log)
                                }
                            }
                        } else {
                            Toast.makeText(context, "No active child profile found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .border(1.dp, Sky400.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Sky400.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShareLocation,
                                contentDescription = null,
                                tint = Sky400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Send Location Alert",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Sky400
                            )
                            Text(
                                text = "Send current GPS coordinates link to caregivers",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Slate400.copy(alpha = 0.5f)
                    )
                }
            }

            // 📞 Direct Call Caregiver Card (Fallback Chain)
            if (profile != null && profile.caregiverPhone1.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${profile.caregiverPhone1}"))
                                context.startActivity(intent)
                                viewModel.addLogSystemEvent("📞 Dialing primary caregiver: ${profile.caregiverPhone1}")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to launch phone dialer", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate700),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Call Primary Caregiver",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Direct call: ${profile.caregiverPhone1}",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Slate400.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // 🚨 Emergency Call Card (Public)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            context.startActivity(intent)
                            viewModel.addLogSystemEvent("🆘 Launched public emergency dialer (112).")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch phone dialer", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .border(1.dp, Color(0xFFBB0112).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFBB0112).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalPolice,
                                contentDescription = null,
                                tint = Color(0xFFFFB4AB),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Call Emergency Services (112)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB4AB)
                            )
                            Text(
                                text = "Instantly dial public emergency services",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Slate400.copy(alpha = 0.5f)
                    )
                }
            }

            // 🚨 Emergency Alarm (Hold to Activate) Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                isHoldingAlarm = true
                                waitForUpOrCancellation()
                                isHoldingAlarm = false
                            }
                        }
                    }
                    .border(
                        1.dp,
                        if (isAlarmActive) Color(0xFFF59E0B) else Slate700,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Hold progress fill bar at the bottom
                    if (alarmProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(alarmProgress)
                                .height(4.dp)
                                .background(Color(0xFFF59E0B))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isAlarmActive) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                    else Slate700
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isAlarmActive) Color(0xFFF59E0B) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Emergency Siren",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isAlarmActive) "Release to mute siren" else "Hold for 2 seconds to play emergency siren",
                                fontSize = 12.sp,
                                color = if (isAlarmActive) Color(0xFFF59E0B) else Slate400
                            )
                        }
                    }
                }
            }
        }
    }

    val profile = activeProfile
    // 📋 Caregiver Edit Dialog
    if (showEditDialog && profile != null) {
        var phone1 by remember { mutableStateOf(profile.caregiverPhone1) }
        var phone2 by remember { mutableStateOf(profile.caregiverPhone2) }
        var phone3 by remember { mutableStateOf(profile.caregiverPhone3) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "Configure Caregiver Chain",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            containerColor = Slate800,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Register up to 3 caregiver contact numbers. Voxa will notify them sequentially in case of emergency.",
                        color = Slate400,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = phone1,
                        onValueChange = { phone1 = it },
                        label = { Text("Primary Caregiver", color = Slate400) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate700
                        )
                    )
                    OutlinedTextField(
                        value = phone2,
                        onValueChange = { phone2 = it },
                        label = { Text("Backup Caregiver 1", color = Slate400) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate700
                        )
                    )
                    OutlinedTextField(
                        value = phone3,
                        onValueChange = { phone3 = it },
                        label = { Text("Backup Caregiver 2", color = Slate400) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Sky400,
                            unfocusedBorderColor = Slate700
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCaregiverPhones(phone1, phone2, phone3)
                        showEditDialog = false
                        Toast.makeText(context, "Caregiver contacts updated!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Sky400)
                ) {
                    Text("Save", color = Slate900, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showEditDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }
}

@Composable
fun CaregiverPhoneRow(label: String, phone: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Slate400,
            fontSize = 12.sp
        )
        Text(
            text = if (phone.isNotBlank()) phone else "Not Registered",
            color = if (phone.isNotBlank()) Color.White else Slate500,
            fontSize = 12.sp,
            fontWeight = if (phone.isNotBlank()) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// 🔊 Custom Synthesized Sine Wave Warning Siren alert generator
private object SirenPlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthThread: Thread? = null

    fun start() {
        if (isPlaying) return
        isPlaying = true
        synthThread = Thread {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
 
                audioTrack?.play()

                val buffer = ShortArray(1024)
                var phase = 0.0
                var samplesGenerated = 0L

                while (isPlaying) {
                    val cycleTime = samplesGenerated.toDouble() / sampleRate
                    val freq = 725.0 + 125.0 * sin(2.0 * PI * 1.6 * cycleTime)
                    
                    for (i in buffer.indices) {
                        val t = (samplesGenerated + i).toDouble() / sampleRate
                        val f = 725.0 + 125.0 * sin(2.0 * PI * 1.6 * t)
                        phase += 2.0 * PI * f / sampleRate
                        if (phase > 2.0 * PI) {
                            phase -= 2.0 * PI
                        }
                        buffer[i] = (sin(phase) * 12000.0).toInt().toShort()
                    }
                    
                    audioTrack?.write(buffer, 0, buffer.size)
                    samplesGenerated += buffer.size
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        synthThread?.start()
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        try {
            synthThread?.join(500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        synthThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}
