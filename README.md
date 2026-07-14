# 🧪 Project Voxa (فوكسا)

> **"Amplifying communication patterns the child already has."**  
> *CU AI Nexus Hackathon 2026 · Healthcare & Wellbeing / Inclusive AI & Accessibility*

**Voxa** is a fully offline, on-device assistive communication Android app designed for minimally verbal and non-speaking autistic children. Many of these children communicate through consistent, personalized vocalizations — a specific hum, a repeated syllable, or a unique sound sequence — that only immediate caregivers can interpret. Voxa bridges this communication gap by acting as a **personalized vocal-intent translator**: caregivers enroll custom sound samples, and the app recognizes them on-device in real time, translating them into clear first-person Arabic speech (e.g., *"أنا عايز ميّه"* — *"I want water"*).

Beyond translation, Voxa helps children **practice pronunciation**, build **executive function** through parent-approved redeemable task quests, and provides a dedicated **emergency toolkit** — all within a secure, private, bilingual (Arabic / English) interface that requires no internet connection.

---

## ✨ Key Highlights

| Principle | Implementation |
|---|---|
| 🔒 **100 % Offline & Private** | All AI inference runs on-device — zero cloud calls, zero data leaves the phone |
| 🧠 **Personalized Few-Shot Learning** | Caregivers enroll as few as 5 audio samples per word; the app learns the child's unique vocal patterns |
| 🌍 **Bilingual UI** | Full Arabic (عربي) and English interface with runtime language switching |
| 🔊 **Dual Audio Output** | Pre-recorded Egyptian Arabic voice packs + Android TTS fallback with Tashkeel vowelization |
| 👨‍👩‍👧 **Dual Profiles** | Separate **Child** and **Caregiver** roles with PIN-protected caregiver access |
| ♿ **Accessibility-First** | Designed for children who are non-speaking, with large touch targets, emoji avatars, and calming color palettes |

---

## 📱 Implemented Screens

| # | Screen | Description |
|---|---|---|
| 1 | 🏠 **Home (Dashboard)** | Pulsing mic toggle for live listening, real-time translation display with confidence scores, volume meter, and activity log timeline |
| 2 | ➕ **Enrollment** | Guided multi-sample recording flow with live volume visualization, silence trimming, YAMNet embedding extraction, and prototypical centroid computation |
| 3 | 📚 **Library** | Manage enrolled words per child — view, replay, and delete custom vocabulary entries |
| 4 | 🎮 **Practice** | Duolingo-style pronunciation game with 6 built-in Egyptian Arabic words, listen-then-repeat flow, DTW-based similarity scoring, and star ratings |
| 5 | 🎯 **Quests** | Executive function system with role selection (Child / Caregiver PIN gate), parent-created tasks, child submission flow, point wallet, and a rewards store |
| 6 | 🚨 **Emergency** | One-tap phone dialer, SMS launcher, WhatsApp call/message/location sharing, and a hold-to-activate synthesized siren alarm |
| 7 | 👤 **Profile** | Create, switch, and delete child profiles; select avatar emoji and voice pack gender (Male / Female); P2P profile sharing via Google Nearby Connections |

Additionally:
- **Onboarding Flow** — 6-page guided first-time setup: language selection, permissions (mic, notifications, location), child profile creation, emergency contact setup, and a feature tour
- **Sidebar Settings** — Language toggle (EN ↔ AR), emergency contact editor, profile export/import (JSON), and timeline clearing

---

## 🏗️ Architecture — Audio Classification Pipeline (v4)

The current architecture uses **YAMNet-based Prototypical Matching** with centroid-based scoring:

