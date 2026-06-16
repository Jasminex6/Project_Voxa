# 🎓 Voxa Study Log: Lesson 9 — Continuous Microphone Capture & AudioRecord Thread

Welcome to Lesson 9! Now that our database is fully set up, we need to implement the core feature of our background listener service: **capturing raw audio in real-time**.

To translate a child's sounds, we cannot rely on standard audio recorders that save to files (like `MediaRecorder`). We need access to the **live stream of raw audio bytes** as they enter the microphone so we can analyze them on-the-fly.

In this lesson, we will:
1.  **Understand `AudioRecord`:** The Android hardware-access class for streaming raw PCM data.
2.  **Build a Background Recording Thread:** Spawn a loop to read microphone bytes continuously without freezing the app.
3.  **Update `VoxaListenerService.kt`:** Implement the actual hardware record thread, WakeLocks, and lifecycles.

---

## 💬 Q&A: Audio Parameters & Threading

### ❓ Question 1: What is PCM audio?
**💡 Mentor Explanation:** **PCM (Pulse Code Modulation)** is uncompressed, raw digital audio. 
*   Unlike MP3 or AAC (which are compressed and take complex CPU power to decode), PCM represents the absolute physical vibration level of the microphone.
*   In Kotlin, a 16-bit PCM sample is represented as a `Short` (an integer between `-32768` and `+32767`). This raw format is exactly what Developer A's DSP and template matching algorithms require for calculation.

---

### ❓ Question 2: Why do we need a separate thread?
**💡 Mentor Explanation:** Because reading from the microphone is a **blocking operation**.
*   When you call `audioRecord.read()`, the execution pauses and waits for the hardware to gather enough sound waves to fill the buffer.
*   If you run this read loop on the app's **Main Thread**, the entire screen will instantly freeze, and the phone will show an "App Not Responding" (ANR) popup. 
*   We spawn a dedicated **Background Thread** that runs a perpetual `while (isRecording)` loop, letting the Main Thread draw the UI smoothly.

---

### ❓ Question 3: Why is `audioRecord` initialized as `null` if we call this "activation"?
**💡 Mentor Explanation:** This is due to Kotlin's strict null-safety and hardware lifecycles.
*   **Startup state:** When the service is first loaded into memory, the microphone hardware hasn't been initialized yet. We cannot instantiate the microphone until the user explicitly requests it.
*   **Kotlin Nullability (`AudioRecord?`):** The `?` tells Kotlin that this variable is allowed to be empty (`null`) at startup.
*   **Activation:** The actual initialization happens inside the `startRecording()` method when the user clicks "Start Listening". We instantiate the `AudioRecord` object, removing it from the `null` state and turning the microphone on via `startRecording()`.
*   **Cleanup:** When stopping, we release the hardware and set it back to `null` to ensure our service doesn't hold an active lock on the phone's microphone.

---

### ❓ Question 4: How does the calculation `10 * 60 * 1000L` work inside `acquire()`?
**💡 Mentor Explanation:** Android's `WakeLock.acquire()` method expects the time duration in **milliseconds**. 
*   **The Math:** 
    $$\text{10 minutes} \times \text{60 seconds} \times \text{1000 milliseconds} = \text{600,000 milliseconds}$$
*   **The `L` Suffix:** Tells Kotlin to treat this number as a 64-bit **Long** integer to prevent numerical overflow errors.
*   **Readable Formatting:** We write it as `10 * 60 * 1000L` instead of `600000L` to avoid "magic numbers," making the math instantly readable to other developers.
*   **Why a Timeout:** If we acquire a lock with no time limit, a crash could leak the lock and drain the phone's battery in a few hours. The 10-minute timeout is a safety guardrail that automatically releases the CPU lock if our app fails to clean up properly.

---

### ❓ Question 5: Why does the code use `return@Thread` instead of a normal `return`?
**💡 Mentor Explanation:** This is Kotlin's syntax for a **labeled return** inside nested blocks.
*   **The Problem:** The microphone recording loop is inside a thread block: `Thread({ ... })`. If you write a plain `return`, Kotlin thinks you want to return from the outer function `startRecording()`. This is illegal in Kotlin because a background thread cannot return a value for the function that spawned it.
*   **The Solution:** Using `return@Thread` tells Kotlin to exit *only* the thread's execution block early. 
*   **Implicit Labels:** Kotlin automatically names the return label after the function or constructor receiving the block (in this case, `@Thread`). If you were inside a `forEach` loop, the syntax would be `return@forEach`.

---

