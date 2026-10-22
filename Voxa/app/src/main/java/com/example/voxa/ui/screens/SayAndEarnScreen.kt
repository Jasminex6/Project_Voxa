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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val prefs = remember { context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE) }
    var micMode by remember { mutableStateOf(prefs.getString("mic_mode", "hold") ?: "hold") }

    // Audio Player instance for authentic pronunciation playback
    val audioPlayer = remember { AudioPlayer(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    // Play word audio helper (authentic English or Arabic TTS/audio)
    fun playWordAudio() {
        val word = allWords.getOrElse(selectedWordIndex) { DEFAULT_SAY_AND_EARN_WORDS[0] }
        val gender = activeProfile?.gender ?: "Male"
        if (selectedLanguage == "en") {
            // Authentic English Text-To-Speech
            audioPlayer.speakWord(text = word.english, language = "en", gender = gender)
        } else {
            // Arabic pronunciation (prefer recorded audio asset if available, else Arabic TTS)
            if (word.audioAssetPath != null) {
                audioPlayer.playTranslation(word.audioAssetPath, gender, word.arabic)
            } else {
                audioPlayer.speakWord(text = word.arabic, language = "ar", gender = gender)
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

    // Track whether the background listening service was active when entering Practice
    var wasServiceRunningBeforePractice by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (com.example.voxa.services.VoxaListenerService.isRunning) {
            wasServiceRunningBeforePractice = true
            viewModel.toggleListening() // Pause background listener so Practice has exclusive mic access
        }
        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                android.util.Log.w("SayAndEarn", "Error destroying speech recognizer on dispose: ${e.message}")
            }
            // Automatically resume background listening when returning to Home/Main if it was active before
            if (wasServiceRunningBeforePractice && !com.example.voxa.services.VoxaListenerService.isRunning) {
                viewModel.toggleListening()
            }
        }
    }

    fun stopListening() {
        try {
            if (isListening) {
                isProcessing = true
                coroutineScope.launch {
                    delay(5000)
                    if (isProcessing) {
                        isProcessing = false
                    }
                }
            }
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            android.util.Log.w("SayAndEarn", "Error stopping listening: ${e.message}")
            isProcessing = false
        }
        isListening = false
        micRmsLevel = 0f
    }

    fun startListeningInternal() {
        // 1. If background listening service is holding the microphone, pause it and wait for AudioRecord to close
        if (com.example.voxa.services.VoxaListenerService.isRunning) {
            wasServiceRunningBeforePractice = true
            viewModel.toggleListening()
            Toast.makeText(context, "تم إيقاف خدمة الاستماع مؤقتاً للتمرين 🎙️", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                delay(350)
                startListeningInternal()
            }
            return
        }

        // 2. Re-create a fresh SpeechRecognizer instance to avoid ERROR_RECOGNIZER_BUSY
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            android.util.Log.w("SayAndEarn", "Cleaned up old recognizer: ${e.message}")
        }
        speechRecognizer = null

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

        // Dynamically capture target word and language at start of recognition
        val targetWord = allWords.getOrElse(selectedWordIndex) { DEFAULT_SAY_AND_EARN_WORDS[0] }
        val targetLang = selectedLanguage

        showResult = false
        matchResult = null
        partialSpokenText = ""
        isListening = true
        isProcessing = false
        bonusAwarded = false

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("SayAndEarn", "SpeechRecognizer: Ready for speech (target: ${targetWord.english})")
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
                micRmsLevel = 0f
            }

            override fun onError(error: Int) {
                val errorName = getSpeechErrorName(error)
                android.util.Log.e("SayAndEarn", "SpeechRecognizer onError: $error ($errorName) for target: ${targetWord.english}")
                isListening = false
                isProcessing = false
                micRmsLevel = 0f

                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        if (targetLang == "ar") "لم نتمكن من تمييز الكلمة. حاول نطقها بوضوح! 🗣️" else "Could not recognize word. Speak clearly! 🗣️"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (targetLang == "ar") "لم نسمع صوتاً. اضغط على المايك وتحدث! 🎙️" else "No speech heard before timeout. Try again! 🎙️"
                    SpeechRecognizer.ERROR_AUDIO ->
                        if (targetLang == "ar") "مشكلة بالمايكروفون. تأكد من إذن المايك وعدم انشغاله! 🎙️" else "Microphone audio error. Check mic permissions! 🎙️"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        context.getString(R.string.say_and_earn_mic_permission)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        if (targetLang == "ar") "يلزم اتصال بالإنترنت للتعرف على الصوت 🌐" else "Internet connection required for speech recognition 🌐"
                    SpeechRecognizer.ERROR_SERVER ->
                        if (targetLang == "ar") "خطأ بخادم الصوت. تحقق من اتصال الإنترنت 🌐" else "Speech server error. Check internet connection 🌐"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        if (targetLang == "ar") "الميكروفون مشغول. جاري إعادة التعيين... اضغط مجدداً ⏳" else "Recognizer busy. Resetting... tap mic again ⏳"
                    SpeechRecognizer.ERROR_CLIENT ->
                        if (targetLang == "ar") "حدث خطأ في النظام. اضغط مجدداً للمحاولة 🔄" else "Client error. Tap mic again to retry 🔄"
                    else ->
                        if (targetLang == "ar") "لم نسمع بوضوح (كود $errorName). حاول مجدداً! 🎙️" else "Could not recognize ($errorName). Try again! 🎙️"
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
                micRmsLevel = 0f
                val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                android.util.Log.d("SayAndEarn", "SpeechRecognizer onResults candidates: $candidates for target: ${targetWord.english}/${targetWord.arabic}")

                if (candidates.isEmpty()) {
                    matchResult = FuzzyMatcher.MatchResult(
                        isMatched = false,
                        isExact = false,
                        editDistance = 999,
                        bestCandidate = "",
                        coinsEarned = 0,
                        feedbackMessage = if (targetLang == "ar") "لم يتم التقاط أي كلمة. حاول مجدداً! 🎙️" else "No word detected. Try again! 🎙️"
                    )
                    showResult = true
                    return
                }

                // Evaluate against target word and aliases corresponding to selected language + cross-language support
                val isEn = targetLang == "en"
                val primaryTarget = if (isEn) targetWord.english else targetWord.arabic
                val primaryAliases = targetWord.getAliases(targetLang)
                val secondaryAliases = targetWord.getAliases(if (isEn) "ar" else "en")
                val crossLanguageWord = if (isEn) targetWord.arabic else targetWord.english
                val combinedAliases = (primaryAliases + secondaryAliases + listOf(crossLanguageWord)).distinct()

                val result = FuzzyMatcher.matchWord(
                    candidates = candidates,
                    primaryTarget = primaryTarget,
                    aliases = combinedAliases
                )

                android.util.Log.d("SayAndEarn", "Match result: isMatched=${result.isMatched}, isExact=${result.isExact}, percent=${result.matchPercent}%, stars=${result.starsEarned}, best='${result.bestCandidate}'")

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
                        word = targetWord.english,
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

        val isEn = targetLang == "en"
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

    // Infinite ripple animations for listening button (replicated from Home tab)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Ripple 1
    val rippleScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale1"
    )
    val rippleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha1"
    )

    // Ripple 2
    val rippleScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale2"
    )
    val rippleAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha2"
    )

    // Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalVoxaColors.current.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── TOP BAR / HEADER ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LocalVoxaColors.current.primaryText)
                        }
                    }
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.say_and_earn_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LocalVoxaColors.current.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.say_and_earn_desc),
                            fontSize = 12.sp,
                            color = LocalVoxaColors.current.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 🪙 Live Animated Child Wallet Badge
                val walletGold = if (LocalVoxaColors.current.isDark) QuestGold else QuestGoldLight
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = walletGold.copy(alpha = if (LocalVoxaColors.current.isDark) 0.15f else 0.10f),
                    border = BorderStroke(1.5.dp, walletGold.copy(alpha = if (LocalVoxaColors.current.isDark) 0.6f else 0.48f)),
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
                            color = walletGold,
                            maxLines = 1,
                            softWrap = false
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
                        .padding(bottom = 8.dp)
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
                text = stringResource(R.string.speech_practice_choose_word),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalVoxaColors.current.secondaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                state = rememberLazyListState()
            ) {
                itemsIndexed(allWords) { index, word ->
                    val isSelected = index == selectedWordIndex

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) QuestTeal.copy(alpha = 0.2f) else LocalVoxaColors.current.normalCard
                        ),
                        border = if (isSelected)
                            BorderStroke(2.dp, QuestTeal)
                        else
                            BorderStroke(1.dp, LocalVoxaColors.current.glassBorder),
                        modifier = Modifier
                            .width(88.dp)
                            .clickable {
                                stopListening()
                                try {
                                    speechRecognizer?.cancel()
                                } catch (e: Exception) {}
                                selectedWordIndex = index
                                showResult = false
                                matchResult = null
                                partialSpokenText = ""
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = word.emoji, fontSize = 26.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = word.arabic,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) QuestTeal else LocalVoxaColors.current.primaryText,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Text(
                                text = word.english,
                                fontSize = 10.sp,
                                color = LocalVoxaColors.current.secondaryText,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── CENTRAL AREA: Practice card with result overlay ──
            // This Box uses weight(1f) to fill remaining space,
            // keeping the mic button area in a stable position.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (showResult && matchResult != null) {
                    val result = matchResult!!
                    val isSuccess = result.isMatched

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = LocalVoxaColors.current.normalCard
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (isSuccess) SuccessGreen.copy(alpha = 0.6f) else ErrorRed.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                fontSize = 28.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = when (result.starsEarned) {
                                    5 -> "نطق مثالي ومطابق 100%! 🏆"
                                    4 -> "نطق ممتاز جداً! 🌟🌟"
                                    3 -> "أحسنت! قريب جداً! 🌟"
                                    1 -> "محاولة جيدة! استمر! ⭐"
                                    else -> "حاول مرة أخرى! يمكنك فعلها! 💪"
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSuccess) SuccessGreen else LocalVoxaColors.current.primaryText,
                                textAlign = TextAlign.Center
                            )

                            // Match percentage chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSuccess) SuccessGreen.copy(alpha = 0.15f) else LocalVoxaColors.current.surfaceVariant,
                                border = BorderStroke(1.dp, if (isSuccess) SuccessGreen.copy(alpha = 0.4f) else LocalVoxaColors.current.inputBorder),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "نسبة التطابق: ${result.matchPercent.toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuccess) SuccessGreen else LocalVoxaColors.current.secondaryText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            if (result.bestCandidate.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.say_and_earn_heard, result.bestCandidate),
                                    fontSize = 12.sp,
                                    color = LocalVoxaColors.current.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        stopListening()
                                        try {
                                            speechRecognizer?.cancel()
                                        } catch (e: Exception) {}
                                        showResult = false
                                        matchResult = null
                                        partialSpokenText = ""
                                        handleMicTap()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, LocalVoxaColors.current.inputBorder),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.say_and_earn_try_again),
                                        color = LocalVoxaColors.current.secondaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        stopListening()
                                        try {
                                            speechRecognizer?.cancel()
                                        } catch (e: Exception) {}
                                        showResult = false
                                        matchResult = null
                                        partialSpokenText = ""
                                        selectedWordIndex = (selectedWordIndex + 1) % allWords.size
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSuccess) QuestTeal else LocalVoxaColors.current.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.say_and_earn_next_word),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Practice card (shown when result is not displayed)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Word representation
                        Text(text = currentWord.emoji, fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentWord.arabic,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LocalVoxaColors.current.primaryText
                        )
                        Text(
                            text = currentWord.english,
                            fontSize = 16.sp,
                            color = LocalVoxaColors.current.secondaryText
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 🔊 "Listen to example" Button
                        Button(
                            onClick = { playWordAudio() },
                            colors = ButtonDefaults.buttonColors(containerColor = QuestTeal.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, QuestTeal.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Listen",
                                tint = QuestTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.speech_practice_listen_example),
                                color = QuestTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── MIC BUTTON AREA (Fixed at bottom, never shifts) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    // Pulsing ripple 1
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(rippleScale1)
                            .clip(CircleShape)
                            .background(LocalVoxaColors.current.microphoneGlow.copy(alpha = if (LocalVoxaColors.current.isDark) rippleAlpha1 else 0.22f * rippleAlpha1))
                    )
                    // Pulsing ripple 2
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(rippleScale2)
                            .clip(CircleShape)
                            .background(LocalVoxaColors.current.microphoneGlow.copy(alpha = if (LocalVoxaColors.current.isDark) rippleAlpha2 else 0.22f * rippleAlpha2))
                    )
                }

                // Floating coin burst animation
                if (coinAnimAlpha.value > 0f) {
                    val earnedStars = matchResult?.coinsEarned ?: 5
                    val burstTotal = earnedStars + if (bonusAwarded) 2 else 0
                    Text(
                        text = if (bonusAwarded) "+$burstTotal ⭐🔥" else "+$burstTotal ⭐",
                        fontSize = 28.sp,
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

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening && LocalVoxaColors.current.isDark) Color(0xFF0F1E2E) else LocalVoxaColors.current.normalCard
                        )
                        .border(
                            width = 2.dp,
                            color = if (isListening) LocalVoxaColors.current.microphone else LocalVoxaColors.current.surfaceVariant,
                            shape = CircleShape
                        )
                        .then(
                            if (micMode == "hold") {
                                Modifier.pointerInput(selectedWordIndex, selectedLanguage, micMode) {
                                    detectTapGestures(
                                        onPress = {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (!hasPermission) {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                return@detectTapGestures
                                            }

                                            startListeningInternal()
                                            tryAwaitRelease()
                                            stopListening()
                                        }
                                    )
                                }
                            } else {
                                Modifier.clickable { handleMicTap() }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (isListening) "Listening Active" else "Listening Paused",
                        tint = if (isListening) LocalVoxaColors.current.microphone else LocalVoxaColors.current.secondaryText,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Status text below mic
            Text(
                text = when {
                    isListening -> stringResource(R.string.say_and_earn_listening)
                    isProcessing -> stringResource(R.string.say_and_earn_processing)
                    else -> stringResource(R.string.say_and_earn_tap_to_speak)
                },
                fontSize = 13.sp,
                color = if (isListening) LocalVoxaColors.current.microphone else LocalVoxaColors.current.secondaryText,
                fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            // Volume indicator bar
            if (isListening) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LocalVoxaColors.current.background)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(micRmsLevel.coerceIn(0.05f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(LocalVoxaColors.current.microphone, if (LocalVoxaColors.current.isDark) Sky400 else LocalVoxaColors.current.microphone)
                                )
                            )
                    )
                }
            }

            // Partial speech transcription live indicator
            if (partialSpokenText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"$partialSpokenText\"",
                    fontSize = 12.sp,
                    color = LocalVoxaColors.current.secondaryText,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
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

