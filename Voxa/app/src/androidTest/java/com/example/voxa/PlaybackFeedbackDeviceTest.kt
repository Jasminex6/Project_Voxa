package com.example.voxa

import android.speech.tts.TextToSpeech
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.ai.MfccExtractor
import com.example.voxa.ai.VoxaClassifierEngine
import com.example.voxa.ai.VoxaVAD
import com.example.voxa.data.AcousticTemplate
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.utils.AudioFileHelper
import com.example.voxa.utils.AudioPlayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Bundled reference audio and synthetic templates only; never reads family recordings or data. */
@RunWith(AndroidJUnit4::class)
class PlaybackFeedbackDeviceTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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

}
