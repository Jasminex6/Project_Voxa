package com.example.voxa.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.content.res.AssetFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voxa.ai.DtwMatcher
import com.example.voxa.ai.MfccExtractor
import com.example.voxa.data.PracticeStats
import com.example.voxa.ui.*
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════
// 🎮 SPEECH PRACTICE SCREEN — Duolingo-Style Pronunciation Game
// ═══════════════════════════════════════════════════════════════

/**
 * Practice word data model — defines each word card in the horizontal selector.
 */
data class PracticeWord(
    val english: String,
    val arabic: String,
    val emoji: String,
    val assetFileName: String // e.g. "practice/water_ref.wav" (supports .wav and .mp3)
)

/** Hardcoded practice vocabulary — ships with the app */
val PRACTICE_WORDS = listOf(
    PracticeWord("Water", "مايه", "💧", "practice/water_ref.wav"),
    PracticeWord("Milk", "لبن", "🥛", "practice/milk_ref.wav"),
    PracticeWord("Bread", "عيش", "🍞", "practice/bread_ref.wav"),
    PracticeWord("Help", "مساعدة", "🆘", "practice/help_ref.wav"),
    PracticeWord("Mom", "ماما", "👩", "practice/mama_ref.wav"),
    PracticeWord("Dad", "بابا", "👨", "practice/baba_ref.wav")
)

// ── Accent color for the Practice tab ──
val PracticeViolet = Color(0xFFa855f7)
val PracticeVioletDark = Color(0xFF7c3aed)
val PracticeVioletBg = Color(0xFF581c87)

// ── Motivational message pools ──
private val MESSAGES_3_STARS = listOf(
    "Amazing! You're a superstar! 🎉",
    "WOW! Perfect pronunciation! 🏆",
    "You nailed it! Champion! 👑",
    "Incredible! You sound perfect! ✨",
    "Bravo! That was flawless! 🌟"
)
private val MESSAGES_2_STARS = listOf(
    "Great job! Almost perfect! 💪",
    "So close to perfect! Keep it up! 🚀",
    "Wonderful work! You're getting better! ⭐",
    "Really good! Just a tiny bit more! 🎯",
    "Impressive! You're nearly there! 🌈"
)
private val MESSAGES_1_STAR = listOf(
    "Good try! Keep practicing! 😊",
    "Nice effort! You're learning fast! 📚",
    "You're on the right track! 🌱",
    "Keep going! Practice makes perfect! 💡",
    "That's a great start! Try again! 🎵"
)
private val MESSAGES_0_STARS = listOf(
    "Let's try again! You can do it! 🌈",
    "Don't give up! Practice makes perfect! 💫",
    "One more time! You've got this! 🎯",
    "Keep trying! Every attempt counts! 🦋",
    "You're brave for trying! Go again! 🌟"
)

/**
 * Maps DTW distance to (score, stars) per the specification.
 */
private fun computeScoreFromDtw(dtwDistance: Double): Pair<Int, Int> {
    return when {
        dtwDistance < 4.0 -> Pair(100, 3)
        dtwDistance < 5.5 -> Pair(80, 2)
        dtwDistance < 7.0 -> Pair(60, 1)
        else -> Pair(0, 0)
    }
}

/**
 * Returns a random motivational message for the given star count.
 */
private fun getMotivationalMessage(stars: Int): String {
    return when (stars) {
        3 -> MESSAGES_3_STARS.random()
        2 -> MESSAGES_2_STARS.random()
        1 -> MESSAGES_1_STAR.random()
        else -> MESSAGES_0_STARS.random()
    }
}

/**
 * Returns the color for the star/score display based on star count.
 */
