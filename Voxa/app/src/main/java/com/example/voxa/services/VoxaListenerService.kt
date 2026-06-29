package com.example.voxa.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.voxa.MainActivity
import com.example.voxa.ai.VoxaClassifierEngine
import com.example.voxa.data.VoxaDatabase
import com.example.voxa.utils.AudioPlayer
import kotlinx.coroutines.*

/**
 * 🎙️ VoxaListenerService
 *
 * A Foreground Service that runs continuously in the background. It performs two main system roles:
 * 1. Obtains a CPU WakeLock to keep the device's processor active even when the screen is turned off.
 * 2. Spawns a dedicated, high-priority background thread that captures raw 16kHz Mono 16-bit PCM audio
 *    from the microphone using Android's AudioRecord API.
 *
 * Integration: Audio blocks are piped through VoxaClassifierEngine for real-time
 * VAD → Speaker Verification → MFCC → DTW → Margin Gate classification.
 */
class VoxaListenerService : Service() {

    // ── CLASS ATTRIBUTES ──
    
    // A power-management lock that prevents the CPU from falling into deep sleep (Doze Mode).
    private var wakeLock: PowerManager.WakeLock? = null
    
    // A control flag used by the background thread to safely start and stop the infinite recording loop.
    private var isRecording = false
    
    // The background thread where the blocking microphone reading loop executes.
    private var recordingThread: Thread? = null
    
    // The Android hardware-access object used to capture raw, uncompressed PCM audio bytes from the mic.
    private var audioRecord: AudioRecord? = null

    // AI/DSP classifier engine — instantiated from Room data at service start
    private var classifierEngine: VoxaClassifierEngine? = null

    // Audio playback for matched translation phrases
    private var audioPlayer: AudioPlayer? = null

