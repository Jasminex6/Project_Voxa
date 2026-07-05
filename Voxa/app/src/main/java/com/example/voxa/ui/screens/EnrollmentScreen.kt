package com.example.voxa.ui.screens

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.LogEvent
import com.example.voxa.ui.theme.*
import com.example.voxa.utils.AudioFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun EnrollmentScreen(viewModel: IVoxaViewModel, onBack: () -> Unit) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var intentName by rememberSaveable { mutableStateOf("") }
    var outputPhrase by rememberSaveable { mutableStateOf("") }
    var recordedSamplesCount by rememberSaveable { mutableStateOf(0) }
    var isRecordingSample by rememberSaveable { mutableStateOf(false) }
    
    var volumeLevel by remember { mutableStateOf(0.0f) }
    
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

    LaunchedEffect(isRecordingSample) {
        if (isRecordingSample) {
            val wasListening = viewModel.isListening.value
            if (wasListening) {
                viewModel.toggleListening()
                delay(600) // Wait for service to stop and release the microphone hardware
            }
            
            val pcmBufferList = mutableListOf<Short>()
            
            try {
                withContext(Dispatchers.IO) {
                    val sampleRate = 16000
                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                    
                    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                        withContext(Dispatchers.Main) {
                            val msg = context.getString(R.string.enroll_microphone_error)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                            val msg = context.getString(R.string.enroll_permission_denied)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            isRecordingSample = false
                        }
                        return@withContext
                    }

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        withContext(Dispatchers.Main) {
                            val msg = context.getString(R.string.enroll_init_failed)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            isRecordingSample = false
                        }
                        return@withContext
                    }

                    val readBuffer = ShortArray(1024)
                    audioRecord.startRecording()

                    try {
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
                                volumeLevel = (maxVal.toFloat() / 26214f).coerceIn(0f, 1f)
                            }
                            delay(20)
                        }
                    } finally {
                        try {
                            audioRecord.stop()
                            audioRecord.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    val rawPcm = pcmBufferList.toShortArray()
                    val trimmedPcm = AudioFileHelper.trimSilence(rawPcm)
                    
                    withContext(Dispatchers.Main) {
                        isRecordingSample = false
                        volumeLevel = 0f
                        
                        if (wasListening && !viewModel.isListening.value) {
                            viewModel.toggleListening()
                        }

                        try {
                            AudioFileHelper.validateDuration(trimmedPcm)
                            val activeId = activeProfile?.id ?: 0L
                            val cleanIntentName = intentName.trim().lowercase().replace(" ", "_")
                            val fileName = "template_${activeId}_${cleanIntentName}_${recordedSamplesCount}.pcm"
                            val filePath = AudioFileHelper.savePcmFile(context, trimmedPcm, fileName)
                            
                            savedFilePaths.add(filePath)
                            savedFilePathsStr = savedFilePaths.joinToString(",")
                            recordedSamplesCount++
                            
                            try {
                                val mp = android.media.MediaPlayer.create(context, com.example.voxa.R.raw.voicy_correct_answer_sound_effect)
                                mp?.setOnCompletionListener { it.release() }
                                mp?.start()
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }

                            val msg = context.getString(R.string.enroll_sample_saved, recordedSamplesCount)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (recordedSamplesCount == 3) {
                                showSaveDialog = true
                            } else {
                                scope.launch {
                                    delay(1500)
                                    if (recordedSamplesCount < 3) {
                                        isRecordingSample = true
                                    }
                                }
                            }
                        } catch (e: IllegalArgumentException) {
                            val message = e.message ?: ""
                            val resolvedMessage = when {
                                message.startsWith("too_short|") -> {
                                    val ms = message.substringAfter("too_short|").toIntOrNull() ?: 0
                                    context.getString(R.string.toast_audio_too_short, ms)
                                }
                                message.startsWith("too_long|") -> {
                                    val ms = message.substringAfter("too_long|").toIntOrNull() ?: 0
                                    context.getString(R.string.toast_audio_too_long, ms)
                                }
                                else -> message.ifEmpty { context.getString(R.string.toast_invalid_audio) }
                            }
                            Toast.makeText(context, resolvedMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_failed_save_sample, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
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
            .padding(16.dp),
        horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = if (isRtl) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                    contentDescription = "Back to Library",
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.enroll_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isRtl) TextAlign.End else TextAlign.Start
            )
        }
        Text(
            text = stringResource(R.string.enroll_desc),
            fontSize = 13.sp,
            color = Slate400,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = if (isRtl) TextAlign.End else TextAlign.Start
        )

        if (activeProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.enroll_no_profile),
                    color = Slate400,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.enroll_vocalization_details),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(R.string.enroll_view_suggestions),
                                color = Sky400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { showSuggestionsDialog = true }
                            )
                        }

                        OutlinedTextField(
                            value = intentName,
                            onValueChange = { intentName = it },
                            label = { Text(stringResource(R.string.enroll_meaning_label), color = Slate400) },
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
                            label = { Text(stringResource(R.string.enroll_arabic_translation_label), color = Slate400) },
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
                            text = stringResource(R.string.enroll_progress_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            for (i in 1..3) {
                                val isRecorded = i <= recordedSamplesCount
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isRecorded -> SuccessGreen
                                                isRecordingSample && i == recordedSamplesCount + 1 -> Sky400
                                                else -> Slate600
                                            }
                                        )
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.enroll_collected_count, recordedSamplesCount),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

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
                                text = if (recordedSamplesCount > 0 && recordedSamplesCount < 3) {
                                    stringResource(R.string.enroll_auto_advance)
                                } else {
                                    stringResource(R.string.enroll_tap_to_start)
                                },
                                color = if (recordedSamplesCount > 0 && recordedSamplesCount < 3) SuccessGreen else Slate400,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (intentName.isBlank() || outputPhrase.isBlank()) return@Button
                                isRecordingSample = !isRecordingSample
                            },
                            enabled = intentName.isNotBlank() && outputPhrase.isNotBlank() && recordedSamplesCount < 3,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecordingSample) ErrorRed else Sky500,
                                disabledContainerColor = Slate700
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(90.dp)
                        ) {
                            Text(
                                text = if (isRecordingSample) stringResource(R.string.enroll_stop_btn) else stringResource(R.string.enroll_record_btn),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                val cleanFileName = "${intentName.lowercase().replace(" ", "_")}.mp3"
                                viewModel.enrollIntent(
                                    intentName = intentName.trim(),
                                    outputPhrase = outputPhrase.trim(),
                                    audioAssetPath = cleanFileName,
                                    tempFilePaths = savedFilePaths.toList()
                                )
                                viewModel.addLogSystemEvent("log_intent_enrolled|${intentName.trim()}")

                                intentName = ""
                                outputPhrase = ""
                                recordedSamplesCount = 0
                                savedFilePaths.clear()
                                savedFilePathsStr = ""
                            },
                            enabled = recordedSamplesCount == 3,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                disabledContainerColor = Slate700
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.enroll_save_sound_btn), color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.enroll_complete_title)) },
            text = { Text(stringResource(R.string.enroll_complete_desc, intentName)) },
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
                        viewModel.addLogSystemEvent("log_intent_enrolled|${intentName.trim()}")

                        intentName = ""
                        outputPhrase = ""
                        recordedSamplesCount = 0
                        savedFilePaths.clear()
                        savedFilePathsStr = ""
                        
                        val msg = context.getString(R.string.enroll_sound_saved_toast)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text(stringResource(R.string.enroll_save_sound_btn), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveDialog = false },
                    border = BorderStroke(1.dp, Slate600),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300)
                ) {
                    Text(stringResource(R.string.cancel_btn))
                }
            },
            containerColor = Slate800,
            titleContentColor = Color.White,
            textContentColor = Slate300
        )
    }

    if (showSuggestionsDialog) {
        AlertDialog(
            onDismissRequest = { showSuggestionsDialog = false },
            title = { Text(stringResource(R.string.enroll_suggestions_title), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    Text(
                        text = stringResource(R.string.enroll_suggestions_desc),
                        color = Slate300,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val categories = listOf(
                        R.string.suggestions_cat_daily to listOf(
                            Triple("Water", "أنا عايز ميّه", "water.mp3"),
                            Triple("Milk", "أنا عايز لبن", "milk.mp3"),
                            Triple("Food", "أنا عايز آكل", "food.mp3")
                        ),
                        R.string.suggestions_cat_comfort to listOf(
                            Triple("Bathroom", "عايز أدخل الحمام", "bathroom.mp3"),
                            Triple("Sleep", "أنا عايز أنام", "sleep.mp3"),
                            Triple("Help / Pain", "أنا تعبان / الحقني", "help.mp3")
                        ),
                        R.string.suggestions_cat_play to listOf(
                            Triple("More", "عايز تاني", "more.mp3"),
                            Triple("Stop", "لأ", "stop.mp3")
                        )
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(categories) { (categoryNameRes, list) ->
                            Text(
                                text = stringResource(categoryNameRes),
                                color = Sky400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (item in list) {
                                    val isFemale = activeProfile?.gender == "Female"
                                    val genderedPhrase = if (isFemale) {
                                        item.second
                                            .replace("عايز", "عايزة")
                                            .replace("تعبان", "تعبانة")
                                    } else {
                                        item.second
                                    }
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Slate900),
                                        border = BorderStroke(1.dp, Slate700),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                intentName = item.first
                                                outputPhrase = genderedPhrase
                                                showSuggestionsDialog = false
                                                val msg = context.getString(R.string.enroll_suggestions_loaded, item.first)
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                                                text = genderedPhrase,
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
                    Text(stringResource(R.string.cancel_btn), color = Sky400, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Slate800,
            titleContentColor = Color.White,
            textContentColor = Slate300
        )
    }
}

private class MockEnrollmentViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true))
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Enrollment Screen Preview")
@Composable
fun EnrollmentScreenPreview() {
    VoxaTheme {
        EnrollmentScreen(viewModel = MockEnrollmentViewModel(), onBack = {})
    }
}