### ❓ Question 6: What do the two zeroes mean in the line `audioRecord?.read(audioData, 0, minBufferSize) ?: 0`?
**💡 Mentor Explanation:** Each zero represents a different syntax rule:
1.  **The first zero (inside `read(...)`):** This is the **offset parameter**. It tells Android's hardware driver: *"Start writing the captured audio samples into my `audioData` array starting from index 0 (the very beginning)."*
2.  **The second zero (after `?:`):** This is the fallback value for Kotlin's **Elvis Operator**. Because `audioRecord` is nullable, if the microphone is closed in another thread mid-read, the safe call evaluates to `null`. Appending `?: 0` tells Kotlin: *"If the recorder is null, pretend we read 0 samples so the loop exits safely instead of crashing."*

---

### ❓ Question 7: What does `start()` do on line 154 of the service file?
**💡 Mentor Explanation:** It launches the **background thread**.
*   In Kotlin/Java, instantiating a Thread class (`val t = Thread(...)`) only allocates it in memory. The thread is created but is completely **idle** and doesn't run.
*   To tell the OS to schedule this thread and begin running the microphone capture code block inside it, you must call `start()`.
*   Inside the `.apply { ... }` block, calling `start()` is equivalent to calling `this.start()` on the newly created thread instance. Without it, the service would run, but the microphone recording thread would never turn on.

---

### ❓ Question 8: Why is the WakeLock released using `wakeLock?.let { if (it.isHeld) { it.release() } }` inside `onDestroy()`?
**💡 Mentor Explanation:** This handles battery preservation and prevents application crashes. 

Think of the WakeLock as a physical **"keep awake" button** on the CPU:
*   **"Held" (Active):** Your app is currently pressing the button down (`acquire()`). The CPU is forced to stay awake.
*   **"Released" (Inactive):** Your app has lifted its finger off the button (`release()`), letting the CPU go to sleep.

Here is what the code does step-by-step:
1.  **Null Safety (`?.let`):** If the service is destroyed before `onStartCommand` runs, the lock doesn't exist. `?.let` ensures we only check the lock if it exists.
2.  **Crash Prevention (`it.isHeld`):** We check if we are currently holding the button down. If we try to release a lock we aren't actively holding, Android will panic and crash the app with a `RuntimeException` ("WakeLock under-locked").
3.  **Battery Conservation (`release()`):** Lifts our finger off the button, letting the phone's CPU sleep and saving battery life.

---

### ❓ Question 9: What is the purpose of `createNotificationChannel()` inside the service?
**💡 Mentor Explanation:** It is a system registration required by Android 8.0+ (API 26+) for user notification control:
*   **Why Channels Exist:** Google requires all notifications to belong to a channel. This allows users to control notification settings (e.g., mute sounds or disable popups) for specific categories of notifications in their system Settings. If you try to start a foreground service without registering a channel, the OS will reject the notification and crash the app.
*   **Importance Levels:** We set `IMPORTANCE_LOW` so the background listener notification sits silently in the drawer without playing annoying chimes or vibrating the phone continuously while active.
*   **System Registration:** We fetch the OS `NotificationManager` service and register our channel object. Once registered, Android's system interface knows how to categorize the notifications we send to it.

---

### ❓ Question 10: Is the `NOTIFICATION_ID = 42` constant hardcoded?
**💡 Mentor Explanation:** Yes, and it is standard practice.
*   **The Purpose of the ID:** The ID is like a "coat check ticket". When your app posts a notification to the phone's tray, Android needs a unique number to identify it. If you want to update the notification text later (e.g., changing from "Listening..." to "Sound Detected!"), you tell Android: *"Update the notification at ID 42."* This prevents the app from spamming the user with multiple different notification cards.
*   **Why the number 42:** The specific number doesn't matter, as long as it is a positive integer greater than 0. We centralize it as a `private const val` so the compiler knows it is a read-only constant that won't change while the app is running.

---

## 🛠️ Step 1: Initialize the Android `AudioRecord` Class

To capture raw audio, we configure the hardware with our specific parameters (16,000 Hz sample rate, Mono, 16-bit):

```kotlin
// 1. Calculate the minimum buffer size the phone's hardware requires to run
val bufferSize = AudioRecord.getMinBufferSize(
    16000,                                 // Sample Rate (Hz)
    AudioFormat.CHANNEL_IN_MONO,           // 1 channel (mono)
    AudioFormat.ENCODING_PCM_16BIT         // 16-bit linear PCM encoding
)

// 2. Initialize the hardware recorder
val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,         // Capture from physical microphone
    16000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)
```

---

## 💻 Step 2: Update `VoxaListenerService.kt`

Open [VoxaListenerService.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/services/VoxaListenerService.kt) and replace its entire content with the complete recording thread logic:

