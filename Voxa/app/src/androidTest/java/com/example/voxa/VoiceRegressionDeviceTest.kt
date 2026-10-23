package com.example.voxa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.ai.MfccExtractor
import com.example.voxa.ai.VoxaClassifierEngine
import com.example.voxa.ai.VoxaVAD
import com.example.voxa.data.*
import com.example.voxa.data.license.*
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.LogEvent
import com.example.voxa.ui.screens.*
import com.example.voxa.ui.theme.VoxaTheme
import com.example.voxa.utils.AudioFileHelper
import com.example.voxa.utils.AudioPlayer
import com.example.voxa.utils.PracticeSpeechRecognizer
import com.example.voxa.utils.PracticeSpeechClient
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

/** Uses isolated UI state and cache files; never modifies the child's database or wallet. */
@RunWith(AndroidJUnit4::class)
class VoiceRegressionDeviceTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalMicMode: String? = null

    @Before fun useContinuousModeForSpeechSessionTests() {
        val prefs = context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
        originalMicMode = prefs.getString("mic_mode", null)
        prefs.edit().putString("mic_mode", "tap").commit()
    }

    @After fun restoreMicrophonePreference() {
        context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE).edit().apply {
            if (originalMicMode == null) remove("mic_mode") else putString("mic_mode", originalMicMode)
        }.commit()
    }

    @Test fun coldSpeechServiceRetriesWithoutFailingTheFirstAttempt() {
        val model = VoiceModel()
        val clients = mutableListOf<FakeSpeechClient>()
        ui.setContent {
            VoxaTheme {
                SayAndEarnScreen(model, speechClientFactory = {
                    FakeSpeechClient { client ->
                        if (clients.size == 1) client.error(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
                        else client.ready()
                    }.also { clients.add(it) }
                })
            }
        }
        ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
        ui.waitUntil(3000) { clients.size == 2 && clients.last().starts == 1 }
        ui.onNodeWithTag("practice_result").assertDoesNotExist()
        assertTrue("Service warm-up must not create a failed attempt", model.observations.isEmpty())
        ui.runOnIdle {
            clients.first().error(SpeechRecognizer.ERROR_CLIENT)
            clients.last().results("ميه")
            clients.last().error(SpeechRecognizer.ERROR_CLIENT)
        }
        ui.onNodeWithTag("practice_result").assertExists()
        assertEquals(listOf(Triple("Water", PracticeOutcome.SCORED, true)), model.observations)
        assertEquals(5, model.coins)
    }

    @Test fun changingWordsIgnoresCancelledAndLateRecognitionCallbacks() {
        val model = VoiceModel()
        val clients = mutableListOf<FakeSpeechClient>()
        ui.setContent {
            VoxaTheme { SayAndEarnScreen(model, speechClientFactory = { FakeSpeechClient().also { clients.add(it) } }) }
        }
        ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Milk"))
        ui.onNodeWithText("Milk").performClick()
        ui.runOnIdle {
            clients.first().error(SpeechRecognizer.ERROR_CLIENT)
            clients.first().results("ميه")
            clients.first().ready()
        }
        ui.onNodeWithTag("practice_result").assertDoesNotExist()
        ui.onNodeWithContentDescription("Listening Paused").assertExists()
        assertTrue(model.observations.isEmpty())
        assertEquals(0, model.coins)
        ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
        ui.runOnIdle {
            clients.last().results("لبن")
            clients.last().results("لبن") // Duplicate terminal callbacks cannot pay twice.
        }
        ui.onNodeWithTag("practice_result").assertExists()
        assertEquals(listOf(Triple("Milk", PracticeOutcome.SCORED, true)), model.observations)
        assertEquals(5, model.coins)
    }

    @Test fun stoppingBeforeReadyCancelsSilentlyButRealNoMatchStillCounts() {
        val model = VoiceModel()
        val clients = mutableListOf<FakeSpeechClient>()
        ui.setContent {
            VoxaTheme {
                SayAndEarnScreen(model, speechClientFactory = {
                    FakeSpeechClient { if (clients.size > 1) it.ready() }.also { clients.add(it) }
                })
            }
        }
        ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
        ui.onNodeWithContentDescription("Listening Active").performTouchInput { click() }
        ui.runOnIdle { clients.first().ready(); clients.first().error(SpeechRecognizer.ERROR_NO_MATCH) }
        ui.onNodeWithTag("practice_result").assertDoesNotExist()
        assertTrue(model.observations.isEmpty())
        ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
        ui.runOnIdle { clients.last().error(SpeechRecognizer.ERROR_NO_MATCH) }
        ui.onNodeWithTag("practice_result").assertDoesNotExist()
        assertEquals(listOf(Triple("Water", PracticeOutcome.NO_SPEECH, false)), model.observations)
        ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
        ui.runOnIdle { clients.last().beginning(); clients.last().error(SpeechRecognizer.ERROR_NO_MATCH) }
        ui.onNodeWithTag("practice_result").assertExists()
        assertEquals(listOf(Triple("Water", PracticeOutcome.NO_SPEECH, false),
            Triple("Water", PracticeOutcome.UNRECOGNIZED, false)), model.observations)
    }

    @Test fun practiceMicSupportsHoldAndContinuousModes() {
        val prefs = context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
        val previousMode = prefs.getString("mic_mode", null)
        prefs.edit().putString("mic_mode", "hold").commit()
        var showPractice by androidx.compose.runtime.mutableStateOf(true)
        val model = VoiceModel()
        try {
            ui.setContent { VoxaTheme { if (showPractice) SayAndEarnScreen(model) } }
            ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
            ui.onNodeWithContentDescription("Listening Paused").assertIsDisplayed()

            ui.onNodeWithContentDescription("Listening Paused").performTouchInput { down(center) }
            // The production main gesture measures real elapsed press duration.
            android.os.SystemClock.sleep(450)
            ui.onNodeWithContentDescription("Listening Active").performTouchInput { up() }
            ui.onNodeWithContentDescription("Listening Paused").assertIsDisplayed()

            ui.runOnIdle { showPractice = false }
            prefs.edit().putString("mic_mode", "tap").commit()
            ui.runOnIdle { showPractice = true }
            ui.onNodeWithContentDescription("Listening Paused").performTouchInput { click() }
            ui.onNodeWithContentDescription("Listening Active").assertIsDisplayed()
            ui.onNodeWithContentDescription("Listening Active").performTouchInput { click() }
            ui.onNodeWithContentDescription("Listening Paused").assertIsDisplayed()
        } finally {
            ui.runOnIdle { showPractice = false }
            prefs.edit().apply {
                if (previousMode == null) remove("mic_mode") else putString("mic_mode", previousMode)
            }.commit()
        }
    }

    @Test fun paidPracticeIncludesFullVocabularyAndCustomWords() {
        val model = VoiceModel()
        model.license.value = ActiveLicense(tier = LicenseTier.FAMILY_PRO, status = LicenseStatus.ACTIVE)
        ui.setContent { VoxaTheme { SayAndEarnScreen(model) } }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Sleep"))
        ui.onNodeWithText("Sleep").assertIsDisplayed().performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("CustomVoiceWord"))
        ui.onNodeWithText("CustomVoiceWord").assertIsDisplayed().performClick()
        ui.onAllNodesWithText("CustomVoiceWord").assertCountEquals(2)
    }

    @Test fun paidPlanAllowsEnrollmentBeyondThreeWords() {
        val model = VoiceModel()
        model.license.value = ActiveLicense(tier = LicenseTier.FAMILY_PRO, status = LicenseStatus.ACTIVE)
        model.enrolledIntents.value = (1L..6L).map {
            EnrolledIntent(id = it, profileId = 101, intentName = "Word$it", outputPhrase = "Word$it", audioAssetPath = "")
        }
        ui.setContent { VoxaTheme { EnrollmentScreen(model, onBack = {}) } }
        ui.onNodeWithText(context.getString(R.string.enroll_title)).assertIsDisplayed()
        ui.onNodeWithText(context.getString(R.string.license_intent_limit)).assertDoesNotExist()
    }

    @Test fun freePracticeExplainsUpgradeAndRespondsToPlanChanges() {
        val model = VoiceModel()
        var upgradeClicks = 0
        ui.setContent { VoxaTheme { SayAndEarnScreen(model, onUpgrade = { upgradeClicks++ }) } }
        ui.onAllNodesWithTag("practice_word").assertCountEquals(3)
        ui.onNodeWithText(context.getString(R.string.license_practice_demo)).assertIsDisplayed()
        ui.onNodeWithText(context.getString(R.string.license_practice_upgrade)).performClick()
        assertEquals(1, upgradeClicks)
        ui.runOnIdle { model.license.value = ActiveLicense(tier = LicenseTier.FAMILY_PRO, status = LicenseStatus.ACTIVE) }
        ui.onNodeWithText(context.getString(R.string.license_practice_demo)).assertDoesNotExist()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("CustomVoiceWord"))
        ui.onNodeWithText("CustomVoiceWord").performClick()
        ui.runOnIdle { model.deactivateSubscription() }
        ui.onAllNodesWithTag("practice_word").assertCountEquals(3)
        ui.onNodeWithTag("practice_result").assertDoesNotExist()
        ui.onNodeWithContentDescription("Listening Paused").assertExists()
        assertEquals(1, model.enrolledIntents.value.size)
    }

    @Test fun subscriptionLogoutRequiresConfirmationAndPreservesChildState() {
        val model = VoiceModel()
        model.license.value = ActiveLicense(tier = LicenseTier.FAMILY_PRO, status = LicenseStatus.ACTIVE)
        ui.setContent { VoxaTheme { SubscriptionCard(model) } }
        ui.onNodeWithText(context.getString(R.string.license_deactivate)).performClick()
        ui.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        assertEquals(LicenseTier.FAMILY_PRO, model.license.value.effectiveTier)
        ui.onNodeWithText(context.getString(R.string.license_deactivate)).performClick()
        ui.onNodeWithText(context.getString(R.string.license_deactivate_confirm)).performClick()
        ui.onNodeWithText(context.getString(R.string.license_free_name)).assertIsDisplayed()
        assertEquals(1, model.allProfiles.value.size)
        assertEquals(1, model.enrolledIntents.value.size)
        assertEquals(101L, model.activeProfile.value?.id)
    }

    @Test fun licenseLogoutPersistsAndTheSameCodeCanBeReactivated() {
        val fixture = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("license-test-fixture.json").bufferedReader().use { it.readText() })
        val name = "voxa_license_logout_regression"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("device_id", fixture.getString("deviceId"))
            .putString("data_marker", "preserved").commit()
        try {
            val manager = LicenseManager(context, name)
            val token = fixture.getString("token")
            manager.activate(token)
            val identity = manager.deviceId
            val maxSeen = prefs.getLong("max_seen_time", 0)
            assertEquals(LicenseTier.FAMILY_PRO, manager.license.value.effectiveTier)
            manager.deactivate()
            manager.refresh()
            assertEquals(LicenseStatus.FREE, manager.license.value.status)
            assertFalse(prefs.contains("token"))
            assertEquals(identity, manager.deviceId)
            assertEquals(identity, prefs.getString("device_id", null))
            assertEquals("preserved", prefs.getString("data_marker", null))
            assertTrue(prefs.getLong("max_seen_time", 0) >= maxSeen)
            manager.activate(token)
            manager.refresh()
            assertEquals(LicenseTier.FAMILY_PRO, manager.license.value.effectiveTier)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun installedRecognizerAcceptsArabicAndEnglishPracticeRequests() {
        ui.setContent { Text("Voice service regression check") }
        for (language in listOf("ar", "en")) {
            val ready = CountDownLatch(1)
            val error = AtomicInteger(0)
            var recognizer: SpeechRecognizer? = null
            try {
                ui.runOnIdle {
                    val request = PracticeSpeechRecognizer.request(context, language)
                    assertFalse("Practice must allow the installed service's hybrid backend",
                        request.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
                    assertEquals(if (language == "ar") "ar-EG" else "en-US",
                        request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                    recognizer = PracticeSpeechRecognizer.create(context)
                    assertNotNull("Installed recognition service must be visible", recognizer)
                    recognizer!!.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { ready.countDown() }
                        override fun onError(code: Int) { error.set(code); ready.countDown() }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onResults(results: Bundle?) {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    recognizer!!.startListening(request)
                }
                assertTrue("No ready callback for $language", ready.await(15, TimeUnit.SECONDS))
                assertEquals("Speech service failed for $language", 0, error.get())
            } finally {
                ui.runOnIdle { recognizer?.cancel(); recognizer?.destroy() }
            }
        }
    }

    @Test fun arabicAndEnglishPronunciationEngineProducesAudio() {
        ui.setContent { Text("Pronunciation regression check") }
        val initialized = CountDownLatch(1)
        val status = AtomicInteger(TextToSpeech.ERROR)
        var tts: TextToSpeech? = null
        ui.runOnIdle {
            tts = TextToSpeech(context) { status.set(it); initialized.countDown() }
        }
        try {
            assertTrue(initialized.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, status.get())
            for ((language, word) in listOf(Locale("ar", "EG") to "ماما", Locale.US to "Water")) {
                val done = CountDownLatch(1)
                val failed = AtomicInteger(0)
                val output = File(context.cacheDir, "voice-regression-${language.language}.wav")
                try {
                    ui.runOnIdle {
                        assertTrue("Missing pronunciation language $language", tts!!.setLanguage(language) >= 0)
                        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) { done.countDown() }
                            @Deprecated("Legacy callback")
                            override fun onError(utteranceId: String?) { failed.set(1); done.countDown() }
                        })
                        assertEquals(TextToSpeech.SUCCESS, tts!!.synthesizeToFile(word, Bundle(), output, word))
                    }
                    assertTrue("No pronunciation output for $language", done.await(20, TimeUnit.SECONDS))
                    assertEquals("Pronunciation failed for $language", 0, failed.get())
                    assertTrue("Empty pronunciation for $language", output.length() > 44)
                } finally { output.delete() }
            }
        } finally { ui.runOnIdle { tts?.shutdown() } }
    }

    @Test fun enrollmentPcmAndLiveRecognitionUseTheSameFeatures() {
        // Real bundled speech, rather than a synthetic tone or a mocked classifier.
        val wav = context.assets.open("practice/water_ref.wav").use { it.readBytes() }
        val bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var pcm = ShortArray(0)
        var sampleRate = 0
        while (offset + 8 <= wav.size) {
            val size = bytes.getInt(offset + 4)
            val chunk = String(wav, offset, 4, Charsets.US_ASCII)
            if (chunk == "fmt ") {
                assertEquals(1, bytes.getShort(offset + 8).toInt())
                assertEquals(1, bytes.getShort(offset + 10).toInt())
                sampleRate = bytes.getInt(offset + 12)
                assertEquals(16, bytes.getShort(offset + 22).toInt())
            }
            if (chunk == "data") { pcm = ShortArray(size / 2) { bytes.getShort(offset + 8 + it * 2) }; break }
            offset += 8 + size + size % 2
        }
        assertTrue(pcm.isNotEmpty())
        assertTrue(sampleRate > 0)
        if (sampleRate != 16000) {
            val source = pcm
            pcm = ShortArray(source.size * 16000 / sampleRate) { index ->
                val position = index * sampleRate / 16000.0
                val left = position.toInt().coerceAtMost(source.lastIndex)
                val right = (left + 1).coerceAtMost(source.lastIndex)
                (source[left] + (source[right] - source[left]) * (position - left)).toInt().toShort()
            }
        }
        val segment = VoxaVAD().processAudio(pcm).firstOrNull()
        assertNotNull("Reference speech must pass enrollment VAD", segment)
        val trimmed = AudioFileHelper.trimSilence(segment!!)
        AudioFileHelper.validateDuration(trimmed)
        val name = "voice-regression-${System.nanoTime()}.pcm"
        val file = File(AudioFileHelper.savePcmFile(context, trimmed, name))
        try {
            val restored = AudioFileHelper.readPcmFile(file)
            assertArrayEquals(trimmed, restored)
            val features = MfccExtractor().extract(restored)
            val template = AcousticTemplate(intentId = 1, templateFeatures = features.flatMap { it.toList() }.joinToString(",", "[", "]"))
            val intent = EnrolledIntent(id = 1, profileId = 101, intentName = "Water", outputPhrase = "أنا عايز مية", audioAssetPath = "water.mp3")
            var nowMs = 10_000L
            val engine = VoxaClassifierEngine(context, null, listOf(intent), mapOf(1L to listOf(template))) { nowMs }
            // Deliberately use blocks that do not align with VAD frames, like AudioRecord.
            val stream = pcm + ShortArray(16000)
            val results = stream.toList().chunked(1024).mapNotNull { engine.processAudioBlock(it.toShortArray()) }
            assertEquals("One utterance must produce one result", 1, results.size)
            assertTrue(results.single().reason, results.single().isMatch)
            assertEquals("Water", results.single().intentName)
            assertEquals("أنا عايز مية", results.single().outputPhrase)
            assertNull("Playback cooldown must suppress echoes", engine.processAudioBlock(pcm + ShortArray(16000)))

            engine.notifyAudioPlaybackStarted()
            nowMs += 10_000 // Delayed synthesis must stay suppressed beyond the old 1.5s timeout.
            assertNull("Queued or long playback must never recognize itself", engine.processAudioBlock(stream))
            nowMs += 10_000
            assertNull("Suppression must last until playback finishes", engine.processAudioBlock(stream))
            engine.notifyAudioPlaybackFinished()
            nowMs += 1499
            // Leave an incomplete speech segment inside the guard interval.
            assertNull("Speaker echo must be discarded", engine.processAudioBlock(pcm))
            nowMs += 1
            assertNull("Suppressed VAD state must not survive into live listening", engine.processAudioBlock(ShortArray(16000)))
            val nextResults = stream.toList().chunked(1024).mapNotNull { engine.processAudioBlock(it.toShortArray()) }
            assertEquals("A fresh utterance of the SAME word must still work", 1, nextResults.size)
            assertTrue(nextResults.single().isMatch)
        } finally { file.delete() }
    }

    @Test fun translationCompletionTracksLongTtsAndInterruptedPlayback() {
        ui.setContent { Text("Translation completion regression check") }
        var player: AudioPlayer? = null
        val completions = AtomicInteger(0)
        val done = CountDownLatch(1)
        try {
            ui.runOnIdle { player = AudioPlayer(context) }
            ui.waitUntil(15_000) { player?.isTtsReady == true }
            ui.runOnIdle {
                player!!.playTranslation("missing-voice-regression.mp3", "Male",
                    "أنا عايز مية. أنا عايز أكل. ماما بابا. أنا عايز مية. أنا عايز أكل.") {
                    completions.incrementAndGet()
                    done.countDown()
                }
            }
            assertFalse("Completion must wait for actual audio, beyond the old timeout", done.await(1700, TimeUnit.MILLISECONDS))
            assertTrue("TTS must signal completion", done.await(25, TimeUnit.SECONDS))
            assertEquals(1, completions.get())
            val interrupted = AtomicInteger(0)
            ui.runOnIdle {
                player!!.speakWord("ماما بابا ماما بابا ماما بابا", onComplete = { interrupted.incrementAndGet() })
                player!!.release()
            }
            assertEquals("Stopping playback must unblock the listener exactly once", 1, interrupted.get())
            assertEquals("Completed requests must not finish twice during release", 1, completions.get())
        } finally { ui.runOnIdle { player?.release() } }
    }

    private class FakeSpeechClient(private val onStart: (FakeSpeechClient) -> Unit = { it.ready() }) : PracticeSpeechClient {
        private var listener: RecognitionListener? = null
        var starts = 0
        override fun setRecognitionListener(listener: RecognitionListener) { this.listener = listener }
        override fun startListening(request: Intent) { starts++; onStart(this) }
        override fun stopListening() {}
        override fun cancel() { error(SpeechRecognizer.ERROR_CLIENT) }
        override fun destroy() {}
        fun ready() { listener?.onReadyForSpeech(Bundle()) }
        fun beginning() { listener?.onBeginningOfSpeech() }
        fun error(code: Int) { listener?.onError(code) }
        fun results(vararg words: String) {
            listener?.onResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(*words)) })
        }
    }

    private class VoiceModel : IVoxaViewModel {
        override val license = MutableStateFlow(ActiveLicense())
        override fun deactivateSubscription() { license.value = ActiveLicense() }
        val observations = mutableListOf<Triple<String, PracticeOutcome, Boolean>>()
        var coins = 0
        override fun addPracticeCoins(coins: Int) { this.coins += coins }
        override fun recordPracticeObservation(profileId: Long, word: String, targetCode: String?, language: String,
            outcome: PracticeOutcome, matched: Boolean, score: Int, stars: Int) {
            observations.add(Triple(word, outcome, matched))
        }
        private val child = ChildProfile(id = 101, name = "Voice test", gender = "Male", isActive = true)
        override val allProfiles = MutableStateFlow(listOf(child))
        override val activeProfile = MutableStateFlow<ChildProfile?>(child)
        override val enrolledIntents = MutableStateFlow(listOf(EnrolledIntent(id = 1, profileId = 101,
            intentName = "كلمة مخصصة", outputPhrase = "CustomVoiceWord", audioAssetPath = "")))
        override val practiceStats = MutableStateFlow(emptyList<PracticeStats>())
        override val isListening = MutableStateFlow(false)
        override val recentEvents = MutableStateFlow(emptyList<LogEvent>())
        override val volumeLevel = MutableStateFlow(0f)
        override fun createProfile(name: String, gender: String, avatarEmoji: String) {}
        override fun selectActiveProfile(profileId: Long) {}
        override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {}
        override fun exportProfileData(context: Context) {}
        override fun importProfileData(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {}
        override fun deleteIntent(intent: EnrolledIntent) {}
        override fun toggleListening() {}
        override fun updateListeningState() {}
        override fun addLogSystemEvent(message: String) {}
        override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {}
        override fun clearLogs() {}
        override fun playRecordedSample(intent: EnrolledIntent) {}
    }
}
