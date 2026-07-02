package com.example.voxa.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voxa.R
import com.example.voxa.ai.archive.DtwMatcher
import com.example.voxa.ai.archive.MfccExtractor
import com.example.voxa.data.PracticeStats
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import com.example.voxa.utils.AudioFileHelper
import com.example.voxa.utils.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI

data class PracticeWord(
    val englishText: String,
    val arabicText: String,
    val emoji: String,
    val filename: String
)

@Composable
fun SpeechPracticeScreen(viewModel: IVoxaViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val practiceHistory by viewModel.practiceStats.collectAsState()

    val practiceWords = listOf(
        PracticeWord("Water", "مية", "💧", "water.pcm"),
        PracticeWord("Food", "أكل", "🍎", "food.pcm"),
        PracticeWord("Bathroom", "حمام", "🛁", "bathroom.pcm"),
        PracticeWord("Sleep", "نام", "😴", "sleep.pcm"),
        PracticeWord("Thank you", "شكرا", "🙏", "thanks.pcm"),
        PracticeWord("Papa", "بابا", "👨", "papa.pcm"),
        PracticeWord("Mama", "ماما", "👩", "mama.pcm")
    )

    var selectedIndex by remember { mutableStateOf(0) }
    val currentWord = practiceWords[selectedIndex]

    var isRecordingAttempt by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf(0f) }

    // Scoring feedback states
    var lastScore by remember { mutableStateOf<Int?>(null) }
    var lastStars by remember { mutableStateOf<Int?>(null) }
    var showResultCelebration by remember { mutableStateOf(false) }

    // TTS speaker helper
    val audioPlayer = remember { AudioPlayer(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    // Interactive Pulsing visualizer scaling factor
    val pulseScale by animateFloatAsState(
        targetValue = if (isRecordingAttempt) 1f + volumeLevel * 0.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PulseScale"
    )

    // Permission check launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Microphone permission is required to practice.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Audio recording logic
    LaunchedEffect(isRecordingAttempt) {
        if (isRecordingAttempt) {
            withContext(Dispatchers.IO) {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    isRecordingAttempt = false
                    return@withContext
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    isRecordingAttempt = false
                    return@withContext
                }

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    isRecordingAttempt = false
                    return@withContext
                }

                val pcmBufferList = mutableListOf<Short>()
                val readBuffer = ShortArray(1024)
                audioRecord.startRecording()

                try {
                    val maxSamples = sampleRate * 2 // 2-second attempt limit
                    while (isRecordingAttempt && pcmBufferList.size < maxSamples) {
                        val readSize = audioRecord.read(readBuffer, 0, readBuffer.size)
                        if (readSize > 0) {
                            var maxVal = 0
                            for (i in 0 until readSize) {
                                val sample = readBuffer[i]
                                pcmBufferList.add(sample)
                                val absVal = abs(sample.toInt())
                                if (absVal > maxVal) maxVal = absVal
                            }
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
                    } catch (_: Exception) {}
                }

                // Process recording
                val rawPcm = pcmBufferList.toShortArray()
                val trimmedPcm = AudioFileHelper.trimSilence(rawPcm)

                if (trimmedPcm.size > 1600) { // Valid speech length minimum ~100ms
                    // 1. Extract MFCC features
                    val extractor = MfccExtractor()
                    val childMfcc = extractor.extract(trimmedPcm)

                    // 2. Load or generate reference template
                    val referencePcm = readAssetPcm(context, currentWord.filename)
                        ?: generateSyntheticReferencePcm(currentWord.arabicText)
                    val referenceMfcc = extractor.extract(referencePcm)

                    // 3. Compute DTW distance
                    val distance = DtwMatcher.dtwDistance(referenceMfcc, childMfcc)

                    // 4. Calculate score & star rating
                    // Threshold mapping:
                    // Distance < 4.0 ➔ 3 Stars
                    // Distance 4.0 - 5.5 ➔ 2 Stars
                    // Distance 5.5 - 7.0 ➔ 1 Star
                    // Distance > 7.0 ➔ Try Again (0 Stars)
                    val (score, stars) = when {
                        distance < 4.0 -> 100 to 3
                        distance in 4.0..5.5 -> 80 to 2
                        distance in 5.5..7.0 -> 60 to 1
                        else -> 40 to 0
                    }

                    withContext(Dispatchers.Main) {
                        lastScore = score
                        lastStars = stars
                        showResultCelebration = true
                        
                        // Play feedback sounds
                        try {
                            val soundRes = if (stars > 0) R.raw.voicy_correct_answer_sound_effect else android.R.drawable.stat_notify_error
                            if (stars > 0) {
                                val mp = android.media.MediaPlayer.create(context, R.raw.voicy_correct_answer_sound_effect)
                                mp?.setOnCompletionListener { it.release() }
                                mp?.start()
                            }
                        } catch (_: Exception) {}

                        // Persist to database
                        viewModel.recordPracticeAttempt(currentWord.arabicText, score, stars)
                        viewModel.addLogSystemEvent("🎮 Practice Attempt: ${currentWord.arabicText} score=$score stars=$stars")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Audio segment too short. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }

                withContext(Dispatchers.Main) {
                    isRecordingAttempt = false
                    volumeLevel = 0f
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pronunciation Game",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Word Carousel Indicator / Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                practiceWords.forEachIndexed { idx, word ->
                    val isSelected = selectedIndex == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Sky400 else Slate800)
                            .clickable {
                                selectedIndex = idx
                                lastScore = null
                                lastStars = null
                                showResultCelebration = false
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = word.emoji,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Practice Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .border(1.dp, Slate700, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top: Speaker Playback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentWord.englishText,
                            color = Slate400,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                val gender = activeProfile?.gender ?: "Male"
                                audioPlayer.playTranslation(currentWord.filename, gender, currentWord.arabicText)
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Sky400.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Hear Correct Pronunciation",
                                tint = Sky400
                            )
                        }
                    }

                    // Center: Word & Emoji
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentWord.emoji,
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentWord.arabicText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Bottom: Scoring Celebration
                    Box(
                        modifier = Modifier.height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showResultCelebration && lastStars != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (lastStars!! > 0) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        repeat(3) { starIndex ->
                                            val isFilled = starIndex < lastStars!!
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Star",
                                                tint = if (isFilled) Color(0xFFF59E0B) else Slate600,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Pronunciation Score: ${lastScore}%",
                                        color = Sky400,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "Try Again! You can do it!",
                                        color = ErrorRed,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Hold the button below and speak",
                                color = Slate400,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hold-to-Record Button Visualizer
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(if (isRecordingAttempt) Sky400.copy(alpha = 0.2f) else Slate800)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                isRecordingAttempt = true
                                waitForUpOrCancellation()
                                isRecordingAttempt = false
                            }
                        }
                    }
                    .border(
                        width = 2.dp,
                        color = if (isRecordingAttempt) Sky400 else Slate700,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isRecordingAttempt) Sky400.copy(alpha = 0.15f) else Color.Transparent)
                )

                // Inner Button Core
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(if (isRecordingAttempt) Sky400 else Slate700),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecordingAttempt) Icons.Default.PlayArrow else Icons.Default.VolumeUp,
                        contentDescription = "Record attempt",
                        tint = if (isRecordingAttempt) Slate900 else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom: History Logs
            Text(
                text = "Practice History",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate800)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (practiceHistory.isEmpty()) {
                    item {
                        Text(
                            text = "No history attempts yet. Speak your first word!",
                            color = Slate400,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        )
                    }
                } else {
                    items(practiceHistory) { item ->
                        PracticeHistoryRow(item)
                    }
                }
            }
        }
    }
}

