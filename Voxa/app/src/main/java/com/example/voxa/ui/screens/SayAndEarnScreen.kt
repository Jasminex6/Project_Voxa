package com.example.voxa.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voxa.R
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import com.example.voxa.utils.AudioPlayer
import com.example.voxa.utils.FuzzyMatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Word data model for the Say & Earn reward module.
 */
data class SayAndEarnWord(
    val english: String,
    val arabic: String,
    val emoji: String,
    val englishAliases: List<String> = emptyList(),
    val arabicAliases: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val audioAssetPath: String? = null
) {
    fun getAliases(language: String): List<String> {
        val isEn = language.equals("en", ignoreCase = true)
        return if (isEn) {
            if (englishAliases.isNotEmpty()) englishAliases else aliases.filter { it.none { c -> c in '\u0600'..'\u06FF' } }
        } else {
            if (arabicAliases.isNotEmpty()) arabicAliases else aliases.filter { it.any { c -> c in '\u0600'..'\u06FF' } }
        }
    }
}

val DEFAULT_SAY_AND_EARN_WORDS = listOf(
    SayAndEarnWord(
        english = "Water",
        arabic = "مايه",
        emoji = "💧",
        englishAliases = listOf("water", "wawa", "watr", "wata", "a water"),
        arabicAliases = listOf("مية", "ماية", "ماء", "مي", "مويه", "امبو", "مبو", "واوا"),
        audioAssetPath = "water.mp3"
    ),
    SayAndEarnWord(
        english = "Milk",
        arabic = "لبن",
        emoji = "🥛",
        englishAliases = listOf("milk", "milky", "mimi", "milkk"),
        arabicAliases = listOf("حليب", "اللبن", "لبنو", "مم"),
        audioAssetPath = "milk.mp3"
    ),
    SayAndEarnWord(
        english = "Bread",
        arabic = "عيش",
        emoji = "🍞",
        englishAliases = listOf("bread", "toast", "bun"),
        arabicAliases = listOf("خبز", "العيش", "عيشو", "نمنم"),
        audioAssetPath = "bread.mp3"
    ),
    SayAndEarnWord(
        english = "Help",
        arabic = "مساعدة",
        emoji = "🆘",
        englishAliases = listOf("help", "help me", "sos", "please"),
        arabicAliases = listOf("ساعدني", "مساعده", "الحقني", "انقذني"),
        audioAssetPath = "help.mp3"
    ),
    SayAndEarnWord(
        english = "Mom",
        arabic = "ماما",
        emoji = "👩",
        englishAliases = listOf("mom", "mama", "mommy", "mother", "mum"),
        arabicAliases = listOf("امي", "ام", "أم", "مامي", "ماما"),
        audioAssetPath = "mama.mp3"
    ),
    SayAndEarnWord(
        english = "Dad",
        arabic = "بابا",
        emoji = "👨",
        englishAliases = listOf("dad", "daddy", "father", "papa", "baba"),
        arabicAliases = listOf("ابي", "اب", "أب", "بابي", "بابا"),
        audioAssetPath = "baba.mp3"
    ),
    SayAndEarnWord(
        english = "Food",
        arabic = "أكل",
        emoji = "🍎",
        englishAliases = listOf("food", "eat", "eating", "yummy", "hungry"),
        arabicAliases = listOf("اكل", "طعام", "هم", "مم", "نمنم", "عايز اكل"),
        audioAssetPath = null
    ),
    SayAndEarnWord(
        english = "Bathroom",
        arabic = "حمام",
        emoji = "🚽",
        englishAliases = listOf("bathroom", "toilet", "restroom", "potty", "pee"),
        arabicAliases = listOf("الحمام", "تواليت", "بيبي", "حمام"),
        audioAssetPath = null
    ),
    SayAndEarnWord(
        english = "Sleep",
        arabic = "نام",
        emoji = "🛌",
        englishAliases = listOf("sleep", "bed", "sleepy", "nap", "sleeping"),
        arabicAliases = listOf("انام", "نوم", "نعسان", "توتو"),
        audioAssetPath = null
    )
)

