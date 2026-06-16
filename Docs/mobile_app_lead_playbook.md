# 📱 Mobile App Lead Playbook — Project Voxa

> Welcome to the team! As the **Mobile App Lead**, you are responsible for bringing Voxa to life on the user's device. 
> 
> Even if this is your first time building a native Android app, writing Kotlin, or working with on-device ML, **do not worry**. This playbook breaks down your responsibilities, explains the core concepts, and gives you a step-by-step roadmap to success. We will build this together!

---

## 1. Your Areas of Responsibility (The Big Picture)

As the Mobile App Lead, your role is divided into **five core blocks**:

```
 ┌─────────────────────────────────────────────────────────────┐
 │                    1. UI & UX Shell                         │
 │  Build beautiful, warm Jetpack Compose screens for parents  │
 └──────────────┬───────────────────────────────┬──────────────┘
                ▼                               ▼
 ┌──────────────────────────────┐ ┌────────────────────────────┐
 │      2. Audio Engine         │ │    3. Local Storage        │
 │  Record 16kHz audio, run VAD │ │ Store enrolled words & the │
 │  in a Foreground Service     │ │ recognition log (Room DB)  │
 └──────────────┬───────────────┘ └─────────────┬──────────────┘
                ▼                               ▼
 ┌─────────────────────────────────────────────────────────────┐
 │               4. On-Device ML Integration                   │
 │ Load TFLite, run feature extraction, compute similarities  │
 └──────────────────────────────┬──────────────────────────────┘
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │                5. Translation & Speech                      │
 │ Play pre-recorded clips or use Android's TextToSpeech (TTS) │
 └─────────────────────────────────────────────────────────────┘
```

---

## 2. Step-by-Step Task Breakdown

