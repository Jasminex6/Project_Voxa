# 🧪 Project Voxa (فوكسا) — Full Technical Summary

> **"Amplifying communication patterns the child already has."**  
> CU AI Nexus Hackathon 2026 · Healthcare & Wellbeing / Inclusive AI & Accessibility

---

## 1. What Is Project Voxa?

**Voxa (فوكسا)** is an offline, edge-native assistive communication application designed for minimally verbal or non-speaking autistic children. 

Many non-speaking children communicate using consistent, personalized vocal approximations (e.g., a specific hum, a repeated syllable, or a unique sound sequence). While immediate family members learn to interpret these sounds over time, external communication partners—such as teachers, doctors, emergency responders, or peers—cannot understand them. This disconnect leads to severe frustration, distress, and behavioral meltdowns.

Voxa resolves this communication gap by acting as a **personalized, small-vocabulary offline vocal-intent translator**. Rather than training a generic speech recognition engine, Voxa enables caregivers to enroll a custom "sound dictionary" for a specific child. When the child vocalizes during an active session, the app matches the sound on-device and translates it into clear, first-person Egyptian Arabic speech (e.g., *"أنا عايز ميّه"*).

---

## 2. Core Architecture & Pipeline

```
Microphone
    ↓
Pipeline Layer 1: WebRTC Voice Activity Detection (VAD)
    → Filters silence/noise & extracts candidate segments (0.4s – 2.0s)
    ↓
Pipeline Layer 2: Acoustic Feature Extractor (MFCC)
    → Computes normalized MFCC features per frame
    ↓
Pipeline Layer 3: Personalized Template Matcher (Dynamic Time Warping)
    → Computes alignment distance to enrolled templates
    → Model Size: < 2 MB | Inference: < 30ms on-device
    ↓
Pipeline Layer 4: Confidence & Margin Gate
    → Checks if best distance < threshold & second-best margin > margin_threshold
    → Yes → Trigger TTS Playback
    → No  → Stay silent / Trigger safe fallback
    ↓
Pipeline Layer 5: Egyptian Arabic Playback System
    → Pre-recorded audio clips played from local assets
```

### The Acoustic Feature Representation

| Feature | Dimension | Purpose |
|---|---|---|
| MFCC Coefficients (1–13) | 13 | Vocal tract shape, phonetic contour (resilient to pitch variations) |
| Delta MFCCs (1–13) | 13 | Temporal changes and transitional speed of the vocal sound |
| Delta-Delta MFCCs (1–13) | 13 | Acceleration of spectral envelope transitions |
| Energy & RMS | 1 | Loudness/stress dynamics to help separate quiet hums from loud cries |
| **Total** | **40** | **40-Dimensional Feature Vector per Audio Frame** |

### Example Enrolled Intent Mapping

| Intent | Child's Vocalization | Phrase Output (Egyptian Arabic - Fem/Masc) | Output (English Translation) |
|---|---|---|---|
| **Water** | *“mmm” / “mmmuh”* | "أنا عايز ميّه" / "أنا عايزة ميّه" | *"I want water"* |
| **Help** | *“aaa”* | "أنا محتاج مساعدة" / "أنا محتاجة مساعدة" | *"I need help"* |
| **Bathroom** | *“ba-ba”* | "أنا عايز الحمام" / "أنا عايزة الحمام" | *"I need the bathroom"* |
| **Stop** | *Short refusal sound* | "لأ" / "كفاية" | *"No / Stop"* |
| **Pain** | *Repeated distress sound* | "في حاجة بتوجعني" | *"Something hurts me"* |
| **Fallback** | *Low-confidence match* | "مش متأكد. ممكن تساعدني؟" | **[Safe Fallback Phrase or Silent]** |

### Audio Input Settings
* **Sample Rate:** 16,000 Hz, mono, 16-bit PCM.
* **Segment Length:** 0.4 to 2.0 seconds (minimum valid segment: 300ms, maximum: 2.5s).
* **Frame Size for VAD:** 20–30 ms chunks.

---

## 3. Identified Problems & Solutions

### Problem 1 — Zero Pre-Existing Datasets

**The Problem:** There are no datasets containing child-specific, idiosyncratic autistic vocal approximations in Egyptian Arabic household environments. The acoustic characteristics of a non-verbal child's sounds vary dramatically from adult or neurotypical children's speech.

**Solution — Few-Shot Personalized Enrollment:**
Voxa avoids general models entirely. The caregiver records 7 training examples of the child's specific vocalization directly on the device. The app extracts features and builds a child-specific template library, executing inference via **Dynamic Time Warping (DTW)** template matching.