```
    Microphone (16kHz Mono 16-bit PCM)
                   ↓
   ┌─────────────────────────────────┐
   │ Energy-Based VAD State Machine  │  → Extracts speech segments (0.4s – 2.0s)
   │ M=8 speech trigger, N=15 end   │    Discards silence, noise, too-short/long
   └─────────────────────────────────┘
                   ↓
   ┌─────────────────────────────────┐
   │   Silence Trim + Pad/Crop      │  → Normalizes to exactly 1.44s (23040 samples)
   │   Center-pad or center-crop    │    Matches enrollment preprocessing
   └─────────────────────────────────┘
                   ↓
   ┌─────────────────────────────────┐
   │   YAMNet Neural Encoder        │  → Extracts 2048-D Temporal-Halved embedding
   │   (yamnet.tflite — 15.3 MB)    │    Concatenation of first 2 YAMNet frames
   └─────────────────────────────────┘
                   ↓
   ┌─────────────────────────────────┐
   │   Prototypical Matcher          │  → Cosine similarity against enrolled centroids
   │   + Centroid Count Penalty      │    K-Means bifurcation for high-variance intents
   │   + OOD Gate (μ − 2σ)          │    Per-intent out-of-distribution threshold
   │   + Margin Gate (Δ ≥ 0.04)     │    Rejects ambiguous matches
   └─────────────────────────────────┘
                   ↓
   ┌─────────────────────────────────┐
   │   Arabic Playback System        │  → Gendered voice packs (boy/girl folders)
   │   + TTS Fallback + Tashkeel    │    Android TextToSpeech with vowelization
   └─────────────────────────────────┘
```

### Enrollment Pipeline

During enrollment, caregivers record multiple samples per word. Each sample is:
1. **Trimmed** — leading/trailing silence removed
2. **Normalized** — pad/crop to 1.44s window
3. **Encoded** — YAMNet extracts a 2048-D L2-normalized embedding
4. **QC Filtered** — outlier rejection via μ + 2σ rule
5. **Bifurcated** — if pairwise distances are high, K-Means (K=2) splits into two centroids
6. **OOD Calibrated** — per-intent threshold set at μ − 2σ of enrollment similarities

---

## 🎯 Quests & Rewards System

A structured executive function module that teaches responsibility through a **task → review → reward** loop:

| Role | Capabilities |
|---|---|
| 👨 **Caregiver** (PIN-protected) | Create quests with custom titles, descriptions, emoji icons, and point values; approve/reject child submissions; manage rewards store; view activity history |
| 👦 **Child** | View active quests; mark as "Done" or "Unable"; earn points into a digital wallet; browse and purchase rewards with accumulated points |

Rewards are real-world items created by the caregiver (e.g., "30 min iPad time" for 50 points). Purchased rewards appear in the caregiver's fulfillment queue.

---

## 🗣️ Speech Practice Module

A Duolingo-style pronunciation practice game shipped with 6 built-in Egyptian Arabic words:

| Word | Arabic | Emoji | Reference Audio |
|---|---|---|---|
| Water | مايه | 💧 | `practice/water_ref.wav` |
| Milk | لبن | 🥛 | `practice/milk_ref.wav` |
| Bread | عيش | 🍞 | `practice/bread_ref.wav` |
| Help | مساعدة | 🆘 | `practice/help_ref.wav` |
| Mom | ماما | 👩 | `practice/mama_ref.wav` |
| Dad | بابا | 👨 | `practice/baba_ref.wav` |

**Flow**: Listen to reference → Hold to record → MFCC extraction + DTW comparison → Similarity score (0–100%) → Star rating (1–3 ⭐) → Stats saved per child profile.

---

## 🚨 Emergency Module

Designed for real-world safety — all actions work without internet:

| Action | Method |
|---|---|
| 📞 **Quick Dial** | Opens phone dialer pre-filled with the saved emergency contact |
| ✉️ **SMS Alert** | Launches SMS with pre-composed emergency message |
| 💬 **WhatsApp Call** | Opens WhatsApp call to emergency contact |
| 💬 **WhatsApp Message** | Sends pre-composed alert via WhatsApp |
| 📍 **Send Location** | Fetches GPS coordinates and shares Google Maps link via WhatsApp |
| 🔊 **Emergency Siren** | Hold-to-activate synthesized sine wave alarm (600–850Hz oscillation) with fallback to bundled MP3 siren |

Emergency contacts and messages are configured during onboarding and editable from the sidebar.

---

## 🗂️ Data Layer

### Room Database (SQLite)

| Entity | Table | Key Fields |
|---|---|---|
| `ChildProfile` | `child_profiles` | name, gender, avatarEmoji, speakerEmbedding, isActive |
| `EnrolledIntent` | `enrolled_intents` | profileId (FK), intentName, outputPhrase, audioAssetPath, oodThreshold |
| `AcousticTemplate` | `acoustic_templates` | intentId (FK), templateFeatures (serialized centroids), templateFilePath |
| `PracticeStats` | `practice_stats` | profileId (FK), word, score, stars, timestamp |
| `QuestEntity` | `quests` | profileId, title, description, icon, points, status (PENDING/SUBMITTED/UNABLE/APPROVED/REJECTED) |
| `RewardEntity` | `rewards` | profileId, title, cost, icon |
| `PurchasedRewardEntity` | `purchased_rewards` | rewardId, profileId, timestamp, isFulfilled |

