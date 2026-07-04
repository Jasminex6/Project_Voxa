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
            text = "Quick access to distress triggers.",
            fontSize = 13.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══════════════════════════════════════
            // 📱 SECTION 1: PHONE
            // ══════════════════════════════════════
            EmergencySectionHeader(emoji = "📱", title = "Phone", textColor = Sky400)

            // 📞 Phone Call Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyPhone"))
                            context.startActivity(intent)
                            viewModel.addLogSystemEvent("🆘 Launched emergency dialer.")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch phone dialer", Toast.LENGTH_SHORT).show()
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
                                .background(Sky400.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Sky400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Call Emergency Contact",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Instantly dial ${emergencyContactName.ifBlank { emergencyPhone }}",
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

            // ✉️ Phone SMS Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val smsPhone = emergencyPhone.ifBlank { "112" }
                            val smsBody = emergencyMessage.ifBlank { "Emergency alert: Please assist immediately." }
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$smsPhone")).apply {
                                putExtra("sms_body", smsBody)
                            }
                            context.startActivity(intent)
                            viewModel.addLogSystemEvent("\uD83C\uDD98 Launched emergency SMS to ${emergencyContactName.ifBlank { smsPhone }}.")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch SMS app", Toast.LENGTH_SHORT).show()
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
                                .background(Sky400.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = null,
                                tint = Sky400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Emergency SMS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Send SMS to ${emergencyContactName.ifBlank { "emergency contact" }}",
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

            Spacer(modifier = Modifier.height(4.dp))

            // ══════════════════════════════════════
            // 💬 SECTION 2: WHATSAPP
            // ══════════════════════════════════════
            EmergencySectionHeader(emoji = "💬", title = "WhatsApp", textColor = Color(0xFF25D366))

            // 📞 WhatsApp Call Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val cleanPhone = emergencyPhone.replace(Regex("[^+\\d]"), "")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone"))
                            intent.setPackage("com.whatsapp")
                            context.startActivity(intent)
                            viewModel.addLogSystemEvent("🆘 Launched WhatsApp call to ${emergencyContactName.ifBlank { cleanPhone }}.")
                        } catch (e: Exception) {
                            // Fallback if WhatsApp not installed — open without package restriction
                            try {
                                val cleanPhone = emergencyPhone.replace(Regex("[^+\\d]"), "")
                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone"))
                                context.startActivity(fallback)
                            } catch (e2: Exception) {
                                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .border(1.dp, Color(0xFF25D366).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
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
                                .background(Color(0xFF25D366).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "WhatsApp Call",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Call ${emergencyContactName.ifBlank { "emergency contact" }} via WhatsApp",
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

            // 💬 WhatsApp Message Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val cleanPhone = emergencyPhone.replace(Regex("[^+\\d]"), "")
                            val waMessage = emergencyMessage.ifBlank { "Emergency alert: Please assist immediately." }
                            val encodedMsg = Uri.encode(waMessage)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=$encodedMsg"))
                            intent.setPackage("com.whatsapp")
                            context.startActivity(intent)
                            viewModel.addLogSystemEvent("🆘 Launched WhatsApp message to ${emergencyContactName.ifBlank { cleanPhone }}.")
                        } catch (e: Exception) {
                            try {
                                val cleanPhone = emergencyPhone.replace(Regex("[^+\\d]"), "")
                                val waMessage = emergencyMessage.ifBlank { "Emergency alert: Please assist immediately." }
                                val encodedMsg = Uri.encode(waMessage)
                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=$encodedMsg"))
                                context.startActivity(fallback)
                            } catch (e2: Exception) {
                                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .border(1.dp, Color(0xFF25D366).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
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
                                .background(Color(0xFF25D366).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "WhatsApp Message",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Send message to ${emergencyContactName.ifBlank { "emergency contact" }}",
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

            // 📍 Send Current Location Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isFetchingLocation) return@clickable
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
                                        val cleanPhone = emergencyPhone.replace(Regex("[^+\\d]"), "")
                                        val locationMsg = Uri.encode(
                                            "Here is my current location:\n$mapsLink"
                                        )
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://wa.me/$cleanPhone?text=$locationMsg")
                                            )
                                            intent.setPackage("com.whatsapp")
                                            context.startActivity(intent)
                                            viewModel.addLogSystemEvent("\uD83C\uDD98 Sent current location via WhatsApp.")
                                        } catch (e: Exception) {
                                            try {
                                                val fallback = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://wa.me/$cleanPhone?text=$locationMsg")
                                                )
                                                context.startActivity(fallback)
                                            } catch (e2: Exception) {
                                                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                                            }
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
                    .border(1.dp, Color(0xFF25D366).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
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
                                .background(Color(0xFF25D366).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Send Current Location",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isFetchingLocation) "Getting location..." else "Send GPS pin via WhatsApp",
                                fontSize = 12.sp,
                                color = if (isFetchingLocation) Color(0xFF25D366) else Slate400
                            )
                        }
                    }
                    if (isFetchingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF25D366),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Slate400.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ══════════════════════════════════════
            // 🚨 SECTION 3: EMERGENCY SIREN
            // ══════════════════════════════════════
            EmergencySectionHeader(emoji = "🚨", title = "Emergency Siren", ErrorRed)

            // 🔊 Hold-to-Activate Siren Card
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
                        if (isAlarmActive) Color(0xFFF59E0B) else ErrorRed.copy(alpha = 0.2f),
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
                                    else ErrorRed.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isAlarmActive) Color(0xFFF59E0B) else ErrorRed,
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

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Reusable section header for the Emergency Screen categories.
 */
@Composable
private fun EmergencySectionHeader(emoji: String, title: String, textColor: Color = Color.White) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        Text(text = emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
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