```python
import numpy as np
from fastdtw import fastdtw
from scipy.spatial.distance import euclidean

def compute_dtw_distance(test_features, template_features):
    # test_features, template_features: matrices of shape (n_frames, 13_mfcc)
    distance, path = fastdtw(test_features, template_features, dist=euclidean)
    # Normalize by path length to make distance duration-independent
    return distance / len(path)
```

---

### Problem 2 — Intrapersonal Vocal Inconsistency

**The Problem:** Non-verbal autistic vocalizations exhibit massive variance, even for the same child, depending on motor controls, stress, posture, or fatigue. A single static template will cause high false-rejection rates.

**Solution — Multi-Template Consensus Scoring:**
Caregivers record multiple samples. During inference, the incoming vocalization is compared to all templates of an intent, and we compute the average of the **best two distances** to represent the score, absorbing natural speech variations.

```python
def compute_intent_score(test_features, enrolled_intent):
    distances = []
    # Compare against all 5-6 stored templates for this specific intent
    for template in enrolled_intent.templates:
        dist = compute_dtw_distance(test_features, template)
        distances.append(dist)
    
    # Sort and average the best 2 matches (lowest DTW distance)
    best_two = sorted(distances)[:2]
    return np.mean(best_two)
```

---

### Problem 3 — False Positives & Alert Fatigue

**The Problem:** Household noises (TV, sibling chatter, door slams, dropping objects) could match a template, triggering incorrect translations. A false "Pain" or "Bathroom" alert damages caregiver trust and frustrates the child.

**Solution — Multi-Barrier Validation Gate:**
To trigger a translation, the classification must pass three strict verification layers: a duration check, an absolute distance threshold, and a **best-vs-second-best margin check**.

```python
def classify_utterance(test_features, enrolled_intents, thresholds, margin_threshold=1.2):
    scores = {}
    for intent in enrolled_intents:
        scores[intent.name] = compute_intent_score(test_features, intent)
    
    # Sort by lowest DTW distance
    ranked_intents = sorted(scores.items(), key=lambda x: x[1])
    best_intent, best_score = ranked_intents[0]
    second_intent, second_score = ranked_intents[1]
    
    # Margin check: ensure the best match is significantly closer than the second best
    margin = second_score - best_score
    
    # Verify thresholds
    if best_score < thresholds[best_intent] and margin > margin_threshold:
        return best_intent
    return "unknown"
```

---

### Problem 4 — Sibling or TV Speech Interference

**The Problem:** Other voices in the room (e.g., a sibling playing or television speech) may accidentally trigger the child's personal template.

**Solution — Speaker Verification Gate:**
Implement an optional second-stage speaker verification gate using a lightweight, pre-trained **ECAPA-style speaker embedding model** optimized for mobile devices. The gate checks if the voice matches the enrolled child's vocal tract profile before executing template matching.

```python
def is_enrolled_child(audio_segment, child_speaker_embedding, model_interpreter):
    # Extract speaker embedding from segment
    current_embedding = extract_speaker_embedding(audio_segment, model_interpreter)
    cosine_similarity = np.dot(current_embedding, child_speaker_embedding) / (
        np.linalg.norm(current_embedding) * np.linalg.norm(child_speaker_embedding)
    )
    # Verify speaker similarity surpasses the threshold (e.g., 0.75)
    return cosine_similarity > 0.75
```

---

### Problem 5 — High Quality Offline Arabic Speech Output

**The Problem:** Text-To-Speech (TTS) engines that work offline are highly robotic or do not support Egyptian Arabic dialect properly.

**Solution — Custom Human Voice Pack Assets:**
The caregiver can select the child's gender and assign pre-recorded, natural Egyptian Arabic voice clips recorded by human voice actors (male/female child-like voices). The app ships with high-quality, pre-recorded audio assets in `assets/audio/` and plays the appropriate file.

---

### Problem 6 — Android Background Microphone Constraints

**The Problem:** Android actively restricts background microphone access to prevent spyware. Standard background tasks listening continuously will be terminated by Doze Mode.

**Solution — Foreground Service & WakeLocks:**
Implement continuous listening within an Android `ForegroundService` that displays a persistent notification. It utilizes a `WakeLock` to keep the CPU awake during active listening sessions.

```kotlin
class VoxaListenerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification()
        // Requires FOREGROUND_SERVICE_TYPE_MICROPHONE on Android 14+
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Voxa::AudioListenerLock").apply {
                acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        }
        startListening()
        return START_STICKY
    }
}
```

---

## 4. Background Noise — Deep Dive

Household background noise is the primary barrier to robust DTW matching. Voxa implements a structured noise handling strategy:

| Noise Problem | Solution Layer | Technical Implementation |
|---|---|---|
| **Silence/Room Hums** | WebRTC VAD | Truncates silent frames, prevents processing empty segments, saving battery. |
| **Volume Differences** | Loudness Normalization | Applies peak/RMS normalization to the input clip before template matching. |
| **Noise Feature Pollution** | Spectral Subtraction | Estimates background noise during silent intervals and subtracts it from the signal. |
| **Transient Noise Spikes** | Margin Gate & Voting | Margin check (`second_best - best > margin_threshold`) and agreement over multiple windows rejects brief noise spikes (door slams). |
| **Continuous Loud Noise** | Dynamic Margin Threshold | Adaptive margin limits: if background RMS rises, the confidence gate automatically tightens. |

---

## 5. Data Resources

### Datasets for Transfer Learning and Speaker Verification

| Dataset | Contents | How to Use |
|---|---|---|
| **DEMAND** | 18 real-world noise environments (office, kitchen, street) | Creating noise augmentation profiles for testing |
| **EYASE** | Egyptian Arabic speech, expressive | Tuning speech activity detection filters for Arabic phonetics |
| **Child Speech Dataset (OGI)** | Speech samples from children | Training/calibrating the speaker verification child-gate model |
| **RAVDESS** | Emotional speech & vocalizations | Evaluating baseline acoustic feature extraction changes |

---

## 6. Full Recommended Development Pipeline

```
Caregiver Recording Flow
    ↓
Record 7 Vocalizations → Quality Check (duration, clipping) → Extract MFCC → Store Templates locally
                                                                                
Active Listening Flow
    ↓
Android AudioRecord Mono PCM (16kHz)
    ↓
WebRTC VAD (Speech Segment Detection)
    ↓
Denoise (Spectral Subtraction) & Normalize Volume
    ↓
Speaker Verification Gate (Is this the child?)
    ↓
MFCC Feature Extraction (40-Dimensional)
    ↓
Dynamic Time Warping (DTW) distance calculation across enrolled templates
    ↓
Confidence & Margin Check → Passed → Play pre-recorded Egyptian Arabic mp3
```

---

## 7. Tools & Libraries Reference

| Module | library / Tool |
|---|---|
| **Audio Capture** | Android `AudioRecord` API |
| **Voice Activity Detection** | WebRTC VAD AAR wrapper |
| **Signal Processing & Feature Extraction** | Kotlin native port of MFCC (TarsosDSP or custom FFT) |
| **Distance Scoring** | Dynamic Time Warping (DTW) optimized C++/Kotlin library |
| **Local Database** | Room SQLite (Profiles, enrolled template metadata, histories) |
| **Offline Audio Synthesis** | Local Android `MediaPlayer` playing premium `.mp3` assets |

---

## 8. Evaluation Metrics That Matter

Voxa is evaluated on-device using a simulated local test dataset:

| Metric | Goal | Why It Matters |
|---|---|---|
| **True Accept Rate (TAR)** | $\ge 80\%$ | Ensure the child is understood when they make an enrolled sound. |
| **False Accept Rate (FAR)** | $< 3\%$ | Prevent wrong translations that confuse caregivers and frustrate the child. |
| **Unknown Rejection Rate** | $\ge 90\%$ | Ensure that random babbles, coughs, and ambient speech are ignored. |
| **Inference Latency** | $< 300\text{ms}$ | The delay between the sound ending and translation playback must feel real-time. |
| **Battery Consumption** | $< 6\%\text{ / hour}$ | The listening service must run continuously during sessions without overheating the device. |

---

## 9. Hackathon Priority Order

| Priority | Task | Effort | Impact |
|---|---|---|---|
| **1** | Core DTW & MFCC Pipeline (Kotlin/Java) | High | Essential for the app's basic logic |
| **2** | Enrollment UX Flow (with quality feedback) | Medium | Crucial for correct template collection |
| **3** | Foreground Service Continuous Listening | Medium | Prevents Android OS from killing the app |
| **4** | Margin Validation & Threshold Tuning | Low | Reduces false translations drastically |
| **5** | Egyptian Arabic audio clip playback asset integration | Low | Provides immediate high-quality demo impact |
| **6** | WebRTC VAD Integration | Medium | Saves CPU cycles and prevents processing noise |

---

## 10. Biggest Risk to Manage

The primary risk is **vocal inconsistency** and **false translations** during a live demo. If a judge makes a random vocal sound and the app triggers a translation, the project loses credibility.

**Demo Mitigation Strategy:**
* Ensure the margin threshold is strict during the presentation.
* Include a distinct "Unknown" visual display on the UI showing that the app successfully detected a sound but rejected it as a non-match.
* Demonstrate the enrollment process live with a team member's voice to prove it works immediately.

---
*Document compiled from full project analysis — CU AI Nexus Hackathon 2026*
