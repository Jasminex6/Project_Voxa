# 🧪 Project Voxa (فوكسا)

> **"Amplifying communication patterns the child already has."**  
> CU AI Nexus Hackathon 2026 · Healthcare & Wellbeing / Inclusive AI & Accessibility

**Voxa** is an offline, edge-native assistive communication app for minimally verbal autistic children. It translates personalized child vocalizations into clear Egyptian Arabic speech in real-time, entirely on-device.

---

## 📁 Monorepo Structure

```
Voxa_Project/
├── Voxa/                  # 📱 Android App (Kotlin · Jetpack Compose · Room DB)
│   ├── app/               #     Main application module
│   └── build.gradle.kts   #     Module-level Gradle config
│
├── Docs/                  # 📚 Documentation, specs & lesson logs
│   ├── Project_Voxa_Description.md
│   ├── voxa_implementation_plan.md
│   ├── voxa_development_split.md
│   └── voxa_lessons.md
│
├── .gitignore             # Git ignore rules (Android, IDE, secrets)
└── README.md              # ← You are here
```

### Future directories (to be added by the team):
```
├── dsp-engine/            # 🔬 Dev A: Signal processing & DTW algorithms
├── models/                # 🧠 Dev A: Pre-trained TFLite models (Git LFS)
└── scripts/               # 🛠️ Shared utility & testing scripts
```

---

## 👥 Team & Responsibilities

| Role | Focus Area |
| :--- | :--- |
| **Developer A** | Core DSP algorithms: WebRTC VAD, MFCC extraction, DTW matching, TFLite speaker model |
| **Developer B** | Android app architecture: Compose UI, Room DB, Foreground Service, AI integration bridge |

See [voxa_development_split.md](Docs/voxa_development_split.md) for detailed ownership and contracts.  
See [voxa_implementation_plan.md](Docs/voxa_implementation_plan.md) for the full technical specification.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- Android SDK 34 (API 34)
- JDK 17
- A physical Android device with a microphone (emulator mic support is limited)

### Build & Run
1. Open the `Voxa/` directory in Android Studio.
2. Sync Gradle.
3. Connect a physical device via USB or Wi-Fi debugging.
4. Click **Run ▶️**.

---

## 📄 License
This project was created for the CU AI Nexus Hackathon 2026.
