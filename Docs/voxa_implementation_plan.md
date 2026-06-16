# 📋 Project Voxa — Long-Term Technical Reference & Implementation Plan

This document serves as the formal technical specification and long-term implementation plan for **Project Voxa**. It provides the architectural specifications, mathematical definitions, database schemas, and codebase patterns required to implement the personalized, offline vocal-intent translator.

---

## 🛠️ Section 1: Developer A — Core Algorithms & DSP Engine
Developer A is responsible for creating a self-contained, high-performance digital signal processing (DSP) and machine learning engine in Kotlin/Java or C++ (via JNI).

### 1.1 WebRTC Voice Activity Detection (VAD)
*   **Purpose:** Filters continuous background noise/silence to identify human vocalizations (0.4s to 2.0s segments) and prevent processing silent buffers to conserve battery.
*   **Technical Specifications:**
    *   **Input Chunk Size:** Must process audio in standard window sizes of **20ms** (320 samples) or **30ms** (480 samples).
    *   **VAD Aggressiveness Mode:** Configurable from `0` (most lenient) to `3` (most aggressive). Recommended setting is `2` or `3` to filter out typical room noise.
    *   **State Machine:**
        *   *Silence state:* Skip frames.
        *   *Speech trigger state:* Initiated when $M$ consecutive frames are flagged as speech (e.g., $M = 8$ or $160\text{ms}$).
        *   *Speech segment collection:* Keep collecting frames until $N$ consecutive silent frames are flagged (e.g., $N = 15$ or $300\text{ms}$).
        *   *Length Validation:* If the total collected segment is $<400\text{ms}$ or $>2000\text{ms}$, discard the buffer.

### 1.2 Acoustic Feature Extractor (MFCC)
Transforms raw time-domain PCM waveforms into a robust frequency-domain feature matrix.
*   **Frame Preparation:** Frame size of 25ms (400 samples) with a 10ms overlap (160 samples hop size). Apply a **Hamming Window** to prevent spectral leakage:
    $$w[n] = 0.54 - 0.46 \cos\left(\frac{2\pi n}{N-1}\right)$$
*   **Fast Fourier Transform (FFT):** Convert each window into a 512-point Power Spectrum.
*   **Mel Filterbank Filtering:** Compute the energy of the power spectrum through 40 triangular Mel-scale filters distributed between 100 Hz and 8000 Hz. The conversion from linear frequency ($f$) to Mel frequency ($m$) is:
    $$m = 2595 \log_{10}\left(1 + \frac{f}{700}\right)$$
*   **Discrete Cosine Transform (DCT):** Apply DCT-II to log-filterbank energies to extract 13 Mel-Frequency Cepstral Coefficients (MFCCs).
*   **Dynamic Features:** Compute first derivatives (Delta $\Delta$) and second derivatives (Delta-Delta $\Delta\Delta$) of the 13 MFCCs + frame energy to capture temporal speech transitions:
    $$\Delta_t = \frac{\sum_{n=1}^{K} n(c_{t+n} - c_{t-n})}{2 \sum_{n=1}^{K} n^2}$$
*   **Result:** A $T \times 40$ matrix for an utterance of $T$ frames (13 MFCC + 13 Delta + 13 Delta-Delta + 1 Energy).

### 1.3 Personalized Template Matcher (Dynamic Time Warping)
Matches an incoming test feature matrix against caregiver-enrolled templates.
*   **Core Math:** Given a test matrix $Y$ (length $M$) and a template matrix $X$ (length $N$), construct an $M \times N$ distance grid where each cell $(i, j)$ contains the Euclidean distance between feature vectors:
    $$d(i, j) = \sqrt{\sum_{k=1}^{40} (Y_{i, k} - X_{j, k})^2}$$
*   **Path Alignment:** Solve for the minimum cumulative distance path $D(i, j)$ using dynamic programming:
    $$D(i, j) = d(i, j) + \min\big(D(i-1, j), D(i, j-1), D(i-1, j-1)\big)$$