Here is your exact checklist mapped directly from the master [implementation_plan.md](file:///d:/Jasmine/Side%20Hustle/AI/CU-AI%20NEXUS/implementation_plan.md):

### 🛠️ Step 1: Project Scaffolding & Permissions (Day 1–2)
* [ ] **Android Studio Setup:** Create a new project in Android Studio using the **Empty Compose Activity** template.
* [ ] **Target SDK Config:** Configure `build.gradle.kts` to target API 34+ and set `minSdkVersion` to 26 (Android 8.0).
* [ ] **Declare Permissions:** Add the following lines to `app/src/main/AndroidManifest.xml`:
  ```xml
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_TYPE_MICROPHONE" />
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  ```

### 🎨 Step 2: Build the UI Shell (Jetpack Compose) (Day 2–3)
* [ ] **Home Dashboard:** Display listening status (active/paused), last recognized word, and enrolled words.
* [ ] **Enrollment Flow:** Build an interactive screen where a parent types a word (e.g., "Water"), writes the output translation (e.g., "أنا عايز ميّه"), and records 5 audio samples.
* [ ] **Word Library:** A list of enrolled words showing metadata, allowing deletion or re-recording.
* [ ] **History Logs:** A simple vertical timeline of recognition events.
* [ ] **Settings Panel:** A confidence threshold slider and option for English/Arabic toggles.

### 🎙️ Step 3: Write the Persistent Audio Engine (Day 4–5)
* [ ] **Foreground Service:** Implement `VoxaListenerService` so the app keeps listening even when the screen is locked.
* [ ] **WakeLock:** Prevent the CPU from sleeping during active sessions.
* [ ] **AudioRecord:** Buffer incoming audio from the microphone at 16,000Hz, mono, 16-bit PCM.
* [ ] **VAD Integration:** Link the WebRTC VAD library to extract 1-second speech chunks and ignore background silences.

### 🧠 Step 4: Integrate the ML Model & Matching (Day 6–8)
* [ ] **TFLite Interpreter:** Load `voxa_encoder.tflite` (delivered by your ML team) into memory.
* [ ] **Feature Parity:** Calculate spectrograms of 1-second audio clips in Kotlin matching the ML team's Python format.
* [ ] **Cosine Similarity:** Write the similarity check comparing a new sound's embedding against the enrolled average prototype.
* [ ] **Margin & Confidence Gate:** Only trigger output if similarity > threshold and the distance margin is clear.

### 🗣️ Step 5: Audio Feedback, Storage & Testing (Day 9–11)
* [ ] **Android Room DB:** Create tables to store profiles, prototypes, and history events.
* [ ] **TTS Output:** Connect Android `TextToSpeech` or play pre-recorded MP3 files when a match occurs.
* [ ] **Testing:** Play audio clips near the phone to confirm correct translations and verify background battery usage ($< 5\%$ per hour).

---

## 3. Explaining the Jargon (For Beginners)

Here are the concepts you need to know, explained simply:

* **Foreground Service:** A background component in Android that displays a visible, non-dismissible notification (e.g., *"Voxa is listening..."*). It tells the Android OS: *"Hey, this task is highly important to the user, please do not close it to save RAM."*
* **WakeLock:** An Android power management feature that keeps the CPU running even when the phone screen turns off. Essential for real-time translation.
* **AudioRecord:** The native Android class used to read raw audio data directly from the microphone hardware.
* **PCM (Pulse Code Modulation):** The uncompressed, raw digital audio format we use. We capture it as an array of short integers (`ShortArray` in Kotlin).
* **TFLite Interpreter:** The TensorFlow Lite library that runs trained machine learning models on a mobile CPU/GPU. You load the model file (`.tflite`) and feed it audio arrays to get back numbers representing the sound (embeddings).
* **Cosine Similarity:** A simple math formula that measures how similar two arrays of numbers are. It outputs a score between `-1.0` (opposite) and `1.0` (identical). We use this to compare the current sound against the enrolled templates.
* **Room Database:** Android's official SQLite wrapper. It lets you save data (like enrolled words and logs) in a structured local database using normal Kotlin objects.

---

## 4. Beginner-Friendly Code Templates

To kickstart your coding, here are the basic structures you'll be writing:

### 1. The Jetpack Compose Home Screen Scaffold
```kotlin
// UI/HomeDashboard.kt
@Composable
fun HomeDashboard(
    isListening: Boolean,
    lastDetectedWord: String,
    onToggleListen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8F0)) // Warm background
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Voxa Dashboard", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Listening status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isListening) "🎙️ Active & Listening" else "🔇 Paused",
                    color = if (isListening) Color(0xFF7ED321) else Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Last phrase: $lastDetectedWord",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onToggleListen,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90D9)),
            modifier = Modifier.size(150.dp),
            shape = CircleShape
        ) {
            Text(if (isListening) "Pause" else "Start")
        }
    }
}
```

### 2. Loading TFLite and Running Inference
```kotlin
// ml/TFLiteEmbeddingExtractor.kt
import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

class TFLiteEmbeddingExtractor(context: Context) {
    private var interpreter: Interpreter? = null

    init {
        // Load the model file from app assets
        val fileDescriptor = context.assets.openFd("voxa_encoder.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        
        interpreter = Interpreter(modelBuffer)
    }

    fun getEmbedding(spectrogramData: Array<FloatArray>): FloatArray {
        // Prepare inputs and outputs arrays matching your TFLite model configuration
        val input = arrayOf(spectrogramData) 
        val output = Array(1) { FloatArray(128) } // Assumes a 128-dim embedding output
        
        interpreter?.run(input, output)
        return output[0]
    }
}
```

### 3. Cosine Similarity in Kotlin
```kotlin
// math/Similarity.kt
import kotlin.math.sqrt

fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f
    for (i in vectorA.indices) {
        dotProduct += vectorA[i] * vectorB[i]
        normA += vectorA[i] * vectorA[i]
        normB += vectorB[i] * vectorB[i]
    }
    if (normA == 0.0f || normB == 0.0f) return 0.0f
    return dotProduct / (sqrt(normA) * sqrt(normB))
}
```

---

## 5. Tips for Success
1. **Take it one module at a time:** Don't try to code the ML and UI together. Build the UI mockups first, then build the audio service, and finally merge the ML logic.
2. **Use Android Studio Emulators:** You can run Compose screens on virtual devices, but you'll need a physical Android phone to test the microphone and Foreground Service reliably.
3. **Ask for code checks:** When you write a class, show it to me. I will review it, help fix compile errors, and optimize code logic.

You've got this! Let's get started.
