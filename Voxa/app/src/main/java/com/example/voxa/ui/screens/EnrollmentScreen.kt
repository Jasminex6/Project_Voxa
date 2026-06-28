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
import android.widget.Toast
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
 * It simulates recording 5 audio samples (with animations and meters)
 * and writes the final intent to Room.
 */
@Composable
fun EnrollmentScreen(viewModel: IVoxaViewModel, onBack: () -> Unit) {
    val activeProfile by viewModel.activeProfile.collectAsState()
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
                            volumeLevel = (maxVal.toFloat() / 32768f).coerceIn(0f, 1f)
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
                val trimmedPcm = AudioFileHelper.trimSilence(rawPcm)
                
                withContext(Dispatchers.Main) {
                    isRecordingSample = false
                    volumeLevel = 0f
                    try {
                        AudioFileHelper.validateDuration(trimmedPcm)
                        val activeId = activeProfile?.id ?: 0L
                        val cleanIntentName = intentName.trim().lowercase().replace(" ", "_")
                        val fileName = "template_${activeId}_${cleanIntentName}_${recordedSamplesCount}.pcm"
                        val filePath = AudioFileHelper.savePcmFile(context, trimmedPcm, fileName)
                        
                        savedFilePaths.add(filePath)
                        savedFilePathsStr = savedFilePaths.joinToString(",")
                        recordedSamplesCount++
                        Toast.makeText(context, "Sample $recordedSamplesCount saved successfully!", Toast.LENGTH_SHORT).show()
                        if (recordedSamplesCount == 5) {
                            showSaveDialog = true
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
                onClick = onBack,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Library",
                    tint = Color.White
                )
            }
            Text(
                text = "Enroll Custom Sound",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = "Record 5 speech samples to train the sound dictionary.",
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
                    text = "Please create and activate a child profile first\nto enroll new vocalizations.",
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
                        OutlinedTextField(
                            value = intentName,
                            onValueChange = { intentName = it },
                            label = { Text("Meaning of Sound / Word (e.g. Water)", color = Slate400) },
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
                            label = { Text("Egyptian Arabic Voice Translation (e.g. أنا عايز ميّه)", color = Slate400) },
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
                            text = "Sample Enrollment Progress",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Step Indicators (dots)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            for (i in 1..5) {
                                val isRecorded = i <= recordedSamplesCount
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isRecorded -> SuccessGreen // Green for recorded
                                                isRecordingSample && i == recordedSamplesCount + 1 -> Sky400 // Cyan for current
                                                else -> Slate600 // Grey
                                            }
                                        )
                                )
                            }
                        }

                        Text(
                            text = "Collected: $recordedSamplesCount / 5 Samples",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

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
                                text = "Tap 'Record Sample' to start, tap again to finish.",
                                color = Slate400,
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
                                text = if (isRecordingSample) "Stop" else "Record\nSample",
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
                                val cleanFileName = "${intentName.lowercase().replace(" ", "_")}.mp3"
                                
                                viewModel.enrollIntent(
                                    intentName = intentName.trim(),
                                    outputPhrase = outputPhrase.trim(),
                                    audioAssetPath = cleanFileName,
                                    tempFilePaths = savedFilePaths.toList()
                                )
                                viewModel.addLogSystemEvent("Enrolled intent '${intentName.trim()}' into database with 5 templates")

                                // Reset screen state
                                intentName = ""
                                outputPhrase = ""
                                recordedSamplesCount = 0
                                savedFilePaths.clear()
                                savedFilePathsStr = ""
                            },
                            enabled = recordedSamplesCount == 5,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                disabledContainerColor = Slate700
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Sound", color = Color.White)
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
            title = { Text("Enrollment Complete") },
            text = { Text("You have recorded all 5 samples for '$intentName'. Would you like to save this sound now?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        val cleanFileName = "${intentName.lowercase().replace(" ", "_")}.mp3"
                        viewModel.enrollIntent(
                            intentName = intentName.trim(),
                            outputPhrase = outputPhrase.trim(),
                            audioAssetPath = cleanFileName,
                            tempFilePaths = savedFilePaths.toList()
                        )
                        viewModel.addLogSystemEvent("Enrolled intent '${intentName.trim()}' into database with 5 templates")

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
                    Text("Save Sound", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveDialog = false },
                    border = BorderStroke(1.dp, Slate600),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300)
                ) {
                    Text("Cancel")
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
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
    override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
    override fun selectActiveProfile(profileId: Long) {}
    override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
    override fun deleteIntent(intent: com.example.voxa.data.EnrolledIntent) {}
    override fun toggleListening() {}
    override fun updateListeningState() {}
    override fun addLogSystemEvent(message: String) {}
    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
    override fun clearLogs() {}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Enrollment Screen Preview")
@Composable
fun EnrollmentScreenPreview() {
    VoxaTheme {
        EnrollmentScreen(viewModel = MockEnrollmentViewModel(), onBack = {})
    }
}