*   **Path Length Normalization:** Since alignment path lengths vary depending on speaking speed, normalize the final distance by dividing by the path length $L$:
    $$\text{DTW Distance} = \frac{D(M, N)}{L}$$

### 1.4 Speaker Verification Embedding Model
*   **Purpose:** Ensures only the enrolled child triggers translations, rejecting sibling voices or TV chatter.
*   **TFLite Model Structure:** Lightweight ECAPA-TDNN model optimized for mobile devices.
*   **Model Input:** 80-channel Log-Mel Spectrogram features.
*   **Model Output:** A fixed-size 192-dimensional floating-point vector (speaker embedding).

---

## 📱 Section 2: Developer B — Android App, UI, DB & AI Integration Bridge
Developer B builds the Android system layer, persistent storage layers, user interface views, and handles the mathematical code linking the DSP outputs to application events.

### 2.1 The Foreground Service & Background Threading
Continuous background recording on modern Android requires strict lifecycle management:
*   **Service Class:** [VoxaListenerService.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/services/VoxaListenerService.kt) inherits from `android.app.Service`.
*   **Foreground Registration:** Must call `startForeground()` within `onStartCommand()` with service type `FOREGROUND_SERVICE_TYPE_MICROPHONE` (Android 14 / API 34+ requirement).
*   **Persistent Notification:** Displays a notification detailing active status:
    *   *Channel ID:* `"voxa_listener_channel"`
    *   *Channel Name:* `"Voxa Listening Service"`
    *   *Importance:* `IMPORTANCE_LOW` (prevents annoying popping sounds while remaining persistent).
*   **Power Management (WakeLock):**
    ```kotlin
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Voxa::ListenerWakeLock").apply {
        acquire()
    }
    ```
*   **Audio Recording Loop Thread:**
    *   Initialize `AudioRecord` (16kHz, Mono, 16-bit PCM).
    *   Execute a blocking read loop inside a dedicated background thread:
        ```kotlin
        val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audioData = ShortArray(bufferSize)
        while (isListening) {
            val readBytes = audioRecord.read(audioData, 0, bufferSize)
            if (readBytes > 0) {
                // Pipe to the classifier pipeline
                classifierPipeline.pushBuffer(audioData.clone())
            }
        }
        ```

### 2.2 Room Database Schemas
Exposes persistence for caregiver profiles, intent mappings, and template vectors.

```kotlin
// 1. Profile Entity
@Entity(tableName = "child_profiles")
data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String, // "Male" or "Female" (swaps voice packs)
    val speakerEmbedding: FloatArray? = null, // Enrolled voice print
    val isActive: Boolean = false
)

// 2. Intent Entity
@Entity(
    tableName = "enrolled_intents",
    foreignKeys = [ForeignKey(
        entity = ChildProfile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class EnrolledIntent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val intentName: String, // e.g. "Water"
    val outputPhrase: String, // e.g. "أنا عايز ميّه"
    val audioAssetPath: String // Path to standard Egyptian Arabic MP3
)

// 3. Acoustic Template Entity
@Entity(
    tableName = "acoustic_templates",
    foreignKeys = [ForeignKey(
        entity = EnrolledIntent::class,
        parentColumns = ["id"],
        childColumns = ["intentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AcousticTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intentId: Long,
    val templateFeatures: FloatArray // Serialized MFCC matrix
)
```

### 2.3 Compose UI & Audio Playback
*   **Dashboard View:** State-driven toggle layout using HSL/M3 styling. Displays a dynamic log of historical translations with timestamps.
*   **7-Sample Enrollment View:** Guides the caregiver through recording. Displays a calibration progress meter (1 to 7 rings). Integrates a raw audio visualizer reading decibel levels:
    $$\text{dB} = 20 \log_{10}\left(\frac{\text{RMS}}{32767}\right)$$
