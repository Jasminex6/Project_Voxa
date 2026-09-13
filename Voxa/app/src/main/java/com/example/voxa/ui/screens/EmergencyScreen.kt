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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
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
import com.example.voxa.data.EmergencyContactPrefs
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay

/**
 * 🆘 EmergencyScreen
 * Bilingual emergency operations screen. Features quick dial triggers,
 * SMS launch configurations, and a hold-to-activate siren alarm.
 */
@Composable
fun EmergencyScreen(viewModel: IVoxaViewModel) {
    val context = LocalContext.current
    var isHoldingAlarm by remember { mutableStateOf(false) }
    var alarmProgress by remember { mutableStateOf(0f) }
    var isAlarmActive by remember { mutableStateOf(false) }
    var ringtonePlayer: MediaPlayer? by remember { mutableStateOf(null) }

    // Load saved emergency contact data
    val emergencyPhone = "+2" + remember { EmergencyContactPrefs.getContactPhone(context) }
    val emergencyMessage = remember { EmergencyContactPrefs.getEmergencyMessage(context) }
    val emergencyContactName = remember { EmergencyContactPrefs.getContactName(context) }

    // Location services for current location sharing
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isFetchingLocation by remember { mutableStateOf(false) }

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EmergencyBigButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Call",
                icon = Icons.Default.Phone,
                color = Color(0xFF2196F3),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyPhone"))
                        context.startActivity(intent)
                        viewModel.addLogSystemEvent("🆘 Launched emergency dialer.")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to launch phone dialer", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            EmergencyBigButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "SMS",
                icon = Icons.Default.Mail,
                color = Color(0xFF4CAF50),
                onClick = {
                    try {
                        val smsPhone = emergencyPhone.ifBlank { "112" }
                        val smsBody = emergencyMessage.ifBlank { "Emergency alert: Please assist immediately." }
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$smsPhone")).apply {
                            putExtra("sms_body", smsBody)
                        }
                        context.startActivity(intent)
                        viewModel.addLogSystemEvent("🆘 Launched emergency SMS.")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to launch SMS app", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EmergencyBigButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = if (isFetchingLocation) "Getting..." else "Location",
                icon = Icons.Default.LocationOn,
                color = Color(0xFFFF9800),
                onClick = {
                    if (isFetchingLocation) return@EmergencyBigButton
                    isFetchingLocation = true
                    try {
                        fusedLocationClient
                            .getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token
                            )
                            .addOnSuccessListener { location ->
                                isFetchingLocation = false
                                if (location != null) {
                                    val lat = location.latitude
                                    val lng = location.longitude
                                    val mapsLink = "https://maps.google.com/?q=$lat,$lng"
                                    val smsPhone = emergencyPhone.ifBlank { "112" }
                                    val locationMsg = "Here is my current location:\n$mapsLink"
                                    try {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$smsPhone")).apply {
                                            putExtra("sms_body", locationMsg)
                                        }
                                        context.startActivity(intent)
                                        viewModel.addLogSystemEvent("🆘 Sent current location via SMS.")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to launch SMS", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                isFetchingLocation = false
                                Toast.makeText(context, "Location error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: SecurityException) {
                        isFetchingLocation = false
                        Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            EmergencyBigHoldButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = if (isAlarmActive) "Siren ON" else "Siren",
                icon = Icons.Default.NotificationsActive,
                color = Color(0xFFF44336),
                progress = alarmProgress,
                isActive = isAlarmActive,
                onHoldChange = { isHoldingAlarm = it }
            )
        }
    }
}

@Composable
fun EmergencyBigButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmergencyBigHoldButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    progress: Float,
    isActive: Boolean,
    onHoldChange: (Boolean) -> Unit
) {
    val bgColor = if (isActive) color.copy(alpha = 0.3f) else color.copy(alpha = 0.15f)
    val borderColor = if (isActive) color.copy(alpha = 0.8f) else color.copy(alpha = 0.3f)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown()
                        onHoldChange(true)
                        waitForUpOrCancellation()
                        onHoldChange(false)
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (progress > 0f && !isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(progress)
                        .background(color.copy(alpha = 0.3f))
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) Color.White else color,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
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
                    // Alternating warning frequency between 600Hz and 850Hz smoothly every 0.6 seconds
                    val freq = 725.0 + 125.0 * sin(2.0 * PI * 1.6 * cycleTime)
                    
                    for (i in buffer.indices) {
                        val t = (samplesGenerated + i).toDouble() / sampleRate
                        val f = 725.0 + 125.0 * sin(2.0 * PI * 1.6 * t)
                        phase += 2.0 * PI * f / sampleRate
                        if (phase > 2.0 * PI) {
                            phase -= 2.0 * PI
                        }
                        // Synthesize smooth wave at comfortable warning volume (12000 out of 32767)
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