@Composable
fun PracticeHistoryRow(stats: PracticeStats) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stats.word,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(stats.timestamp)),
                    fontSize = 11.sp,
                    color = Slate500
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${stats.score}%",
                    color = Sky400,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { starIndex ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Star",
                            tint = if (starIndex < stats.stars) Color(0xFFF59E0B) else Slate600,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Attempts to read the raw PCM file from assets.
 */
private fun readAssetPcm(context: Context, filename: String): ShortArray? {
    return try {
        val inputStream = context.assets.open("practice/$filename")
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        shorts
    } catch (_: Exception) {
        null
    }
}

/**
 * Fallback generator to synthesize a valid reference 1-second 400Hz PCM block if assets are missing.
 * Prevents file-missing crashes and allows evaluation on mock signals.
 */
private fun generateSyntheticReferencePcm(seedWord: String): ShortArray {
    val sampleRate = 16000
    val durationSeconds = 1.0
    val totalSamples = (sampleRate * durationSeconds).toInt()
    
    // Select frequency based on seed word length to differentiate template signatures slightly
    val freq = 350.0 + (seedWord.length * 15.0)
    
    return ShortArray(totalSamples) { i ->
        (sin(2.0 * PI * freq * i / sampleRate) * 16000.0).toInt().toShort()
    }
}