    // Coroutine scope for database loading
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * onBind() is a mandatory method of the Service class.
     * We return null because we are a "Started Service" (we run on our own lifecycle), 
     * not a "Bound Service" (other apps do not connect directly to bind to us).
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * onCreate() represents the "birth" of our service.
     * It runs exactly once when the service is first loaded into memory.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d("VoxaService", "Service Created")
        isRunning = true
        // Initialize the notification channel (required by Android 8.0+ before posting notifications)
        createNotificationChannel()
        audioPlayer = AudioPlayer(this)
    }

    /**
     * onStartCommand() runs every time our UI sends a start request (e.g. clicking "Start Listening").
     * This is where we kick off the active background recording and locks.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VoxaService", "Service Started")

        // 1. Register as a Foreground Service.
        // Android requires background tasks to display a non-swipeable notification so the user knows
        // the microphone is actively capturing data. Without this, the OS terminates the app instantly.
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 2. Acquire a CPU WakeLock.
        // Tells the operating system: "Keep the CPU running even if the screen turns off, because we are actively
        // listening for vocalizations." We set a safety timeout of 10 minutes to prevent battery drain bugs.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Voxa::ListenerLock").apply {
            acquire(10 * 60 * 1000L /* 10 minutes safety timeout */)
        }

        // 3. Load AI pipeline data from Room then start recording
        loadPipelineAndStart()

        // START_STICKY tells Android: "If you have to force-kill this service due to low RAM,
        // recreate it and start it again as soon as memory clears up."
        return START_STICKY
    }

    /**
     * Loads the active profile, enrolled intents, and templates from Room database,
     * then instantiates the classifier engine and starts the recording thread.
     */
    private fun loadPipelineAndStart() {
        serviceScope.launch {
            try {
                val dao = VoxaDatabase.getDatabase(applicationContext).voxaDao()
                val profile = dao.getActiveProfile()
                
                if (profile == null) {
                    Log.w("VoxaService", "No active profile — starting without classifier")
                    startRecording()
                    return@launch
                }

                val intents = dao.getIntentsForProfile(profile.id)
                val templateMap = mutableMapOf<Long, MutableList<com.example.voxa.data.AcousticTemplate>>()
                for (intent in intents) {
                    val templates = dao.getTemplatesForIntent(intent.id)
                    templateMap[intent.id] = templates.toMutableList()
                }

                classifierEngine = VoxaClassifierEngine(
                    context = applicationContext,
                    activeProfile = profile,
                    enrolledIntents = intents,
                    intentTemplates = templateMap
                )

                Log.d("VoxaService", "Pipeline loaded: ${intents.size} intents for profile '${profile.name}'")
                startRecording()
            } catch (e: Exception) {
                Log.e("VoxaService", "Failed to load pipeline: ${e.message}")
                startRecording() // Start without classifier so mic is active
            }
        }
    }

    /**
     * Configures the microphone parameters and spawns the dedicated background thread.
     */
    private fun startRecording() {
        if (isRecording) return // If the recording thread is already running, do nothing.
        isRecording = true

        recordingThread = Thread({
            // ── AUDIO HARDWARE SETUP ──
            val sampleRate = 16000                 // 16kHz frequency (standard for speech recognition models)
            val channelConfig = AudioFormat.CHANNEL_IN_MONO // Mono (1 channel) captures clean vocal signals
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT // 16-bit linear PCM (each sample is a Short: -32768 to 32767)
            
            // Calculate the minimum buffer size in bytes that the physical device's audio driver requires to function.
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            // If the device does not support our audio configuration, log an error and exit the thread.
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e("VoxaService", "Invalid buffer size computed")
                return@Thread
            }

            try {
                // Initialize the AudioRecord interface to access the microphone hardware
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, // Capture from physical microphone
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufferSize
                )

                // Start streaming data from the physical microphone
                audioRecord?.startRecording()
                Log.d("VoxaService", "Microphone recording started successfully")

                // Create a temporary buffer array to hold each read audio block in memory
                val audioData = ShortArray(minBufferSize)

                // Accumulation buffer: collect ~2 seconds of audio before processing
                // This gives the VAD enough context to extract speech segments
                val accumulationTarget = sampleRate * 2  // 32000 samples = 2 seconds
                val accBuffer = mutableListOf<Short>()

                // ── THE PERPETUAL RECORDING LOOP ──
                // This loop runs continuously on our background thread.
                while (isRecording) {
                    // audioRecord.read() is a blocking call. It halts the thread right here until the microphone
                    // gathers enough sound waves to completely fill our minBufferSize array.
                    val readResult = audioRecord?.read(audioData, 0, minBufferSize) ?: 0
                    
                    // If we successfully read data from the hardware buffer
                    if (readResult > 0) {
                        // Compute peak amplitude for visual feedback
                        var maxVal = 0
                        for (i in 0 until readResult) {
                            val absVal = kotlin.math.abs(audioData[i].toInt())
                            if (absVal > maxVal) {
                                maxVal = absVal
                            }
                        }
                        val peakVal = maxVal.toFloat() / 32768f
                        val volIntent = Intent(ACTION_VOLUME_UPDATE).apply {
                            putExtra(EXTRA_VOLUME, peakVal)
                            setPackage(packageName)
                        }
                        sendBroadcast(volIntent)

                        // Accumulate audio data
                        for (i in 0 until readResult) {
                            accBuffer.add(audioData[i])
                        }

                        // Process accumulated audio when we have enough
                        if (accBuffer.size >= accumulationTarget) {
                            val engine = classifierEngine
                            if (engine != null) {
                                val pcmBlock = accBuffer.toShortArray()
                                accBuffer.clear()

                                try {
                                    val result = engine.processAudioBlock(pcmBlock)
                                    if (result != null) {
                                        handleClassificationResult(result)
                                    }
                                } catch (e: Exception) {
                                    Log.e("VoxaService", "Classification error: ${e.message}")
                                }
                            } else {
                                // No classifier — just clear and continue
                                accBuffer.clear()
                                Log.d("VoxaService", "Captured buffer frame (no classifier active)")
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Triggered if the user revokes microphone permissions in system settings while the service is running.
                Log.e("VoxaService", "Permission denied for recording audio: ${e.message}")
            } finally {
                // Always release hardware back to the OS when the loop ends (preventing microphone locking errors)
                stopAudioHardware()
            }
        }, "VoxaAudioRecordThread").apply {
            // Assign maximum scheduling priority to this thread.
            // This ensures the Android CPU prioritizes our recording loop even if the user opens other heavy apps,
            // preventing dropped audio frames.
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Handles a classification result from the AI pipeline.
     * If matched, plays the translation audio and broadcasts an event to the UI.
     */
    private fun handleClassificationResult(result: com.example.voxa.ai.ClassificationResult) {
        Log.d("VoxaService", "Classification: match=${result.isMatch}, intent=${result.intentName}, " +
                "confidence=${result.confidence}, reason=${result.reason}")

        // Broadcast the result to VoxaViewModel for timeline display
        val broadcastIntent = Intent(ACTION_CLASSIFICATION_RESULT).apply {
            putExtra(EXTRA_IS_MATCH, result.isMatch)
            putExtra(EXTRA_INTENT_NAME, result.intentName ?: "Unknown")
            putExtra(EXTRA_OUTPUT_PHRASE, result.outputPhrase ?: "")
            putExtra(EXTRA_CONFIDENCE, result.confidence)
            putExtra(EXTRA_REASON, result.reason)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // Play translation audio on match
        if (result.isMatch && result.audioAssetPath != null && result.outputPhrase != null) {
            val dao = VoxaDatabase.getDatabase(applicationContext).voxaDao()
            serviceScope.launch {
                val profile = dao.getActiveProfile()
                val gender = profile?.gender ?: "Male"
                withContext(Dispatchers.Main) {
                    audioPlayer?.playTranslation(result.audioAssetPath, gender, result.outputPhrase)
                }
            }
        }
    }

    /**
     * Safely stops the microphone hardware and releases its system locks.
     */
    private fun stopAudioHardware() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d("VoxaService", "Audio hardware stopped and released")
        } catch (e: Exception) {
            Log.e("VoxaService", "Error releasing audio hardware: ${e.message}")
        }
    }

    /**
     * onDestroy() represents the "death" of our service.
     * It is called when the user stops the service cleanly (e.g. clicking "Stop Listening").
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("VoxaService", "Service Destroyed")
        isRunning = false

        // 1. Flip the loop flag to false, which breaks the background thread's while loop
        isRecording = false
        recordingThread = null
        
        // 2. Shut down and release the microphone hardware
        stopAudioHardware()

        // 3. Release the CPU WakeLock if it is currently held, letting the processor enter sleep mode again
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        // 4. Release audio player and coroutine scope
        audioPlayer?.release()
        audioPlayer = null
        serviceScope.cancel()
    }

    // ==========================================
    // 🔔 NOTIFICATION SYSTEM (Android 14+ Rules)
    // ==========================================

    /**
     * Creates the notification channel required by Android 8.0 (Oreo) and above.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voxa Voice Monitor",
            NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW prevents the phone from chiming or vibrating continuously
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Constructs the persistent notification displayed in the drawer when listening is active.
     * Tapping the notification brings the user back into the Voxa app.
     */
    private fun createNotification(): Notification {
        // Create an intent that opens MainActivity when the notification is tapped.
        // This is like a portal that brings the user straight back to the main app dashboard.
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE required on Android 12+
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ Voxa Listening Active")
            .setContentText("Listening for child vocalizations in the background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Standard Android system microphone icon
            .setOngoing(true) // Makes the notification persistent (the user cannot swipe it away)
            .setContentIntent(pendingIntent) // ← Tapping notification opens the app
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voxa_listener_channel"
        private const val NOTIFICATION_ID = 42

        // Broadcast action for classification results
        const val ACTION_CLASSIFICATION_RESULT = "com.example.voxa.CLASSIFICATION_RESULT"
        const val EXTRA_IS_MATCH = "is_match"
        const val EXTRA_INTENT_NAME = "intent_name"
        const val EXTRA_OUTPUT_PHRASE = "output_phrase"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_REASON = "reason"

        // Broadcast action for live mic volume levels
        const val ACTION_VOLUME_UPDATE = "com.example.voxa.VOLUME_UPDATE"
        const val EXTRA_VOLUME = "volume"

        @Volatile
        var isRunning = false
    }
}
