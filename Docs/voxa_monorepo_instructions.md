# 🤖 Project Voxa — Team & AI Agent Instructions

This document provides a copy-pasteable prompt and a set of rules for **any** developer or AI agent joining the **Project Voxa** monorepo. It ensures that everyone respects the folder boundaries, follows clean Git practices, and implements the correct interfaces and audio standards.

---

## 📋 Copy-Paste Prompt for Team Members & Their AI Agents

````markdown
You are an AI coding assistant helping a developer on **Project Voxa**—an edge-native, offline assistive communication application designed for minimally verbal autistic children. 

We are developing in a **Git monorepo** with multiple team members working on different layers of the stack. To prevent compile issues, merge conflicts, and architectural drift, you must strictly adhere to the monorepo structure, ownership guidelines, and system standards outlined below.

---

### 📁 1. Monorepo Directory Layout & Ownership
All developers work in distinct areas of the repository. Do not modify directories outside your assigned domain without prior coordination:

```
Voxa_Project/              ← Git Monorepo Root
├── Voxa/                  ← 📱 Developer B (UI & App Shell Workspace)
│   ├── app/src/main/...   - Contains Jetpack Compose UI, Room DB, Foreground Services
│   └── build.gradle.kts   - Android module-level Gradle configuration
│
├── dsp-engine/            ← 🔬 Developer A (DSP & Algorithms Workspace)
│   └── ...                - WebRTC VAD native JNI code, MFCC feature extraction, DTW matching logic
│
├── models/                ← 🧠 Machine Learning Artifacts (Shared)
│   └── ...                - Pre-trained ECAPA-TDNN speaker verification models (.tflite files)
│
├── assets/                ← 🔊 Sound Curation Workspace (Audio Packs)
│   └── ...                - Human Egyptian Arabic pre-recorded .mp3 clips (male/female child packs)
│
├── Docs/                  ← 📚 Documentation
│   ├── voxa_implementation_plan.md
│   └── voxa_development_split.md
│   └── voxa_monorepo_instructions.md
│
├── .gitignore             # Root-level ignore configurations (DO NOT MODIFY INDEPENDENTLY)
└── README.md              # Project onboarding instructions
```

---

### ⚠️ 2. Monorepo Git Hygiene & Rules of Engagement
1.  **Strict Path Isolation:** Do not write or edit code outside your designated folder (e.g., if you are working on the DSP engine, stay in `dsp-engine/`; do not modify UI state files in `Voxa/` unless explicitly instructed).
2.  **Respect .gitignore:** The root `.gitignore` blocks Android build outputs (`.gradle/`, `build/`), IDE files (`.idea/`, `.vscode/`), and local machine configs (`local.properties`). Do not stage or force-add these files.
3.  **Modular Code Design:** Write self-contained, library-style code that can be integrated as modular dependencies. Avoid tight coupling between the UI/Service layers and the algorithmic layers.

---

### 🤝 3. Common System & Integration Contracts
To ensure all modules run together seamlessly, conform strictly to these standards:

#### A. Raw Audio Stream Specifications
All real-time audio captured from the device and passed to downstream engines must be:
*   **Sample Rate:** 16,000 Hz
*   **Channels:** Mono (1 channel)
*   **Encoding:** 16-bit linear PCM (represented as `ShortArray` in Kotlin/Java)
*   **Frame Window:** 20ms (320 samples) or 30ms (480 samples) chunks (WebRTC VAD standard)

#### B. Unified Classification Contract
Any algorithmic matching pipeline must implement the shared engine interface:
```kotlin
interface IVoxaClassifierEngine {
    fun initialize(context: android.content.Context)
    fun classifyUtterance(pcmData: ShortArray, enrolledIntents: List<EnrolledIntent>): String
    fun processEnrollmentSample(pcmData: ShortArray): FloatArray
}
```

---

### 🤖 4. AI Agent Workflow Instructions
1.  **Before writing code:** Locate your developer's assigned directory and understand their specific task bounds.
2.  **Mocking and Stubbing:** If you need features from another developer's module that isn't finished yet, write a clean interface and mock the output (e.g., use a `MockClassifier` instead of hardcoding changes in another folder).
3.  **No OS-specific absolute paths:** Always resolve file paths relatively from the project root or Android context variables.
````
