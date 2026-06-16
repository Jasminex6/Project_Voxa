package com.example.voxa.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

/**
 * 🎙️ VoxaListenerService
 *
 * A Foreground Service that runs continuously in the background. It performs two main system roles:
 * 1. Obtains a CPU WakeLock to keep the device's processor active even when the screen is turned off.
 * 2. Spawns a dedicated, high-priority background thread that captures raw 16kHz Mono 16-bit PCM audio
 *    from the microphone using Android's AudioRecord API.
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

        // 3. Launch the background microphone recording thread
        startRecording()

        // START_STICKY tells Android: "If you have to force-kill this service due to low RAM,
        // recreate it and start it again as soon as memory clears up."
        return START_STICKY
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

                // ── THE PERPETUAL RECORDING LOOP ──
                // This loop runs continuously on our background thread.
                while (isRecording) {
                    // audioRecord.read() is a blocking call. It halts the thread right here until the microphone
                    // gathers enough sound waves to completely fill our minBufferSize array.
                    val readResult = audioRecord?.read(audioData, 0, minBufferSize) ?: 0
                    
                    // If we successfully read data from the hardware buffer
                    if (readResult > 0) {
                        // 🔍 INTEGRATION SPOT:
                        // This is where Developer B captures the live PCM audio block (audioData)
                        // and pipes it to Developer A's DSP/AI model (VAD, MFCC features, and DTW).
                        Log.d("VoxaService", "Captured buffer frame: read $readResult samples")
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
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ Voxa Listening Active")
            .setContentText("Listening for child vocalizations in the background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Standard Android system microphone icon
            .setOngoing(true) // Makes the notification persistent (the user cannot swipe it away)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voxa_listener_channel"
        private const val NOTIFICATION_ID = 42

        @Volatile
        var isRunning = false
    }
}
