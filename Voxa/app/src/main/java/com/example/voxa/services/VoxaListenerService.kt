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
import com.example.voxa.R
import com.example.voxa.ai.VoxaClassifierEngine
import com.example.voxa.data.VoxaDatabase
import com.example.voxa.utils.AudioPlayer
import android.content.res.Configuration
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
    @Volatile
    private var isRecording = false
    
    // The background thread where the blocking microphone reading loop executes.
    private var recordingThread: Thread? = null
    
    // The Android hardware-access object used to capture raw, uncompressed PCM audio bytes from the mic.
    private var audioRecord: AudioRecord? = null

    // AI/DSP classifier engine — instantiated from Room data at service start
    @Volatile
    private var classifierEngine: VoxaClassifierEngine? = null

    // Audio playback for matched translation phrases
    private var audioPlayer: AudioPlayer? = null

    // Coroutine scope for database loading
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // SOS Emergency Dispatch States
    private var currentSosContacts = listOf<Pair<String, String>>()
    private var currentSosLink = ""

    // Debounce to prevent TTS audio playback from triggering a secondary feedback match
    private var lastMatchTimestamp = 0L
    private val DEBOUNCE_PERIOD_MS = 2500L

    // Tracks the current asynchronous playback/dispatch coroutine job to prevent race conditions
    private var playbackJob: Job? = null

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val index = intent?.getIntExtra("caregiver_index", -1) ?: -1
            if (index != -1) {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    Log.d("VoxaService", "SMS alert successfully delivered to caregiver at index $index")
                } else {
                    Log.w("VoxaService", "SMS alert delivery failed for caregiver at index $index, trying fallback")
                    triggerNextCaregiverAlert(index + 1)
                }
            }
        }
    }

    private fun triggerNextCaregiverAlert(index: Int) {
        if (index >= currentSosContacts.size) {
            Log.w("VoxaService", "All caregiver SMS alerts failed or no more contacts to notify.")
            return
        }
        val contact = currentSosContacts[index]
        val name = contact.first
        val phone = contact.second
        val localizedContext = getLocalizedContext()
        val message = localizedContext.getString(R.string.sms_sos_message, currentSosLink)

        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }

            val sentIntent = PendingIntent.getBroadcast(
                this,
                100 + index,
                Intent(ACTION_SMS_SENT).apply {
                    putExtra("caregiver_index", index)
                    setPackage(packageName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            smsManager.sendTextMessage(phone, null, message, sentIntent, null)
            Log.d("VoxaService", "Sending emergency SMS to Caregiver $index: $name ($phone)")
        } catch (e: Exception) {
            Log.e("VoxaService", "Failed to send SMS to $name: ${e.message}")
            // Fallback immediately to next caregiver
            triggerNextCaregiverAlert(index + 1)
        }
    }

    private fun triggerSosDispatch(profile: com.example.voxa.data.ChildProfile?) {
        if (profile == null) {
            Log.w("VoxaService", "No active profile to pull emergency caregivers from")
            return
        }

        serviceScope.launch {
            val contacts = mutableListOf<Pair<String, String>>()
            val jsonStr = profile.caregiverContactsJson
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val phone = obj.optString("phone", "")
                        if (phone.isNotBlank()) {
                            contacts.add(Pair(name, phone))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VoxaService", "Error parsing caregiver JSON: ${e.message}")
                }
            }

            if (contacts.isEmpty()) {
                Log.w("VoxaService", "No emergency contacts registered for SOS")
                return@launch
            }

            Log.d("VoxaService", "SOS Triggered! Fetching location...")
            val location = com.example.voxa.utils.LocationHelper.getFreshLocation(applicationContext)
            val mapsLink = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "Location unavailable"
            }

            currentSosContacts = contacts
            currentSosLink = mapsLink

            triggerNextCaregiverAlert(0)

            val primary = contacts.first()
            showSosNotification(primary.first, primary.second)
        }
    }

    private fun showSosNotification(caregiverName: String, caregiverPhone: String) {
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:$caregiverPhone")
        }
        val pendingCallIntent = PendingIntent.getActivity(
            this,
            99,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val localizedContext = getLocalizedContext()
        val builder = NotificationCompat.Builder(this, "voxa_emergency_channel")
            .setContentTitle(localizedContext.getString(R.string.notification_sos_title))
            .setContentText(localizedContext.getString(R.string.notification_sos_text, caregiverName))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingCallIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "voxa_emergency_channel",
                "Voxa Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
        manager.notify(101, builder.build())
    }

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

        // Register SMS sent status receiver
        val filter = IntentFilter(ACTION_SMS_SENT)
        registerReceiver(smsSentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (_: Exception) {}
            }
        }
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
                isRecording = false
                stopSelf()
                return@Thread
            }

            try {
                // Initialize the AudioRecord interface to access the microphone hardware.
                // We retry up to 5 times (300ms delay) to allow the HAL to release the microphone
                // if transitioning from another recording screen (like the enrollment page).
                var success = false
                var attempts = 0
                while (!success && attempts < 5 && isRecording) {
                    try {
                        audioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            minBufferSize
                        )
                        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord?.startRecording()
                            success = true
                        } else {
                            attempts++
                            Log.w("VoxaService", "Microphone busy, retrying in 300ms... (attempt $attempts)")
                            stopAudioHardware()
                            Thread.sleep(300)
                        }
                    } catch (e: Exception) {
                        attempts++
                        Log.w("VoxaService", "Failed to start recording, retrying in 300ms... (attempt $attempts): ${e.message}")
                        stopAudioHardware()
                        Thread.sleep(300)
                    }
                }

                if (!success) {
                    Log.e("VoxaService", "Could not start microphone after 5 attempts.")
                    stopSelf()
                    return@Thread
                }
                
                Log.d("VoxaService", "Microphone recording started successfully after $attempts attempts")

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
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastMatchTimestamp < DEBOUNCE_PERIOD_MS) {
                                        Log.d("VoxaService", "Ignoring audio block: within TTS playback debounce window")
                                    } else {
                                        val result = engine.processAudioBlock(pcmBlock)
                                        if (result != null) {
                                            if (result.isMatch) {
                                                lastMatchTimestamp = System.currentTimeMillis()
                                            }
                                            handleClassificationResult(result)
                                        }
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
            } catch (e: Exception) {
                Log.e("VoxaService", "Critical error in recording thread: ${e.message}", e)
                stopSelf()
            } finally {
                isRecording = false
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
            playbackJob?.cancel()
            playbackJob = serviceScope.launch {
                val dao = VoxaDatabase.getDatabase(applicationContext).voxaDao()
                val profile = dao.getActiveProfile()
                val gender = profile?.gender ?: "Male"
                withContext(Dispatchers.Main) {
                    audioPlayer?.playTranslation(result.audioAssetPath, gender, result.outputPhrase)
                }

                // If it is an SOS intent, trigger GPS coordinates lookup and caregiver SMS dispatch chain
                if (result.intentName.equals("SOS", ignoreCase = true)) {
                    triggerSosDispatch(profile)
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

        // Unregister SMS sent receiver
        try {
            unregisterReceiver(smsSentReceiver)
        } catch (_: Exception) {}

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

        try {
            val destroyIntent = Intent(ACTION_SERVICE_DESTROYED).apply {
                setPackage(packageName)
            }
            sendBroadcast(destroyIntent)
        } catch (_: Exception) {}
    }

    private fun getLocalizedContext(): Context {
        val prefs = getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("language", "en") ?: "en"
        return com.example.voxa.utils.LocaleHelper.wrap(this, savedLang)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
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

        val localizedContext = getLocalizedContext()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(localizedContext.getString(R.string.notification_active_title))
            .setContentText(localizedContext.getString(R.string.notification_active_text))
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

        // Broadcast action for SMS alert status tracking
        const val ACTION_SMS_SENT = "com.example.voxa.SMS_SENT"

        // Broadcast action for service destroyed/stopped state syncing
        const val ACTION_SERVICE_DESTROYED = "com.example.voxa.SERVICE_DESTROYED"

        @Volatile
        var isRunning = false
    }
}