### Local Storage
- **Audio templates**: Saved as `.pcm` files in internal storage (paths stored in Room, not blobs)
- **Emergency contacts**: `SharedPreferences` (app-wide, not per-child)
- **Language preference**: `SharedPreferences` (`app_language` key: `"en"` / `"ar"`)

---

## 📁 Monorepo Layout

```
Voxa_Project/
├── Voxa/                          # 📱 Android Application
│   └── app/src/main/
│       ├── java/com/example/voxa/
│       │   ├── MainActivity.kt            # App entry, navigation, bottom bar
│       │   ├── ai/                        # 🧠 AI/ML Pipeline
│       │   │   ├── IVoxaClassifierEngine.kt   # Classification interface contract
│       │   │   ├── VoxaClassifierEngine.kt    # Full pipeline orchestrator (v4)
│       │   │   ├── VoxaVAD.kt                 # Energy-based VAD state machine
│       │   │   ├── YamnetEncoder.kt           # YAMNet TFLite 2048-D encoder
│       │   │   ├── PrototypicalMatcher.kt     # Centroid scoring, OOD/Margin gates
│       │   │   └── archive/                   # Legacy MFCC/DTW/SpeakerVerifier
│       │   ├── data/                      # 💾 Room Entities, DAOs, Database
│       │   ├── services/                  # 🎙️ Foreground Listener Service
│       │   ├── ui/                        # 🎨 Compose Screens & ViewModels
│       │   │   ├── screens/               #     Dashboard, Enrollment, Library,
│       │   │   │   │                      #     Practice, Emergency, Profile, Onboarding
│       │   │   │   └── quests/            #     Quests: RoleSelect, ChildPlayground,
│       │   │   │                          #     ParentDashboard, RewardsStore
│       │   │   └── theme/                 #     Color, Theme, Typography
│       │   └── utils/                     # 🔧 AudioPlayer, AudioFileHelper,
│       │                                  #     ProfileSharingManager, TFLiteModelLoader
│       ├── assets/
│       │   ├── yamnet.tflite              # YAMNet feature extractor (15.3 MB)
│       │   ├── ecapa_speaker_id.tflite    # ECAPA-TDNN speaker verification (20 MB)
│       │   └── practice/                  # Reference .wav files for practice mode
│       └── res/
│           ├── values/strings.xml         # English string resources
│           ├── values-ar/strings.xml      # Arabic string resources
│           └── raw/                       # Emergency siren MP3, sound effects
│
├── models/                        # 🧠 Pre-trained TFLite models (source copies)
├── notebooks/                     # 📓 Python research: MFCC, DTW, VAD, ECAPA, YAMNet prototyping
├── Docs/                          # 📚 Project plans, technical specs, team docs
├── Assets/                        # 🎨 Logo files and branding assets
└── README.md                      # ← You are here
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Database** | Room (SQLite) with KSP annotation processing |
| **AI/ML Runtime** | TensorFlow Lite (LiteRT) |
| **Audio Capture** | Android `AudioRecord` API (16kHz Mono PCM) |
| **Background Processing** | Android Foreground Service + WakeLock |
| **Location** | Google Play Services Fused Location Provider |
| **P2P Sharing** | Google Nearby Connections API (BLE + WiFi Direct) |
| **Serialization** | Gson |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 36 |
| **Build System** | Gradle (KTS) with version catalogs |

---

## 🚀 Build Instructions

### Prerequisites
- **Android Studio** Ladybug (2024.2+) or newer
- **Android SDK 36** (compile) / **SDK 26** (min)
- **JDK 11** configured in your IDE
- **A physical Android device** (microphone + speaker testing requires real hardware)

### Steps
1. Clone the repository.
2. Open Android Studio → **Open Project** → select the `Voxa/` directory.
3. Wait for Gradle sync to complete.
4. Connect your Android device via USB or wireless debugging.
5. Click **Run ▶️** (select `app` configuration).

---

## 👥 Team

**CU AI Nexus Hackathon 2026** — Cairo University  
Track: Healthcare & Wellbeing / Inclusive AI & Accessibility

---

*Built with ❤️ for every child who deserves to be heard.*