*   **Voice Player Engine:** Utility wrapper mapping SQLite `audioAssetPath` variables into a cached Android `MediaPlayer` pipeline to playback local files quickly.

---

## 🤝 Section 3: Shared Area & AI Integration Bridge
This is the integration zone where Developer B links the algorithmic structures built by Developer A into the Android system loop.

### 3.1 The Shared Interface Contracts
All operations execute against the unified engine interface:

```kotlin
interface IVoxaClassifierEngine {
    /** Loads model variables, configures threshold bounds, and registers database mappings */
    fun initialize(context: android.content.Context)
    
    /** Processes PCM buffers, running feature extraction and DTW matching */
    fun classifyUtterance(pcmData: ShortArray, enrolledIntents: List<EnrolledIntent>): String
    
    /** Processes a raw enrollment utterance to extract its canonical MFCC FloatArray template */
    fun processEnrollmentSample(pcmData: ShortArray): FloatArray
}
```

### 3.2 TensorFlow Lite Model Mapping (Developer B Integration Task)
Load the pre-trained `ecapa_speaker_id.tflite` model directly into memory:

```kotlin
fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(modelName)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    return fileChannel.map(
        FileChannel.MapMode.READ_ONLY, 
        fileDescriptor.startOffset, 
        fileDescriptor.declaredLength
    )
}
```

### 3.3 Vector Cosine Similarity Math (Developer B Integration Task)
Implement the mathematical comparison of the 192-dimensional speaker verification vector embeddings. The formula measures the angular cosine between vector $\vec{A}$ (the candidate utterance) and vector $\vec{B}$ (the child's enrolled model):

$$\text{Cosine Similarity} = \frac{\sum_{i=1}^{D} A_i B_i}{\sqrt{\sum_{i=1}^{D} A_i^2} \cdot \sqrt{\sum_{i=1}^{D} B_i^2}}$$

```kotlin
fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f
    for (i in vectorA.indices) {
        dotProduct += vectorA[i] * vectorB[i]
        normA += vectorA[i] * vectorA[i]
        normB += vectorB[i] * vectorB[i]
    }
    return if (normA > 0.0f && normB > 0.0f) {
        dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
    } else {
        0.0f
    }
}
```

### 3.4 Margin & Confidence Gating Logic (Developer B Integration Task)
Once Developer A's engine outputs the array of DTW alignment distances, Developer B applies the margin-gating checks:

1.  **Rank Matches:** Sort candidate intents by lowest DTW distance:
    $$\text{Sorted Intents} = [I_1, I_2, \dots, I_K] \quad \text{where} \quad d(I_1) \le d(I_2) \le \dots \le d(I_K)$$
2.  **Margin Check:** Compute difference between the best match and the second-best match:
    $$\text{Margin} = d(I_2) - d(I_1)$$
3.  **Threshold Gate:** A valid trigger must pass both conditions:
    *   $d(I_1) < \theta_{\text{absolute}}$ (Best match is close enough to the template).
    *   $\text{Margin} > \theta_{\text{margin}}$ (Best match is significantly closer than the second alternative).

---

## 🏁 Section 4: Project Integration Milestones
1.  **Milestone 1 (Interfaces & Mocking):** Dev B implements the database schemas and binds the background service to a `MockVoxaEngine` that simulates outputs.
2.  **Milestone 2 (DSP Integration):** Dev A delivers the compiled WebRTC VAD JNI files and MFCC/DTW Kotlin packages. Dev B swaps the mock engine wrapper with the real DSP pipeline.
3.  **Milestone 3 (AI Verification Integration):** Dev B loads the TFLite speaker model, implements the cosine similarity helper, and wraps the processing thread inside the speaker-verification gate.
4.  **Milestone 4 (Calibration & Sandbox Testing):** Fine-tune threshold parameters ($\theta_{\text{absolute}}$ and $\theta_{\text{margin}}$) in quiet vs noisy room simulations.