private fun getScoreColor(stars: Int): Color {
    return when (stars) {
        3 -> Color(0xFFfbbf24) // Gold
        2 -> Color(0xFF38bdf8) // Sky blue
        1 -> Color(0xFFfb923c) // Orange
        else -> Color(0xFFef4444) // Red
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════

@Composable
fun SpeechPracticeScreen(viewModel: IVoxaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val practiceStats by viewModel.practiceStats.collectAsState()

    // ── State ──
    var selectedWordIndex by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var isScoring by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var resultScore by remember { mutableIntStateOf(0) }
    var resultStars by remember { mutableIntStateOf(0) }
    var resultDtw by remember { mutableFloatStateOf(0f) }
    var resultMessage by remember { mutableStateOf("") }
    var recordedPcm by remember { mutableStateOf<ShortArray?>(null) }

    val selectedWord = PRACTICE_WORDS[selectedWordIndex]

    // ── Recording animation ──
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // ── Star celebration animation ──
    val celebrationScale = remember { Animatable(0f) }
    val celebrationAlpha = remember { Animatable(0f) }

    LaunchedEffect(showResult) {
        if (showResult && resultStars > 0) {
            celebrationScale.snapTo(0f)
            celebrationAlpha.snapTo(0f)
            launch {
                celebrationScale.animateTo(
                    1.2f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
                celebrationScale.animateTo(1f, animationSpec = tween(200))
            }
            launch {
                celebrationAlpha.animateTo(1f, animationSpec = tween(400))
            }
        }
    }

    // ── Recording logic ──
    fun startRecording() {
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return

        isRecording = true
        showResult = false

        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(bufferSize, sampleRate * 2 * 2) // At least 2 seconds buffer
                )

                val totalSamples = sampleRate * 2 // 2 seconds of audio
                val pcmBuffer = ShortArray(totalSamples)
                var samplesRead = 0

                recorder.startRecording()

                while (samplesRead < totalSamples && isRecording) {
                    val toRead = minOf(1024, totalSamples - samplesRead)
                    val read = recorder.read(pcmBuffer, samplesRead, toRead)
                    if (read > 0) samplesRead += read
                }

                recorder.stop()
                recorder.release()

                val captured = if (samplesRead < totalSamples) {
                    pcmBuffer.copyOf(samplesRead)
                } else {
                    pcmBuffer
                }

                withContext(Dispatchers.Main) {
                    recordedPcm = captured
                    isRecording = false
                    isScoring = true
                }

                // ── Run DTW scoring ──
                val mfccExtractor = MfccExtractor()
                val childFeatures = mfccExtractor.extract(captured)

                // Load or generate reference template
                val refFeatures = loadReferenceTemplate(context, selectedWord, mfccExtractor)

                if (childFeatures.isEmpty() || refFeatures.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isScoring = false
                        resultStars = 0
                        resultScore = 0
                        resultDtw = 99f
                        resultMessage = "Could not process audio. Try speaking louder! 🔊"
                        showResult = true
                    }
                    return@launch
                }

                val dtwDistance = DtwMatcher.dtwDistance(refFeatures, childFeatures)

                val (score, stars) = computeScoreFromDtw(dtwDistance)
                val message = getMotivationalMessage(stars)

                withContext(Dispatchers.Main) {
                    resultDtw = dtwDistance.toFloat()
                    resultScore = score
                    resultStars = stars
                    resultMessage = message
                    isScoring = false
                    showResult = true

                    // Persist to database
                    viewModel.savePracticeResult(
                        word = selectedWord.english,
                        dtwDistance = dtwDistance.toFloat(),
                        score = score,
                        stars = stars
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SpeechPractice", "Recording/scoring failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isRecording = false
                    isScoring = false
                    resultStars = 0
                    resultScore = 0
                    resultDtw = 99f
                    resultMessage = "Something went wrong. Let's try again! 🔄"
                    showResult = true
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
    }

    // ═══════════════════════════════════════════════════════════
    // UI LAYOUT
    // ═══════════════════════════════════════════════════════════

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── HEADER ──
            Text(
                text = "🎮 Speech Practice",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Tap a word, hold the mic to speak, and earn stars!",
                fontSize = 13.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (activeProfile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Please create or activate a child profile first\nto start practicing! 🧒",
                        color = Slate400,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
                return@Column
            }

            // ── WORD SELECTOR (Horizontal LazyRow) ──
            Text(
                text = "Choose a word:",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate300,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                state = rememberLazyListState()
            ) {
                items(PRACTICE_WORDS.size) { index ->
                    val word = PRACTICE_WORDS[index]
                    val isSelected = index == selectedWordIndex

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                PracticeViolet.copy(alpha = 0.2f)
                            else
                                Slate800
                        ),
                        border = if (isSelected)
                            BorderStroke(2.dp, PracticeViolet)
                        else
                            BorderStroke(1.dp, Slate600.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .width(110.dp)
                            .clickable {
                                selectedWordIndex = index
                                showResult = false
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = word.emoji,
                                fontSize = 36.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = word.english,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) PracticeViolet else Color.White
                            )
                            Text(
                                text = word.arabic,
                                fontSize = 12.sp,
                                color = if (isSelected) PracticeViolet.copy(alpha = 0.8f) else Slate400
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── PRACTICE AREA (Central Card) ──
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Big emoji + word
                    Text(
                        text = selectedWord.emoji,
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedWord.arabic,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = selectedWord.english,
                        fontSize = 16.sp,
                        color = Slate400,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Reference speaker button ──
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                playReferenceAudio(context, selectedWord)
                            }
                        }
                    ) {
                        Text(
                            text = "🔊 Listen to example",
                            color = PracticeViolet,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Hold-to-Record Button ──
                    Box(contentAlignment = Alignment.Center) {
                        // Pulsing ring (only while recording)
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(PracticeViolet.copy(alpha = pulseAlpha * 0.3f))
                            )
                        }

                        // Main record button
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .shadow(
                                    elevation = if (isRecording) 16.dp else 8.dp,
                                    shape = CircleShape,
                                    ambientColor = PracticeViolet.copy(alpha = 0.3f),
                                    spotColor = PracticeViolet.copy(alpha = 0.5f)
                                )
                                .clip(CircleShape)
                                .background(
                                    brush = if (isRecording) Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFe879f9),
                                            PracticeVioletDark
                                        )
                                    ) else Brush.radialGradient(
                                        colors = listOf(
                                            PracticeViolet,
                                            PracticeVioletDark
                                        )
                                    )
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            if (!isScoring) {
                                                startRecording()
                                                // Wait for release
                                                val released = tryAwaitRelease()
                                                if (released) {
                                                    stopRecording()
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isRecording) "🔴" else "🎙️",
                                fontSize = 36.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isRecording) "Recording... release to score!"
                        else if (isScoring) "Scoring your pronunciation..."
                        else "Hold the mic and say the word!",
                        fontSize = 13.sp,
                        color = if (isRecording) PracticeViolet else Slate400,
                        textAlign = TextAlign.Center
                    )

                    // ── Scoring progress indicator ──
                    if (isScoring) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = PracticeViolet,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════
            // RESULT CARD (shown after scoring)
            // ═══════════════════════════════════════════════════
            if (showResult) {
                val scoreColor = getScoreColor(resultStars)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = scoreColor.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Star celebration
                        if (resultStars > 0) {
                            Text(
                                text = "🌟".repeat(resultStars),
                                fontSize = 40.sp,
                                modifier = Modifier
                                    .graphicsLayer(
                                        scaleX = celebrationScale.value,
                                        scaleY = celebrationScale.value,
                                        alpha = celebrationAlpha.value
                                    )
                            )
                        } else {
                            Text(
                                text = "🔄",
                                fontSize = 40.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Score percentage
                        Text(
                            text = "${resultScore}%",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Motivational message
                        Text(
                            text = resultMessage,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Try again button
                        Button(
                            onClick = { showResult = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scoreColor.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (resultStars >= 2) "Practice Again! 🎮" else "Try Again! 💪",
                                color = scoreColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // ═══════════════════════════════════════════════════
            // PROGRESS HISTORY (mini section)
            // ═══════════════════════════════════════════════════
            if (practiceStats.isNotEmpty()) {
                Text(
                    text = "📊 Recent Practice",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val recentStats = practiceStats.take(10)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentStats.forEach { stat ->
                        PracticeHistoryItem(stat)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PRACTICE HISTORY ITEM
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PracticeHistoryItem(stat: PracticeStats) {
    val scoreColor = getScoreColor(stat.stars)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Word emoji
            val wordEmoji = PRACTICE_WORDS.find { it.english == stat.word }?.emoji ?: "🗣️"
            Text(
                text = wordEmoji,
                fontSize = 22.sp,
                modifier = Modifier.padding(end = 10.dp)
            )

            // Word and timestamp
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.word,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = formatTimestamp(stat.timestamp),
                    fontSize = 11.sp,
                    color = Slate400
                )
            }

            // Stars
            if (stat.stars > 0) {
                Text(
                    text = "⭐".repeat(stat.stars),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Score badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scoreColor.copy(alpha = 0.15f))
                    .border(1.dp, scoreColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${stat.score}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Loads a reference MFCC template from assets (.wav or .mp3), falling back to a
 * synthetic tone-based template if the audio file is not found.
 *
 * Uses Android's MediaExtractor + MediaCodec pipeline to decode any supported
 * audio format (WAV, MP3, OGG, etc.) into raw 16-bit PCM for MFCC extraction.
 */
private fun loadReferenceTemplate(
    context: android.content.Context,
    word: PracticeWord,
    mfccExtractor: MfccExtractor
): Array<FloatArray> {
    // Try decoding from assets first
    try {
        val pcmData = decodeAudioAssetToPcm(context, word.assetFileName)
        if (pcmData != null && pcmData.isNotEmpty()) {
            val features = mfccExtractor.extract(pcmData)
            if (features.isNotEmpty()) return features
        }
    } catch (_: Exception) {
        // Asset not found or decode failed — fall through to synthetic template
    }

    // Generate a synthetic reference template (deterministic per word)
    return generateSyntheticTemplate(word, mfccExtractor)
}

/**
 * Decodes an audio asset file (.wav, .mp3, .ogg, etc.) into a raw 16kHz mono
 * PCM ShortArray using Android's MediaExtractor + MediaCodec pipeline.
 *
 * @param context Application context for accessing assets
 * @param assetPath Path within assets folder (e.g. "practice/water_ref.wav")
 * @return Decoded PCM ShortArray at the file's native sample rate, or null on failure
 */
private fun decodeAudioAssetToPcm(
    context: android.content.Context,
    assetPath: String
): ShortArray? {
    var extractor: MediaExtractor? = null
    var codec: MediaCodec? = null
    var afd: AssetFileDescriptor? = null

    try {
        afd = context.assets.openFd(assetPath)
        extractor = MediaExtractor()
        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)

        // Find the audio track
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        if (audioTrackIndex == -1 || audioFormat == null) return null

        extractor.selectTrack(audioTrackIndex)
        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null

        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        val pcmSamples = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var isEos = false

        while (!isEos) {
            // Feed input buffers
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    isEos = true
                } else {
                    codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            // Drain output buffers
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val shortBuf = outputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val shorts = ShortArray(shortBuf.remaining())
                    shortBuf.get(shorts)
                    for (s in shorts) pcmSamples.add(s)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEos = true
                    break
                }
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        return pcmSamples.toShortArray()
    } catch (e: Exception) {
        android.util.Log.e("SpeechPractice", "Failed to decode audio asset '$assetPath': ${e.message}")
        return null
    } finally {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        try { extractor?.release() } catch (_: Exception) {}
        try { afd?.close() } catch (_: Exception) {}
    }
}

/**
 * Generates a deterministic synthetic MFCC template based on the word's properties.
 * Each word gets a unique frequency so that scoring produces differentiated results.
 * This acts as a functional placeholder until real reference recordings are provided.
 */
private fun generateSyntheticTemplate(
    word: PracticeWord,
    mfccExtractor: MfccExtractor
): Array<FloatArray> {
    val sampleRate = 16000
    val durationSamples = sampleRate * 1 // 1 second
    val baseFrequency = 200.0 + (word.english.hashCode().and(0x7FFFFFFF) % 300) // 200–500 Hz

    val pcm = ShortArray(durationSamples) { i ->
        val t = i.toDouble() / sampleRate
        // Simple sine tone with harmonic to simulate vowel formant
        val sample = 0.5 * sin(2.0 * Math.PI * baseFrequency * t) +
                0.3 * sin(2.0 * Math.PI * baseFrequency * 2.0 * t) +
                0.1 * sin(2.0 * Math.PI * baseFrequency * 3.0 * t)
        (sample * 16000).toInt().coerceIn(-32768, 32767).toShort()
    }

    return mfccExtractor.extract(pcm)
}

/**
 * Plays the reference audio for a practice word using Android's MediaPlayer.
 * MediaPlayer natively supports .wav, .mp3, .ogg, and other common formats.
 * Falls back to a short sine tone if the asset file is not found.
 */
private fun playReferenceAudio(context: android.content.Context, word: PracticeWord) {
    try {
        val afd = context.assets.openFd(word.assetFileName)
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
        afd.close()
        mediaPlayer.setOnCompletionListener { it.release() }
        mediaPlayer.prepare()
        mediaPlayer.start()
    } catch (_: Exception) {
        // Asset not found — generate and play a short placeholder tone via AudioTrack
        try {
            val sampleRate = 16000
            val duration = sampleRate / 2 // 0.5 seconds
            val freq = 440.0
            val pcm = ShortArray(duration) { i ->
                val t = i.toDouble() / sampleRate
                val envelope = if (i < 800) i.toFloat() / 800f
                else if (i > duration - 800) (duration - i).toFloat() / 800f
                else 1f
                (sin(2.0 * Math.PI * freq * t) * 12000 * envelope).toInt().coerceIn(-32768, 32767).toShort()
            }
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufSize, pcm.size * 2),
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(pcm, 0, pcm.size)
            audioTrack.play()
        } catch (e: Exception) {
            android.util.Log.e("SpeechPractice", "Failed to play fallback tone: ${e.message}")
        }
    }
}

/**
 * Formats a timestamp for the practice history display.
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

// ═══════════════════════════════════════════════════════════════
// PREVIEW
// ═══════════════════════════════════════════════════════════════

private class MockPracticeViewModel : IVoxaViewModel {
    override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
    override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(
        com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true)
    )
    override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
    override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<LogEvent>())
    override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
    override val practiceStats = kotlinx.coroutines.flow.MutableStateFlow(
        listOf(
            PracticeStats(id = 1, profileId = 1, word = "Water", dtwDistance = 3.2f, score = 100, stars = 3),
            PracticeStats(id = 2, profileId = 1, word = "Milk", dtwDistance = 5.0f, score = 80, stars = 2),
            PracticeStats(id = 3, profileId = 1, word = "Help", dtwDistance = 6.5f, score = 60, stars = 1)
        )
    )

    override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
    override fun selectActiveProfile(profileId: Long) {}
    override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
    override fun exportProfileData(context: android.content.Context) {}
    override fun importProfileData(context: android.content.Context, uri: android.net.Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {}
    override fun deleteIntent(intent: com.example.voxa.data.EnrolledIntent) {}
    override fun toggleListening() {}
    override fun updateListeningState() {}
    override fun addLogSystemEvent(message: String) {}
    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
    override fun clearLogs() {}
    override fun playRecordedSample(intent: com.example.voxa.data.EnrolledIntent) {}
    override fun savePracticeResult(word: String, dtwDistance: Float, score: Int, stars: Int) {}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Speech Practice Screen")
@Composable
fun SpeechPracticeScreenPreview() {
    com.example.voxa.ui.theme.VoxaTheme {
        SpeechPracticeScreen(viewModel = MockPracticeViewModel())
    }
}
