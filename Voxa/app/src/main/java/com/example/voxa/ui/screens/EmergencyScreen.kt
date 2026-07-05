package com.example.voxa.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.voxa.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import com.example.voxa.utils.LocationHelper

data class CaregiverContact(val name: String, val phone: String)

private fun parseCaregiverContacts(jsonStr: String?): List<CaregiverContact> {
    val list = mutableListOf<CaregiverContact>()
    if (jsonStr.isNullOrBlank()) return list
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(CaregiverContact(obj.getString("name"), obj.getString("phone")))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

private fun serializeCaregiverContacts(list: List<CaregiverContact>): String {
    val array = JSONArray()
    for (contact in list) {
        val obj = JSONObject()
        obj.put("name", contact.name)
        obj.put("phone", contact.phone)
        array.put(obj)
    }
    return array.toString()
}

@Composable
fun EmergencyScreen(viewModel: IVoxaViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl

    val caregivers = remember(activeProfile) {
        parseCaregiverContacts(activeProfile?.caregiverContactsJson)
    }

    var isHoldingAlarm by remember { mutableStateOf(false) }
    var alarmProgress by remember { mutableStateOf(0f) }
    var isAlarmActive by remember { mutableStateOf(false) }
    var ringtonePlayer: MediaPlayer? by remember { mutableStateOf(null) }

    var isFetchingLocation by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }
    var tempName by remember { mutableStateOf("") }
    var tempPhone by remember { mutableStateOf("") }

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
                Toast.makeText(context, context.getString(R.string.toast_siren_activated), Toast.LENGTH_SHORT).show()
                viewModel.addLogSystemEvent("log_siren_activated")
                
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
                viewModel.addLogSystemEvent("log_siren_paused")
                
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
                Toast.makeText(context, context.getString(R.string.toast_siren_stopped), Toast.LENGTH_SHORT).show()
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

    // Function to dispatch direct SMS to primary caregiver
    fun sendLocationSMS() {
        if (activeProfile == null) {
            Toast.makeText(context, context.getString(R.string.toast_select_profile_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (caregivers.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_enroll_caregiver_first), Toast.LENGTH_SHORT).show()
            return
        }
        
        isFetchingLocation = true
        coroutineScope.launch {
            val location = LocationHelper.getFreshLocation(context)
            isFetchingLocation = false
            
            val mapsLink = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "Location unavailable"
            }
            
            val message = "🚨 Voxa SOS Alert! Child might need assistance. Location: $mapsLink"
            
            var sentSuccess = false
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }
                
                // Alert primary caregiver first
                val primary = caregivers.first()
                smsManager.sendTextMessage(primary.phone, null, message, null, null)
                viewModel.addLogSystemEvent("log_sms_sent|${primary.name}")
                Toast.makeText(context, context.getString(R.string.toast_sms_sent_success, primary.name), Toast.LENGTH_SHORT).show()
                sentSuccess = true
            } catch (e: Exception) {
                Log.e("EmergencyScreen", "Failed to dispatch SMS: ${e.message}")
            }
            
            if (!sentSuccess) {
                Toast.makeText(context, context.getString(R.string.toast_sms_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to share location via WhatsApp
    fun shareLocationWhatsApp(contact: CaregiverContact) {
        isFetchingLocation = true
        coroutineScope.launch {
            val location = LocationHelper.getFreshLocation(context)
            isFetchingLocation = false
            val mapsLink = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "Location unavailable"
            }
            val message = "🚨 Voxa SOS Alert! Child might need assistance. Location: $mapsLink"
            
            try {
                val url = "whatsapp://send?phone=${contact.phone}&text=${URLEncoder.encode(message, "UTF-8")}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
                viewModel.addLogSystemEvent("log_whatsapp_shared|${contact.name}")
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_whatsapp_not_installed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to dial caregiver
    fun dialCaregiver(contact: CaregiverContact) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
            context.startActivity(intent)
            viewModel.addLogSystemEvent("log_dialer_launched|${contact.name}")
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_dialer_failed), Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.emergency_actions_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.quick_distress_triggers_desc),
                fontSize = 13.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
        }

        // ── CAREGIVER PRIORITY REGISTRY ──
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.caregiver_contacts_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (caregivers.size < 3 && activeProfile != null) {
                            IconButton(onClick = {
                                editingIndex = caregivers.size
                                tempName = ""
                                tempPhone = ""
                                showEditDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Sky400)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (activeProfile == null) {
                        Text(
                            text = "Please create and select a Child Profile to enroll contacts.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    } else if (caregivers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_caregivers_registered),
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    } else {
                        caregivers.forEachIndexed { index, contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate900)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = contact.phone,
                                        color = Slate400,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (index == 0) ErrorRed.copy(alpha = 0.2f) else Slate700)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (index == 0) {
                                                stringResource(R.string.primary_caregiver_badge)
                                            } else {
                                                stringResource(R.string.secondary_caregiver_badge, index)
                                            },
                                            color = if (index == 0) ErrorRed else Slate300,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { dialCaregiver(contact) }) {
                                        Icon(Icons.Default.Phone, contentDescription = "Dial", tint = SuccessGreen)
                                    }
                                    IconButton(onClick = { shareLocationWhatsApp(contact) }) {
                                        Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = Sky400)
                                    }
                                    IconButton(onClick = {
                                        editingIndex = index
                                        tempName = contact.name
                                        tempPhone = contact.phone
                                        showEditDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Slate400)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 🚨 Emergency Call & SMS shortcuts
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            context.startActivity(intent)
                            viewModel.addLogSystemEvent("log_dialer_launched|112")
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_dialer_failed), Toast.LENGTH_SHORT).show()
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
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFFFFB4AB),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.call_emergency_services_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB4AB)
                            )
                            Text(
                                text = stringResource(R.string.dial_112_desc),
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Slate400.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // 📍 Send Location Alert Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isFetchingLocation) { sendLocationSMS() }
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
                            if (isFetchingLocation) {
                                CircularProgressIndicator(color = Sky400, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mail,
                                    contentDescription = null,
                                    tint = Sky400,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.send_location_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Sky400
                            )
                            Text(
                                text = if (isFetchingLocation) stringResource(R.string.emergency_acquiring_gps) else stringResource(R.string.send_location_desc),
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Slate400.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // 🚨 Emergency Alarm (Hold to Activate) Card
        item {
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
                                text = stringResource(R.string.emergency_siren_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isAlarmActive) stringResource(R.string.siren_mute_desc) else stringResource(R.string.siren_hold_desc),
                                fontSize = 12.sp,
                                color = if (isAlarmActive) Color(0xFFF59E0B) else Slate400
                            )
                        }
                    }
                }
            }
        }
    }

    // Caregiver Edit/Add Alert Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.caregiver_enrollment_title), color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = Slate800,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text(stringResource(R.string.caregiver_name_label), color = Slate400) },
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
                        label = { Text(stringResource(R.string.caregiver_phone_label), color = Slate400) },
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
                        val active = activeProfile ?: return@Button
                        if (tempName.isNotBlank() && tempPhone.isNotBlank()) {
                            val newList = caregivers.toMutableList()
                            val newContact = CaregiverContact(tempName.trim(), tempPhone.trim())
                            if (editingIndex in 0 until caregivers.size) {
                                newList[editingIndex] = newContact
                            } else {
                                newList.add(newContact)
                            }
                            viewModel.updateCaregivers(active, serializeCaregiverContacts(newList))
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Sky400)
                ) {
                    Text(stringResource(R.string.save_btn), color = Slate900, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.cancel_btn), color = Slate400)
                }
            }
        )
    }
}

// ── PLAYBACK SYNTH WARNING ALARM ──
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