```kotlin
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
 * A foreground service that runs continuously, capturing raw microphone streams and sending them
 * to the classification engine. It holds a partial WakeLock to keep the CPU active.
 */
class VoxaListenerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("VoxaService", "Service Created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VoxaService", "Service Started")

        // 1. Immediately register as a Foreground Service with a notification to prevent OS termination
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 2. Acquire a CPU WakeLock to keep listening when the screen goes black
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Voxa::ListenerLock").apply {
            acquire(10 * 60 * 1000L /* Keep CPU awake for 10 minutes */)
        }

        // 3. Start our background microphone recording thread
        startRecording()

        return START_STICKY // Restart the service automatically if the OS runs out of RAM
    }

    private fun startRecording() {
        if (isRecording) return // Already running
        isRecording = true

        recordingThread = Thread({
            // Configure the hardware parameters
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            // Verify if the device supports the buffer size
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e("VoxaService", "Invalid buffer size computed")
                return@Thread
            }

            try {
                // Initialize the recording hardware
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufferSize
                )

                // Start recording hardware stream
                audioRecord?.startRecording()
                Log.d("VoxaService", "Microphone recording started successfully")

                // Create a temporary buffer array to hold each read segment
                val audioData = ShortArray(minBufferSize)

                // The Perpetual Recording Loop (runs on this background thread)
                while (isRecording) {
                    // Read blocking audio bytes from mic hardware
                    val readResult = audioRecord?.read(audioData, 0, minBufferSize) ?: 0
                    
                    if (readResult > 0) {
                        // 🔍 INTEGRATION SPOT: 
                        // This is where Developer B captures the raw PCM audio block (audioData)
                        // and passes it to Developer A's signal processor.
                        Log.d("VoxaService", "Captured buffer frame: read $readResult samples")
                    }
                }
            } catch (e: SecurityException) {
                Log.e("VoxaService", "Permission denied for recording audio: ${e.message}")
            } finally {
                // Clean up hardware resources when the loop terminates
                stopAudioHardware()
            }
        }, "VoxaAudioRecordThread").apply {
            priority = Thread.MAX_PRIORITY // Assign high priority so audio stream doesn't drop frames
            start()
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        Log.d("VoxaService", "Service Destroyed")

        // Clean up threads and power locks
        isRecording = false
        recordingThread = null
        stopAudioHardware()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    // ==========================================
    // 🔔 NOTIFICATION SYSTEM (Android 14+ Rules)
    // ==========================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voxa Voice Monitor",
            NotificationManager.IMPORTANCE_LOW // Low importance prevents annoying chime sounds
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ Voxa Listening Active")
            .setContentText("Listening for child vocalizations in the background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Standard Android speech/mic icon
            .setOngoing(true) // Cannot be swiped away by the user
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voxa_listener_channel"
        private const val NOTIFICATION_ID = 42
    }
}
```

---

## 🔍 Understanding the UI-to-Service Connection & Permissions

1.  **`AudioRecord.read(audioData, 0, minBufferSize)`**: This is a **blocking call**. The loop will wait right here until the microphone gathers enough samples to fill the `minBufferSize`. Once full, it populates `audioData` and executes the next lines.
2.  **`Thread.MAX_PRIORITY`**: Audio threads must be high-priority. If the user opens another app, the Android scheduler might pause normal threads. Assigning maximum priority ensures the CPU keeps processing audio buffers without causing stutter or gaps.
3.  **Service State Synchronization (`isRunning`)**:
    *   **The Issue:** Because activities can be destroyed and recreated on configuration changes (like screen rotation), keeping the recording toggle state in a simple local `mutableStateOf` variable in Compose leads to UI/Service desync.
    *   **The Solution:** We added a thread-safe, `@Volatile var isRunning` boolean to the `VoxaListenerService`'s companion object. We toggle it `true` in `onCreate()` and `false` in `onDestroy()`. This allows the Compose UI to initialize its state correctly using `VoxaListenerService.isRunning`.
4.  **`POST_NOTIFICATIONS` Runtime Permission (Android 13+)**:
    *   Since Android 13 (API 33), applications must explicitly request runtime permission to display notifications. We added a `RequestMultiplePermissions` launcher in `MainActivity.kt` to check and request both `RECORD_AUDIO` and `POST_NOTIFICATIONS` simultaneously.
5.  **Service Lifecycles & Intents**:
    *   When the user starts listening, we launch `VoxaListenerService` as a Foreground Service via `context.startForegroundService(intent)`.
    *   When the user stops listening, we cleanly terminate it via `context.stopService(intent)`.

---

## 🛠️ Step 3: Run & Verify on Emulator
1. Launch the app on the emulator.
2. Accept the runtime permissions dialog for Microphone & Notifications.
3. Click "Start Listening" and verify the persistent notification card displays in the notification drawer.
4. Verify Logcat logs show active PCM frames: `Captured buffer frame: read X samples`.
