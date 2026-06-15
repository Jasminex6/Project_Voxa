# 🧪 Project Voxa 

> **"Amplifying communication patterns the child already has."**  
> *CU AI Nexus Hackathon 2026 · Healthcare & Wellbeing / Inclusive AI & Accessibility*

**Voxa (فوكسا)** is an offline, edge-native assistive communication application designed for minimally verbal or non-speaking autistic children. Many non-speaking children communicate using consistent, personalized vocal approximations (e.g., a specific hum, a repeated syllable, or a unique sound sequence) that only immediate caregivers can interpret. Voxa acts as a **personalized vocal-intent translator**, mapping these unique vocalizations on-device to clear, first-person Egyptian Arabic speech (e.g., *"أنا عايز ميّه"* - *"I want water"*).

---

## 🚀 Core Features

*   **Edge-Native & 100% Offline:** Runs entirely on-device (under 30ms inference) without external servers, ensuring absolute privacy and zero dependency on internet access.
*   **Few-Shot Enrollment:** Caregivers enroll custom vocalizations by recording just 7 training samples directly on the device.
*   **Multi-Template Consensus Scoring:** Compares incoming audio against multiple enrolled templates and averages the best matches to absorb natural variations in the child's voice.
*   **Multi-Barrier Validation (Margin Gate):** Restricts false positives (TV, door slams, background chatter) using a strict best-vs-second-best margin validation check.
*   **Speaker Verification Gate:** Utilizes a lightweight, on-device TensorFlow Lite speaker embedding model to verify that the sound originates from the enrolled child rather than a sibling or television.
*   **Pre-recorded Human Voice Packs:** Plays natural, child-like Egyptian Arabic dialect audio clips from local assets instead of robotic offline TTS engines.

---

## 🏗️ Architecture & Audio Processing Pipeline

When the microphone is active, audio data flows through a strict multi-layer pipeline:

```
    Microphone (16kHz Mono 16-bit PCM)
                   ↓
   [ WebRTC Voice Activity Detector ]    → Discards ambient silence/noise
                   ↓ (0.4s - 2.0s segments)
     [ Signal Denoising & Norm ]         → Spectral subtraction & RMS normalization
                   ↓
     [ Speaker Verification Gate ]       → TF Lite model checks child profile similarity
                   ↓ (Similarity > 0.75)
    [ Acoustic Feature Extractor ]       → Computes 40-D MFCC + Deltas + Delta-Deltas
                   ↓
      [ DTW Template Matcher ]           → Matrix alignment path calculation
                   ↓
       [ Margin & Confidence Gate ]      → Checks if best match is significantly closer
                   ↓
      [ Arabic Playback System ]         → Plays corresponding local MP3 voice asset
```

---

## 📁 Monorepo Layout

The repository is organized as a unified monorepo containing the mobile application, custom DSP engines, model assets, and reference documentation:

```
Voxa_Project/
├── Voxa/                  # 📱 Android Application (Jetpack Compose, Room DB, Foreground Services)
│   ├── app/               #     Main application source files
│   └── build.gradle.kts   #     Module gradle configurations
│
├── dsp-engine/            # 🔬 Digital Signal Processing (MFCC, WebRTC VAD JNI wrappers, FastDTW)
│
├── models/                # 🧠 Pre-trained TF Lite models (Speaker Verification embeddings)
│
├── assets/                # 🔊 Voice Packs (Egyptian Arabic male/female child MP3 files)
│
├── Docs/                  # 📚 Project reference sheets, technical plans, and lessons
└── README.md              # ← Core project overview
```

---

## 🛠️ System Requirements & Build Instructions

### Prerequisites
*   **Android Studio** Ladybug (2024.2+) or newer.
*   **Android SDK 34** (target and compile API levels).
*   **JDK 17** configured in your IDE.
*   **A physical Android device** with developer options enabled (microphone testing is highly recommended on physical hardware).

### Building the Project
1.  Clone the repository to your local workspace.
2.  Open Android Studio and choose **Open Project**, selecting the `Voxa/` directory.
3.  Let the Gradle sync complete.
4.  Connect your physical Android device via USB or wireless debugging.
5.  Click **Run ▶️** (select `app` configuration).
