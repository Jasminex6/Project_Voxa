package com.example.voxa.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.example.voxa.R
import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*
import com.example.voxa.utils.AudioFileHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

/**
 * ➕ EnrollmentScreen
 * Enables caregivers to enroll new sounds for a child.
 * In Lesson 11, this screen acts as an interactive UI mockup.
 * It simulates recording 3 audio samples (with animations and meters)
 * and writes the final intent to Room.
 */
@Composable
fun EnrollmentScreen(viewModel: IVoxaViewModel, onBack: () -> Unit) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val bifurcationWarning by viewModel.bifurcationWarningTriggered.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // UI state inputs tracking the name of the sound (e.g. "Water") and the Arabic meaning translation.
    var intentName by rememberSaveable { mutableStateOf("") }
    var outputPhrase by rememberSaveable { mutableStateOf("") }
    var recordedSamplesCount by rememberSaveable { mutableStateOf(0) }
    var isRecordingSample by rememberSaveable { mutableStateOf(false) }
    
    // Live volume level for the animated recording visualizer.
    var volumeLevel by remember { mutableStateOf(0.0f) }
    
    // Serialize state list to string to persist across tab navigation
    var savedFilePathsStr by rememberSaveable { mutableStateOf("") }
    val savedFilePaths = remember {
        mutableStateListOf<String>().apply {
            if (savedFilePathsStr.isNotEmpty()) {
                addAll(savedFilePathsStr.split(","))
            }
        }
    }

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showSuggestionsDialog by rememberSaveable { mutableStateOf(false) }

    // Real audio recording thread controller
    LaunchedEffect(isRecordingSample) {
        if (isRecordingSample) {
            withContext(Dispatchers.IO) {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Microphone hardware error.", Toast.LENGTH_SHORT).show()
                        isRecordingSample = false
                    }
                    return@withContext
                }

                val audioRecord = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                } catch (e: SecurityException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Microphone permission denied.", Toast.LENGTH_SHORT).show()
                        isRecordingSample = false
                    }
                    return@withContext
                }

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Microphone failed to initialize.", Toast.LENGTH_SHORT).show()
                        isRecordingSample = false
                    }
                    return@withContext
                }

                val pcmBufferList = mutableListOf<Short>()
                val readBuffer = ShortArray(1024)
                audioRecord.startRecording()

                try {
                    // Maximum 2.5 seconds to prevent memory overflow
                    val maxSamples = (sampleRate * 2.5).toInt()
                    while (isRecordingSample && pcmBufferList.size < maxSamples) {
                        val readSize = audioRecord.read(readBuffer, 0, readBuffer.size)
                        if (readSize > 0) {
                            var maxVal = 0
                            for (i in 0 until readSize) {
                                val sample = readBuffer[i]
                                pcmBufferList.add(sample)
                                val absVal = abs(sample.toInt())
                                if (absVal > maxVal) {
                                    maxVal = absVal
                                }
                            }
                            // Update live volume level
                            volumeLevel = (maxVal.toFloat() / 26214f).coerceIn(0f, 1f)
                        }
                        delay(20)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        audioRecord.stop()
                        audioRecord.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val rawPcm = pcmBufferList.toShortArray()
                
                // Use the exact same VAD engine as live listening to extract the segment,
                // ensuring enrollment templates match the preprocessing applied during live classification.
                val vad = com.example.voxa.ai.VoxaVAD()
                val segments = vad.processAudio(rawPcm)
                
                withContext(Dispatchers.Main) {
                    isRecordingSample = false
                    volumeLevel = 0f
                    
                    if (segments.isEmpty()) {
                        Toast.makeText(context, "No speech detected. Please speak louder.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    
                    val processedPcm = segments[0]
                    
                    try {
                        AudioFileHelper.validateDuration(processedPcm)
                        val activeId = activeProfile?.id ?: 0L
                        val cleanIntentName = intentName.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}_]"), "_")
                        val fileName = "template_${activeId}_${cleanIntentName}_${recordedSamplesCount}.pcm"
                        val filePath = AudioFileHelper.savePcmFile(context, processedPcm, fileName)
                        
                        savedFilePaths.add(filePath)
                        savedFilePathsStr = savedFilePaths.joinToString(",")
                        recordedSamplesCount++
                        
                        // Play Voicy Correct Answer Sound Effect
                        try {
                            val mp = android.media.MediaPlayer.create(context, com.example.voxa.R.raw.voicy_correct_answer_sound_effect)
                            mp?.setOnCompletionListener { it.release() }
                            mp?.start()
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }

                        Toast.makeText(context, "Sample $recordedSamplesCount saved successfully!", Toast.LENGTH_SHORT).show()
                        if (recordedSamplesCount == 5) {
                            showSaveDialog = true
                        } else {
                            // Auto-advance: launch a coroutine to start next sample recording after 1.5 seconds
                            scope.launch {
                                delay(1500)
                                if (recordedSamplesCount < 5) {
                                    isRecordingSample = true
                                }
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(context, e.message ?: "Invalid audio", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to save sample: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.clearBifurcationWarning(); onBack() },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Library",
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(id = R.string.enroll_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = stringResource(id = R.string.enroll_subtitle),
            fontSize = 13.sp,
            color = Slate400,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (activeProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.enroll_no_profile),
                    color = Slate400,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── TEXT INPUTS ──
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth()
                ) {
                     Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.enroll_card_title),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(id = R.string.btn_suggestions),
                                color = Sky400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { showSuggestionsDialog = true }
                            )
                        }

                        OutlinedTextField(
                            value = intentName,
                            onValueChange = { intentName = it },
                            label = { Text(stringResource(id = R.string.field_meaning), color = Slate400) },
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
                            value = outputPhrase,
                            onValueChange = { outputPhrase = it },
                            label = { Text(stringResource(id = R.string.field_translation), color = Slate400) },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3,
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

                // ── BIFURCATION WARNING BANNER ──
                if (bifurcationWarning != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningAmberDark.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, WarningAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ Notice: High Vocal Variation Detected",
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = bifurcationWarning!!,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.clearBifurcationWarning() },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(com.example.voxa.R.string.onboarding_dismiss), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ── INTERACTIVE RECORDING WIZARD ──
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.enroll_progress_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Progress Step Indicators (Linear Progress)
                        LinearProgressIndicator(
                            progress = recordedSamplesCount / 5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = SuccessGreen,
                            trackColor = Slate600
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(id = R.string.enroll_collected_count, recordedSamplesCount),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Min: 3 / Target: 5",
                                color = if (recordedSamplesCount >= 3) SuccessGreen else Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Live Volume Visualizer Bar
                        if (isRecordingSample) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Slate900)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(volumeLevel)
                                        .fillMaxHeight(0.7f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Sky400)
                                )
                            }
                        } else {
                            Text(
                                text = if (recordedSamplesCount > 0 && recordedSamplesCount < 5) {
                                    stringResource(id = R.string.enroll_timer_progress)
                                } else {
                                    stringResource(id = R.string.enroll_guideline_tap)
                                },
                                color = if (recordedSamplesCount > 0 && recordedSamplesCount < 5) SuccessGreen else Slate400,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Recording control button
                        Button(
                            onClick = {
                                if (intentName.isBlank() || outputPhrase.isBlank()) return@Button
                                isRecordingSample = !isRecordingSample
                            },
                            enabled = intentName.isNotBlank() && outputPhrase.isNotBlank() && recordedSamplesCount < 5,
                            contentPadding = PaddingValues(0.dp), // Clear default margins for comfy circle text
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecordingSample) ErrorRed else Sky500,
                                disabledContainerColor = Slate700
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(90.dp)
                        ) {
                            Text(
                                text = if (isRecordingSample) stringResource(id = R.string.btn_stop) else stringResource(id = R.string.btn_record),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Save Intent Button
                        Button(
                            onClick = {
                                showSaveDialog = true
                            },
                            enabled = recordedSamplesCount >= 3,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                disabledContainerColor = Slate700
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .width(220.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(id = R.string.btn_save_sound), color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Enrollment complete Save Dialog popup
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(id = R.string.dialog_complete_title)) },
            text = { Text(stringResource(id = R.string.dialog_complete_text, intentName, recordedSamplesCount)) },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        val cleanFileName = "${intentName.lowercase().trim().replace(Regex("[^\\p{L}\\p{N}_]"), "_")}.mp3"
                        viewModel.enrollIntent(
                            intentName = intentName.trim(),
                            outputPhrase = outputPhrase.trim(),
                            audioAssetPath = cleanFileName,
                            tempFilePaths = savedFilePaths.toList()
                        )
                        viewModel.addLogSystemEvent("Enrolled intent '${intentName.trim()}' into database with $recordedSamplesCount templates")

                        // Reset screen state
                        intentName = ""
                        outputPhrase = ""
                        recordedSamplesCount = 0
                        savedFilePaths.clear()
                        savedFilePathsStr = ""
                        Toast.makeText(context, "Sound intent saved to library!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text(stringResource(id = R.string.btn_save_sound), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveDialog = false },
                    border = BorderStroke(1.dp, Slate600),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300)
                ) {
                    Text(stringResource(id = R.string.dialog_cancel))
                }
            },
            containerColor = Slate800,
            titleContentColor = Color.White,
            textContentColor = Slate300
        )
    }

    // Suggested Vocalizations Dialog popup
    if (showSuggestionsDialog) {
        AlertDialog(
            onDismissRequest = { showSuggestionsDialog = false },
            title = { Text(stringResource(id = R.string.dialog_suggestions_title), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_suggestions_desc),
                        color = Slate300,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val categories = listOf(
                        "Daily Needs" to listOf(
                            Triple("Water", "أنا عايز ميّه", "water.mp3"),
                            Triple("Milk", "أنا عايز لبن", "milk.mp3"),
                            Triple("Food", "أنا عايز آكل", "food.mp3")
                        ),
                        "Comfort & Care" to listOf(
                            Triple("Bathroom", "عايز أدخل الحمام", "bathroom.mp3"),
                            Triple("Sleep", "أنا عايز أنام", "sleep.mp3"),
                            Triple("Help / Pain", "أنا تعبان / الحقني", "help.mp3")
                        ),
                        "Play & Social" to listOf(
                            Triple("More", "عايز تاني", "more.mp3"),
                            Triple("Stop", "لأ", "stop.mp3")
                        )
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(categories) { (categoryName, list) ->
                            Text(
                                text = categoryName,
                                color = Sky400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (item in list) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Slate900),
                                        border = BorderStroke(1.dp, Slate700),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                intentName = item.first
                                                outputPhrase = item.second
                                                showSuggestionsDialog = false
                                                Toast.makeText(context, "Loaded suggested word: ${item.first}!", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.first,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = item.second,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSuggestionsDialog = false }) {
                    Text(stringResource(id = R.string.dialog_cancel), color = Sky400, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Slate800,
            titleContentColor = Color.White,
            textContentColor = Slate300
        )
    }
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──

private class MockEnrollmentViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true))
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
    override val practiceStats = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.PracticeStats>())
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
    override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Enrollment Screen Preview")
@Composable
fun EnrollmentScreenPreview() {
    VoxaTheme {
        EnrollmentScreen(viewModel = MockEnrollmentViewModel(), onBack = {})
    }
}

