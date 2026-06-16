# 👥 Voxa Development Split & Team Responsibilities

This document defines the team responsibilities, software dependencies, and code contracts for splitting the development of **Project Voxa** between two developers (Developer A: Core Algorithms & DSP, Developer B: App Architecture & UI).

---

## 🗺️ High-Level Responsibilities Matrix

| Component | Primary Owner | Description |
| :--- | :---: | :--- |
| **Microphone Capture Thread** | **Developer A** | Capturing raw PCM data in real-time. |
| **DSP Pipeline (MFCC, Denoising)** | **Developer A** | Filtering noise, extracting 40-dimensional features per frame. |
| **AI Matching Engine (DTW)** | **Developer A** | Scoring vocalizations against enrolled templates. |
| **Speaker Verification (TFLite)** | **Developer A** | Verifying if the vocalizer is the enrolled child. |
| **Android Foreground Service** | **Developer B** | App background lifecycles, notification, WakeLocks. |
| **Database & Cache (Room)** | **Developer B** | Profile settings, intent mappings, history, and template storage. |
| **Compose UI Screens** | **Developer B** | Dashboard, Enrollment UX, Profile settings. |
| **Arabic Audio TTS Playback** | **Developer B** | Loading pre-recorded human MP3 voice assets. |

---

## 🛠️ Developer A: Core Algorithms & DSP Engine

Developer A is responsible for the mathematics, signal processing, and audio analysis stack. They build a self-contained, testable engine that does not require any Android UI components.

### 📋 Key Responsibilities
1. **Denoising Pipeline:** Implementing volume normalization and Spectral Subtraction filters on raw PCM frames.
2. **Feature Extraction:** Creating the 40-D MFCC feature extractor (13 coefficients + energy + deltas).
3. **Template Matcher:** Implementing Dynamic Time Warping (DTW) with margin gate checks (`second_best_distance - best_distance > margin_threshold`).
4. **Child Speaker Verification:** Integrating TensorFlow Lite with the pre-trained speaker verification model.

### 📦 Dependencies (Developer A)
*   **WebRTC VAD wrapper** (e.g., JNI/NDK native library) for Speech Activity Detection.
*   **FastDTW Algorithm** (optimised C++ library or high-speed Kotlin port) to process alignments in <30ms.
*   **FFT Library:** KissFFT (via NDK) or a Kotlin FFT library to transform time-domain audio to frequency domain for MFCCs.
*   **TensorFlow Lite Task Library** (`org.tensorflow:tensorflow-lite-task-audio`) or standard TFLite Runtime for the speaker verification gate.

---

## 📱 Developer B: App Architecture, UI & DB Layer

Developer B is responsible for the Android system integrations, persistent local storage, background services, and styling.

### 📋 Key Responsibilities
1. **Jetpack Compose Screens:**
    *   `PermissionRequiredScreen` to request microphone access.
    *   `EnrollmentScreen` that records 7 samples for an intent and shows calibration feedback.
    *   `VoxaDashboard` to start/stop active listening and show translated outputs.
2. **Foreground Service Lifecycle:** Managing [VoxaListenerService.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/services/VoxaListenerService.kt), notification channels, and obtaining CPU `WakeLock` to prevent the OS from killing the app in Doze Mode.
3. **Data Persistence (Room DB):** Saving profiles, mapping child intents to output phrases (Egyptian Arabic), and managing paths to serialized template data.
4. **Speech Output:** Initializing `MediaPlayer` or `Jetpack Media3 ExoPlayer` to play Egyptian Arabic voice clips.

### 📦 Dependencies (Developer B)
*   **Jetpack Compose:** `androidx.compose.ui`, `androidx.compose.material3`.
*   **Room Database:** `androidx.room:room-runtime`, `androidx.room:room-compiler`, `androidx.room:room-ktx`.
*   **Lifecycle Extensions:** `androidx.lifecycle:lifecycle-service` for foreground service lifecycle tracking.
*   **Navigation Compose:** `androidx.navigation:navigation-compose` to switch screens.

---

## 🤝 Code Contracts (The Shared Interface)

To work in parallel without blocking each other, both developers must agree on the **interfaces** and **data structures** that link their modules.

### 1. The Audio Format Contract
The raw audio data passing between components must be standard:
*   **Sample Rate:** 16,000 Hz
*   **Encoding:** 16-bit linear PCM (represented as `ShortArray` in Kotlin)
*   **Channels:** Mono (1 channel)
*   **Frame Size:** 20ms or 30ms blocks (320 or 480 short samples per frame) for VAD compatibility.

### 2. The Engine API Contract
Developer A writes the engine class, but Developer B can use a **Mock Engine** matching this interface to build the UI and service in parallel:

```kotlin
interface IVoxaClassifierEngine {
    /**
     * Initializes the engine, loading TF Lite models and configuring thresholds.
     */
    fun initialize(context: android.content.Context)

    /**
     * Evaluates a candidate audio recording segment (0.4s to 2.0s).
     * @param pcmData The recorded vocalization.
     * @param enrolledIntents List of enrolled child intents to compare against.
     * @return The matching intent name, or "unknown" if confidence is low.
     */
    fun classifyUtterance(pcmData: ShortArray, enrolledIntents: List<EnrolledIntent>): String
    
    /**
     * Processes raw recorded samples of a new intent, normalizes features, 
     * and returns a serialized template representation (ready to save in database).
     */
    fun processEnrollmentSample(pcmData: ShortArray): FloatArray
}
```

### 3. Database Data Transfer Object (DTO)
Developer B's database stores intents, and Developer A's algorithms read their templates:

```kotlin
data class EnrolledIntent(
    val id: Long,
    val intentName: String, // e.g. "Water"
    val outputPhrase: String, // e.g. "أنا عايز ميّه"
    val voiceAssetPath: String, // e.g. "audio/masc/water.mp3"
    val templates: List<FloatArray> // List of 7 extracted feature templates (Developer A's MFCC output)
)
```

---

## 🏁 Integration Milestones

1. **Milestone 1 (Interface Lock):** Code the `IVoxaClassifierEngine` interface and the DTO data classes. Dev B implements a `MockVoxaEngine` that randomly matches "Water" or "Help" after 1 second of audio.
2. **Milestone 2 (Isolated Development):** 
    *   Dev A implements the MFCC extraction and DTW matching in Kotlin, testing it with pre-recorded WAV files.
    *   Dev B builds the UI screens, Room database schemas, and background service lifecycle using the Mock Engine.
3. **Milestone 3 (Integration):** Swap the `MockVoxaEngine` with Dev A's real engine inside `VoxaListenerService`. 
4. **Milestone 4 (Fine-Tuning):** Calibrate the margin thresholds and test in noisy rooms.