/**
 * 🪙 SayAndEarnScreen — Gamified Pronunciation Reward System
 *
 * Flow:
 * 1. Word is displayed with child-friendly emoji and Arabic/English text.
 * 2. Word is spoken aloud via local human voice packs or Egyptian Arabic TTS.
 * 3. Child taps mic and tries to say/approximate the word.
 * 4. Android SpeechRecognizer transcribes speech locally (offline-preferred).
 * 5. FuzzyMatcher evaluates transcription using Levenshtein edit distance & normalization.
 * 6. Correct/close speech awards coins (stars) directly into the child's wallet for the Rewards Store!
 */
@Composable
fun SayAndEarnScreen(
    viewModel: IVoxaViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val enrolledIntents by viewModel.enrolledIntents.collectAsState()
    val childPoints by viewModel.childPoints.collectAsState()

    // Combined vocabulary: default core words + child's custom enrolled words
    val allWords = remember(enrolledIntents) {
        val customWords = enrolledIntents.map { intent ->
            SayAndEarnWord(
                english = intent.outputPhrase,
                arabic = intent.intentName,
                emoji = "🗣️",
                aliases = listOf(intent.outputPhrase),
                audioAssetPath = intent.audioAssetPath
            )
        }
        (DEFAULT_SAY_AND_EARN_WORDS + customWords).distinctBy { it.arabic }
    }

    var selectedWordIndex by remember { mutableIntStateOf(0) }
    val currentWord = allWords.getOrElse(selectedWordIndex) { DEFAULT_SAY_AND_EARN_WORDS[0] }

    var selectedLanguage by remember { mutableStateOf("ar") } // "ar" for Egyptian Arabic, "en" for English

    // Audio & Speech recognition states
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var partialSpokenText by remember { mutableStateOf("") }
    var matchResult by remember { mutableStateOf<FuzzyMatcher.MatchResult?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var streakCount by remember { mutableIntStateOf(0) }
    var micRmsLevel by remember { mutableFloatStateOf(0f) }
    var bonusAwarded by remember { mutableStateOf(false) }

    // Audio Player instance for authentic pronunciation playback
    val audioPlayer = remember { AudioPlayer(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    // Play word audio helper (authentic English or Arabic TTS/audio)
    fun playWordAudio() {
        val gender = activeProfile?.gender ?: "Male"
        if (selectedLanguage == "en") {
            // Authentic English Text-To-Speech
            audioPlayer.speakWord(text = currentWord.english, language = "en", gender = gender)
        } else {
            // Arabic pronunciation (prefer recorded audio asset if available, else Arabic TTS)
            if (currentWord.audioAssetPath != null) {
                audioPlayer.playTranslation(currentWord.audioAssetPath, gender, currentWord.arabic)
            } else {
                audioPlayer.speakWord(text = currentWord.arabic, language = "ar", gender = gender)
            }
        }
    }

    // Floating coin celebration animation
    val coinAnimY = remember { Animatable(0f) }
    val coinAnimAlpha = remember { Animatable(0f) }
    val coinAnimScale = remember { Animatable(1f) }

    fun triggerCoinBurst() {
        coroutineScope.launch {
            coinAnimY.snapTo(0f)
            coinAnimAlpha.snapTo(1f)
            coinAnimScale.snapTo(0.6f)
            launch {
                coinAnimScale.animateTo(
                    targetValue = 1.3f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            }
            launch {
                coinAnimY.animateTo(
                    targetValue = -90f,
                    animationSpec = tween(700, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(400)
                coinAnimAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(300)
                )
            }
        }
    }

    // SpeechRecognizer instance
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                android.util.Log.w("SayAndEarn", "Error destroying speech recognizer on dispose: ${e.message}")
            }
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            android.util.Log.w("SayAndEarn", "Error stopping listening: ${e.message}")
        }
        isListening = false
    }

    fun startListeningInternal() {
        // 1. If background listening service is holding the microphone, pause it
        if (com.example.voxa.services.VoxaListenerService.isRunning) {
            viewModel.toggleListening()
            Toast.makeText(context, "تم إيقاف خدمة الاستماع مؤقتاً للتمرين 🎙️", Toast.LENGTH_SHORT).show()
        }

        // 2. Re-create a fresh SpeechRecognizer instance to avoid ERROR_RECOGNIZER_BUSY
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            android.util.Log.w("SayAndEarn", "Cleaned up old recognizer: ${e.message}")
        }

        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
        speechRecognizer = recognizer

        if (recognizer == null) {
            Toast.makeText(context, "Speech recognition is not available on this device", Toast.LENGTH_SHORT).show()
            matchResult = FuzzyMatcher.MatchResult(
                isMatched = false,
                isExact = false,
                editDistance = 999,
                bestCandidate = "",
                coinsEarned = 0,
                feedbackMessage = if (selectedLanguage == "ar") "خدمة التعرف على الصوت غير متاحة على هذا الجهاز" else "Speech recognition service not available"
            )
            showResult = true
            return
        }

        showResult = false
        matchResult = null
        partialSpokenText = ""
        isListening = true
        isProcessing = false
        bonusAwarded = false

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("SayAndEarn", "SpeechRecognizer: Ready for speech")
                isListening = true
                isProcessing = false
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.d("SayAndEarn", "SpeechRecognizer: Beginning of speech")
                isProcessing = false
            }

            override fun onRmsChanged(rmsdB: Float) {
                micRmsLevel = (rmsdB / 10f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                android.util.Log.d("SayAndEarn", "SpeechRecognizer: End of speech")
                isListening = false
                isProcessing = true
            }

            override fun onError(error: Int) {
                val errorName = getSpeechErrorName(error)
                android.util.Log.e("SayAndEarn", "SpeechRecognizer onError: $error ($errorName)")
                isListening = false
                isProcessing = false

                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        if (selectedLanguage == "ar") "لم نتمكن من تمييز الكلمة. حاول نطقها بوضوح! 🗣️" else "Could not recognize word. Speak clearly! 🗣️"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (selectedLanguage == "ar") "لم نسمع صوتاً. اضغط على المايك وتحدث! 🎙️" else "No speech heard before timeout. Try again! 🎙️"
                    SpeechRecognizer.ERROR_AUDIO ->
                        if (selectedLanguage == "ar") "مشكلة بالمايكروفون. تأكد من إذن المايك وعدم انشغاله! 🎙️" else "Microphone audio error. Check mic permissions! 🎙️"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        context.getString(R.string.say_and_earn_mic_permission)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        if (selectedLanguage == "ar") "يلزم اتصال بالإنترنت للتعرف على الصوت 🌐" else "Internet connection required for speech recognition 🌐"
                    SpeechRecognizer.ERROR_SERVER ->
                        if (selectedLanguage == "ar") "خطأ بخادم الصوت. تحقق من اتصال الإنترنت 🌐" else "Speech server error. Check internet connection 🌐"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        if (selectedLanguage == "ar") "الميكروفون مشغول. جاري إعادة التعيين... اضغط مجدداً ⏳" else "Recognizer busy. Resetting... tap mic again ⏳"
                    SpeechRecognizer.ERROR_CLIENT ->
                        if (selectedLanguage == "ar") "حدث خطأ في النظام. اضغط مجدداً للمحاولة 🔄" else "Client error. Tap mic again to retry 🔄"
                    else ->
                        if (selectedLanguage == "ar") "لم نسمع بوضوح (كود $errorName). حاول مجدداً! 🎙️" else "Could not recognize ($errorName). Try again! 🎙️"
                }

                matchResult = FuzzyMatcher.MatchResult(
                    isMatched = false,
                    isExact = false,
                    editDistance = 999,
                    bestCandidate = "",
                    coinsEarned = 0,
                    feedbackMessage = errorMsg
                )
                showResult = true
                streakCount = 0
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                isProcessing = false
                val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                android.util.Log.d("SayAndEarn", "SpeechRecognizer onResults candidates: $candidates")

                if (candidates.isEmpty()) {
                    matchResult = FuzzyMatcher.MatchResult(
                        isMatched = false,
                        isExact = false,
                        editDistance = 999,
                        bestCandidate = "",
                        coinsEarned = 0,
                        feedbackMessage = if (selectedLanguage == "ar") "لم يتم التقاط أي كلمة. حاول مجدداً! 🎙️" else "No word detected. Try again! 🎙️"
                    )
                    showResult = true
                    return
                }

                // Evaluate against target word and aliases corresponding to selected language
                val isEn = selectedLanguage == "en"
                val primaryTarget = if (isEn) currentWord.english else currentWord.arabic
                val targetAliases = currentWord.getAliases(selectedLanguage)

                val result = FuzzyMatcher.matchWord(
                    candidates = candidates,
                    primaryTarget = primaryTarget,
                    aliases = targetAliases
                )

                android.util.Log.d("SayAndEarn", "Match result ($selectedLanguage): isMatched=${result.isMatched}, isExact=${result.isExact}, percent=${result.matchPercent}%, stars=${result.starsEarned}, best='${result.bestCandidate}'")

                matchResult = result
                showResult = true

                if (result.isMatched) {
                    var totalCoins = result.coinsEarned
                    streakCount += 1

                    // Streak bonus every 3 consecutive matches
                    if (streakCount % 3 == 0) {
                        totalCoins += 2
                        bonusAwarded = true
                    }

                    // Persist coins in child profile wallet
                    viewModel.addPracticeCoins(totalCoins)
                    viewModel.recordPracticeAttempt(
                        word = currentWord.english,
                        score = result.matchPercent.toInt(),
                        stars = result.starsEarned
                    )

                    // Trigger celebration
                    triggerCoinBurst()
                } else {
                    streakCount = 0
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    partialSpokenText = matches[0]
                    android.util.Log.d("SayAndEarn", "Partial speech: ${matches[0]}")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val isEn = selectedLanguage == "en"
        val langTag = if (isEn) "en-US" else "ar-EG"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            if (isEn) {
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "en-GB", "en"))
            } else {
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ar-EG", "ar-SA", "ar"))
            }
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // DO NOT set EXTRA_PREFER_OFFLINE so network/offline hybrid works seamlessly
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            android.util.Log.e("SayAndEarn", "Error starting speech recognizer: ${e.message}", e)
            Toast.makeText(context, "Could not start voice recognizer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningInternal()
        } else {
            Toast.makeText(context, context.getString(R.string.say_and_earn_mic_permission), Toast.LENGTH_LONG).show()
        }
    }

    fun handleMicTap() {
        if (isListening) {
            stopListening()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                startListeningInternal()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Pulse animation for recording ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── TOP BAR / HEADER ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = if (selectedLanguage == "en") "Say & Earn 🪙" else stringResource(R.string.say_and_earn_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedLanguage == "en") "Listen to the word, say it clearly, and earn coins!" else stringResource(R.string.say_and_earn_desc),
                            fontSize = 12.sp,
                            color = Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 🪙 Live Animated Child Wallet Badge (Non-wrapping single line)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = QuestGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, QuestGold.copy(alpha = 0.6f)),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$childPoints",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = QuestGold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // ── LANGUAGE SELECTOR ROW ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language toggle pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate800)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedLanguage == "ar") QuestTeal else Color.Transparent)
                            .clickable {
                                if (selectedLanguage != "ar") {
                                    stopListening()
                                    selectedLanguage = "ar"
                                    showResult = false
                                    matchResult = null
                                    partialSpokenText = ""
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "🇪🇬 عربي",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedLanguage == "ar") Color.White else Slate400
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedLanguage == "en") Sky500 else Color.Transparent)
                            .clickable {
                                if (selectedLanguage != "en") {
                                    stopListening()
                                    selectedLanguage = "en"
                                    showResult = false
                                    matchResult = null
                                    partialSpokenText = ""
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "🇬🇧 English",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedLanguage == "en") Color.White else Slate400
                        )
                    }
                }
            }

            // Streak indicator banner
            if (streakCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = QuestOrange.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, QuestOrange.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.say_and_earn_streak, streakCount),
                            color = QuestOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (bonusAwarded) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.say_and_earn_streak_bonus),
                                color = QuestGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ── WORD SELECTOR (Horizontal Row) ──
            Text(
                text = if (selectedLanguage == "en") "Choose a word to practice:" else stringResource(R.string.speech_practice_choose_word),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate300,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                state = rememberLazyListState()
            ) {
                itemsIndexed(allWords) { index, word ->
                    val isSelected = index == selectedWordIndex

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) QuestTeal.copy(alpha = 0.2f) else Slate800
                        ),
                        border = if (isSelected)
                            BorderStroke(2.dp, QuestTeal)
                        else
                            BorderStroke(1.dp, Slate700),
                        modifier = Modifier
                            .width(100.dp)
                            .clickable {
                                selectedWordIndex = index
                                showResult = false
                                matchResult = null
                                partialSpokenText = ""
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = word.emoji, fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedLanguage == "en") word.english else word.arabic,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) QuestTeal else Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (selectedLanguage == "en") word.arabic else word.english,
                                fontSize = 11.sp,
                                color = Slate400,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── CENTRAL MAIN PRACTICE CARD ──
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                border = BorderStroke(1.5.dp, Slate700),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Word representation (adapts dynamically to selected language)
                    Text(text = currentWord.emoji, fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedLanguage == "en") currentWord.english else currentWord.arabic,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = if (selectedLanguage == "en") currentWord.arabic else currentWord.english,
                        fontSize = 18.sp,
                        color = Slate400
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🔊 "Listen to example" Button
                    Button(
                        onClick = { playWordAudio() },
                        colors = ButtonDefaults.buttonColors(containerColor = QuestTeal.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, QuestTeal.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Listen",
                            tint = QuestTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedLanguage == "en") "Listen to example" else stringResource(R.string.speech_practice_listen_example),
                            color = QuestTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 🎙️ BIG INTERACTIVE MICROPHONE BUTTON WITH FLOATING COIN CELEBRATION
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(150.dp)
                    ) {
                        // Pulsing outer ripple when listening
                        if (isListening) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .scale(pulseScale + micRmsLevel * 0.2f)
                                    .clip(CircleShape)
                                    .background(QuestOrange.copy(alpha = pulseAlpha))
                            )
                        }

                        // Floating coin burst animation
                        if (coinAnimAlpha.value > 0f) {
                            val earnedStars = matchResult?.coinsEarned ?: 5
                            val burstTotal = earnedStars + if (bonusAwarded) 2 else 0
                            Text(
                                text = if (bonusAwarded) "+$burstTotal ⭐🔥" else "+$burstTotal ⭐",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = QuestGold,
                                modifier = Modifier
                                    .graphicsLayer {
                                        translationY = coinAnimY.value
                                        alpha = coinAnimAlpha.value
                                        scaleX = coinAnimScale.value
                                        scaleY = coinAnimScale.value
                                    }
                            )
                        }

                        // The Mic button
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .shadow(
                                    elevation = if (isListening) 16.dp else 6.dp,
                                    shape = CircleShape,
                                    ambientColor = if (isListening) QuestOrange else QuestTeal,
                                    spotColor = if (isListening) QuestOrange else QuestTeal
                                )
                                .clip(CircleShape)
                                .background(
                                    brush = if (isListening) {
                                        Brush.radialGradient(listOf(QuestOrange, Color(0xFFc2410c)))
                                    } else {
                                        Brush.radialGradient(listOf(QuestTeal, Sky500))
                                    }
                                )
                                .clickable { handleMicTap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when {
                            isListening -> if (selectedLanguage == "en") "Listening... Speak in English! 🎙️" else stringResource(R.string.say_and_earn_listening)
                            isProcessing -> if (selectedLanguage == "en") "Checking pronunciation... ✨" else stringResource(R.string.say_and_earn_processing)
                            else -> if (selectedLanguage == "en") "Tap mic & say the word!" else stringResource(R.string.say_and_earn_tap_to_speak)
                        },
                        fontSize = 14.sp,
                        color = if (isListening) QuestOrange else Slate300,
                        fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )

                    // Partial speech transcription live indicator
                    if (partialSpokenText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"$partialSpokenText\"",
                            fontSize = 13.sp,
                            color = Slate400,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── RESULT / REWARD FEEDBACK CARD ──
            AnimatedVisibility(
                visible = showResult && matchResult != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                val result = matchResult ?: return@AnimatedVisibility
                val isSuccess = result.isMatched

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isSuccess) SuccessGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (result.starsEarned) {
                                5 -> "⭐⭐⭐⭐⭐"
                                4 -> "⭐⭐⭐⭐"
                                3 -> "⭐⭐⭐"
                                1 -> "⭐"
                                else -> "💪"
                            },
                            fontSize = 36.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = when (result.starsEarned) {
                                5 -> if (selectedLanguage == "ar") "نطق مثالي ومطابق 100%! 🏆" else "Awesome! Exact match! 🏆"
                                4 -> if (selectedLanguage == "ar") "نطق ممتاز جداً! 🌟🌟" else "Excellent pronunciation! 🌟"
                                3 -> if (selectedLanguage == "ar") "أحسنت! قريب جداً! 🌟" else "Great effort! Very close! 🌟"
                                1 -> if (selectedLanguage == "ar") "محاولة جيدة! استمر! ⭐" else "Good try! Keep practicing! ⭐"
                                else -> if (selectedLanguage == "ar") "حاول مرة أخرى! يمكنك فعلها! 💪" else "Keep trying! You can do it! 💪"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSuccess) SuccessGreen else Color.White,
                            textAlign = TextAlign.Center
                        )

                        // Match percentage chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSuccess) SuccessGreen.copy(alpha = 0.15f) else Slate700,
                            border = BorderStroke(1.dp, if (isSuccess) SuccessGreen.copy(alpha = 0.4f) else Slate600),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(
                                text = if (selectedLanguage == "ar")
                                    "نسبة التطابق: ${result.matchPercent.toInt()}%"
                                else
                                    "Match: ${result.matchPercent.toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) SuccessGreen else Slate300,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (result.bestCandidate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.say_and_earn_heard, result.bestCandidate),
                                fontSize = 13.sp,
                                color = Slate400,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showResult = false
                                    handleMicTap()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Slate600),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (selectedLanguage == "en") "Try Again 🔄" else stringResource(R.string.say_and_earn_try_again),
                                    color = Slate300,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    showResult = false
                                    matchResult = null
                                    partialSpokenText = ""
                                    selectedWordIndex = (selectedWordIndex + 1) % allWords.size
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSuccess) QuestTeal else Slate700
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (selectedLanguage == "en") "Next Word ➡️" else stringResource(R.string.say_and_earn_next_word),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Offline reminder tip footer
            Text(
                text = if (selectedLanguage == "en")
                    "💡 Tip: Android Speech Recognition works offline when the English (US) offline speech package is installed in device settings."
                else
                    stringResource(R.string.say_and_earn_offline_tip),
                fontSize = 11.sp,
                color = Slate500,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

private fun getSpeechErrorName(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        10 -> "ERROR_TOO_MANY_REQUESTS"
        11 -> "ERROR_SERVER_DISCONNECTED"
        12 -> "ERROR_LANGUAGE_UNAVAILABLE"
        13 -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        14 -> "ERROR_CANNOT_CHECK_SUPPORT"
        else -> "UNKNOWN_$error"
    }
}

// ═══════════════════════════════════════════════════════════════
// PREVIEW
// ═══════════════════════════════════════════════════════════════

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Say and Earn Screen")
@Composable
fun SayAndEarnScreenPreview() {
    VoxaTheme {
        SayAndEarnScreen(viewModel = object : IVoxaViewModel {
            override val allProfiles = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.ChildProfile>())
            override val activeProfile = kotlinx.coroutines.flow.MutableStateFlow(
                com.example.voxa.data.ChildProfile(name = "Adam", gender = "Male", isActive = true)
            )
            override val enrolledIntents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.EnrolledIntent>())
            override val practiceStats = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.data.PracticeStats>())
            override val isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val recentEvents = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.voxa.ui.LogEvent>())
            override val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
            override val childPoints = kotlinx.coroutines.flow.MutableStateFlow(42)

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
        })
    }
}

