# ECAPA-TDNN Speaker Verification — Android Integration Guide

> **Author:** Dev A
> **Date:** June 28, 2026
> **Purpose:** Integration guide for the ECAPA-TDNN TFLite model into the Voxa Android app.
> Dev B should use this guide to wire up speaker verification in `VoxaListenerService.kt`.

---

## 1. Model Overview

ECAPA-TDNN is a neural network that produces a **192-dimensional "voice fingerprint"** from any audio segment. Two fingerprints from the **same person** will have high cosine similarity (> 0.6), while fingerprints from **different people** will have low similarity (< 0.3). Voxa uses this to verify that the detected speech belongs to the enrolled child, not a sibling, parent, or TV.

**Where it fits in the pipeline:**

```
Microphone → VAD (is there speech?) → ECAPA-TDNN (is it the child?) → MFCC+DTW (what word?)
```

| Property | Value |
|---|---|
| Model file | `ecapa_speaker_id.tflite` |
| Placement | `app/src/main/assets/ecapa_speaker_id.tflite` |
| Source | `speechbrain/spkrec-ecapa-voxceleb` |
| Training data | VoxCeleb1 + VoxCeleb2 (~7000 adult speakers) |

---

## 2. Input/Output Shapes (CONFIRMED)

| Property | Value |
|---|---|
| **Input shape** | `[1, T, 80]` |
| Input meaning | batch=1, T=variable time frames, 80=mel bands |
| Input dtype | `float32` |
| **Output shape** | `[1, 192]` |
| Output meaning | 192-dimensional speaker embedding vector |
| Output normalization | L2 normalized (unit vector) |
| Output dtype | `float32` |

> [!IMPORTANT]
> `T` is variable — it depends on the duration of the speech segment. For a 2-second utterance at 16kHz with 10ms hop: T = 200 frames.

---

## 3. Log-Mel Spectrogram Parameters

These parameters MUST match exactly between the Kaggle training notebook and Android.

| Parameter | ECAPA-TDNN Value | MFCC Pipeline Value | ⚠️ Same? |
|---|---|---|---|
| Sample rate | 16000 Hz | 16000 Hz | ✅ Same |
| FFT size (n_fft) | **400** | 512 | ❌ Different |
| Hop length | 160 (10ms) | 160 (10ms) | ✅ Same |
| Mel bands | **80** | 40 | ❌ Different |
| Freq min (f_min) | **0 Hz** | 100 Hz | ❌ Different |
| Freq max (f_max) | 8000 Hz | 8000 Hz | ✅ Same |
| Window function | **Hann** | Hamming | ❌ Different |
| Pre-emphasis | 0.97 | 0.97 | ✅ Same |
| Normalization | Per-utterance mean | Per-utterance mean+var | ❌ Different |

> [!CAUTION]
> **ECAPA-TDNN and MFCC use DIFFERENT feature extraction paths!** You need TWO separate functions:
> 1. `computeLogMelSpectrogram()` → 80 mel bands, Hann window, n_fft=400 → feeds ECAPA
> 2. `extractMfcc()` → 40 MFCC features, Hamming window, n_fft=512 → feeds DTW
>
> Do NOT reuse the MFCC features for ECAPA or vice versa.

---

## 4. Kotlin Implementation Guide

### 4.1 Log-Mel Spectrogram Computation

```kotlin
/**
 * Computes 80-band Log-Mel spectrogram for ECAPA-TDNN.
 * NOTE: This is DIFFERENT from the MFCC extraction function!
 *
 * @param pcmSamples Raw 16-bit PCM audio as FloatArray (normalized to [-1, 1])
 * @return Array<FloatArray> with shape [T, 80] — T time frames, 80 mel bands
 */
fun computeLogMelSpectrogram(pcmSamples: FloatArray): Array<FloatArray> {
    val sampleRate = 16000
    val nFft = 400          // 25ms window (NOT 512 like MFCC!)
    val hopLength = 160     // 10ms hop
    val nMels = 80          // 80 bands (NOT 40 like MFCC!)
    val fMin = 0.0          // 0 Hz (NOT 100 Hz like MFCC!)
    val fMax = 8000.0

    // 1. Pre-emphasis (same as MFCC)
    val emphasized = FloatArray(pcmSamples.size)
    emphasized[0] = pcmSamples[0]
    for (i in 1 until pcmSamples.size) {
        emphasized[i] = pcmSamples[i] - 0.97f * pcmSamples[i - 1]
    }

    // 2. Frame the signal
    val numFrames = (emphasized.size - nFft) / hopLength + 1
    if (numFrames <= 0) return emptyArray()

    // 3. Apply Hann window (NOT Hamming!)
    val hannWindow = FloatArray(nFft) { i ->
        (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (nFft - 1)))).toFloat()
    }

    // 4. Build mel filterbank [nMels x (nFft/2 + 1)]
    val melFilterbank = buildMelFilterbank(nMels, nFft, sampleRate, fMin, fMax)

    // 5. For each frame: FFT → power spectrum → mel filterbank → log
    val melSpectrogram = Array(numFrames) { FloatArray(nMels) }
    for (t in 0 until numFrames) {
        val start = t * hopLength
        val frame = FloatArray(nFft) { i -> emphasized[start + i] * hannWindow[i] }

        // FFT → power spectrum
        val powerSpec = computePowerSpectrum(frame, nFft) // Returns [nFft/2 + 1]

        // Apply mel filterbank
        for (m in 0 until nMels) {
            var energy = 0.0f
            for (k in powerSpec.indices) {
                energy += melFilterbank[m][k] * powerSpec[k]
            }
            // Log compression (with floor to avoid log(0))
            melSpectrogram[t][m] = Math.log(Math.max(energy.toDouble(), 1e-10)).toFloat()
        }
    }

    // 6. Per-utterance mean normalization (mean only, NOT variance)
    val means = FloatArray(nMels)
    for (m in 0 until nMels) {
        var sum = 0.0f
        for (t in 0 until numFrames) sum += melSpectrogram[t][m]
        means[m] = sum / numFrames
    }
    for (t in 0 until numFrames) {
        for (m in 0 until nMels) {
            melSpectrogram[t][m] -= means[m]
        }
    }

    return melSpectrogram
}

/**
 * Build triangular mel filterbank matrix [nMels x nBins].
 * Uses the same mel scale as SpeechBrain (HTK formula).
 */
private fun buildMelFilterbank(
    nMels: Int, nFft: Int, sampleRate: Int,
    fMin: Double, fMax: Double
): Array<FloatArray> {
    val nBins = nFft / 2 + 1  // = 201

    fun hzToMel(hz: Double): Double = 2595.0 * Math.log10(1.0 + hz / 700.0)
    fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    val melMin = hzToMel(fMin)
    val melMax = hzToMel(fMax)

    // nMels + 2 equally spaced points in mel scale
    val melPoints = DoubleArray(nMels + 2) { i ->
        melMin + i * (melMax - melMin) / (nMels + 1)
    }
    val hzPoints = melPoints.map { melToHz(it) }
    val binPoints = hzPoints.map { (it * nFft / sampleRate).toInt() }

    val filterbank = Array(nMels) { FloatArray(nBins) }
    for (m in 0 until nMels) {
        val left = binPoints[m]
        val center = binPoints[m + 1]
        val right = binPoints[m + 2]

        for (k in left until center) {
            if (center > left) {
                filterbank[m][k] = (k - left).toFloat() / (center - left)
            }
        }
        for (k in center until right) {
            if (right > center) {
                filterbank[m][k] = (right - k).toFloat() / (right - center)
            }
        }
    }
    return filterbank
}
```

### 4.2 Speaker Embedding Extraction

The existing `SpeakerVerifier.kt` already handles this. Feed it the mel spectrogram:

```kotlin
// In SpeakerVerifier.kt — the extractEmbedding function is already correct:
fun extractEmbedding(audioFeatures: Array<FloatArray>): FloatArray {
    val input = arrayOf(audioFeatures)       // [1, T, 80]
    val output = Array(1) { FloatArray(192) } // [1, 192]
    interpreter?.run(input, output)
    return output[0]
}
```

### 4.3 Speaker Verification Decision

```kotlin
/**
 * Verifies if the test audio matches the enrolled speaker.
 *
 * @param testEmbedding 192-dim embedding from current audio
 * @param enrolledEmbedding 192-dim averaged embedding from enrollment
 * @param threshold Cosine similarity threshold (default 0.25)
 * @return true if same speaker
 */
fun verifySpeaker(
    testEmbedding: FloatArray,
    enrolledEmbedding: FloatArray,
    threshold: Float = 0.25f
): Boolean {
    val similarity = SpeakerVerifier.computeCosineSimilarity(testEmbedding, enrolledEmbedding)
    return similarity >= threshold
}
```

---

## 5. Enrollment Flow

During enrollment, after the caregiver records the child saying a word 5 times:

```
┌─────────────────────────────────────────────────────┐
│ ENROLLMENT (happens once per child)                  │
│                                                      │
│ 1. Record 5 audio templates for word enrollment      │
│ 2. For EACH template:                                │
│    a. Compute Log-Mel (80 bands) → [T_i, 80]       │
│    b. Run ECAPA-TDNN → embedding_i [192]            │
│ 3. Average all 5 embeddings:                         │
│    enrolled_embedding = mean(emb_1..emb_5)           │
│ 4. L2 normalize the average                          │
│ 5. Store enrolled_embedding in Room database         │
│    (as JSON-serialized FloatArray, 192 floats)       │
└─────────────────────────────────────────────────────┘
```

```kotlin
fun enrollSpeaker(audioTemplates: List<FloatArray>): FloatArray {
    val embeddings = audioTemplates.map { pcm ->
        val mel = computeLogMelSpectrogram(pcm)
        speakerVerifier.extractEmbedding(mel)
    }

    // Average all embeddings
    val averaged = FloatArray(192)
    for (emb in embeddings) {
        for (i in averaged.indices) averaged[i] += emb[i]
    }
    for (i in averaged.indices) averaged[i] /= embeddings.size

    // L2 normalize
    var norm = 0.0f
    for (v in averaged) norm += v * v
    norm = Math.sqrt(norm.toDouble()).toFloat()
    if (norm > 0) for (i in averaged.indices) averaged[i] /= norm

    return averaged  // Store this in Room DB
}
```

---

## 6. Verification Flow (Runtime)

```
┌─────────────────────────────────────────────────────┐
│ RUNTIME VERIFICATION (happens on every speech event) │
│                                                      │
│ 1. VAD detects speech segment                        │
│ 2. Compute Log-Mel (80 bands) from segment           │
│ 3. Run ECAPA-TDNN → test_embedding [192]            │
│ 4. Load enrolled_embedding from Room DB              │
│ 5. Cosine similarity = dot(test, enrolled)           │
│ 6. If similarity ≥ 0.25 → PASS → continue to DTW   │
│    If similarity < 0.25 → REJECT → ignore           │
└─────────────────────────────────────────────────────┘
```

Expected similarity scores:

| Scenario | Expected Similarity | Decision |
|---|---|---|
| Same child, same word | 0.7 — 0.95 | ✅ PASS |
| Same child, different word | 0.5 — 0.8 | ✅ PASS |
| Different person (parent) | -0.1 — 0.2 | ❌ REJECT |
| TV / background noise | -0.2 — 0.1 | ❌ REJECT |

---

## 7. Integration into VoxaListenerService

Currently, `VoxaListenerService.kt` has this integration spot at **line 139-142**:

```kotlin
// CURRENT (just logs):
if (readResult > 0) {
    Log.d("VoxaService", "Captured buffer frame: read $readResult samples")
}
```

**Replace with the full pipeline:**

```kotlin
// FULL PIPELINE:
if (readResult > 0) {
    // Convert ShortArray to FloatArray (normalize to [-1, 1])
    val floatSamples = FloatArray(readResult) { i ->
        audioData[i].toFloat() / 32768.0f
    }

    // 1. VAD: Is there speech?
    val isSpeech = vad.process(floatSamples)
    if (!isSpeech) return@Thread  // Skip non-speech frames

    // 2. Speaker Verification: Is it the enrolled child?
    val melFeatures = computeLogMelSpectrogram(floatSamples)
    if (melFeatures.isEmpty()) return@Thread

    val testEmbedding = speakerVerifier.extractEmbedding(melFeatures)
    val enrolledEmbedding = loadEnrolledEmbedding()  // From Room DB
    val similarity = SpeakerVerifier.computeCosineSimilarity(
        testEmbedding, enrolledEmbedding
    )

    if (similarity < 0.25f) {
        Log.d("VoxaService", "Speaker rejected (similarity=$similarity)")
        return@Thread  // Not the enrolled child
    }
    Log.d("VoxaService", "Speaker verified (similarity=$similarity)")

    // 3. Word Recognition: What did they say?
    val mfccFeatures = extractMfcc(floatSamples)  // 40-dim MFCC
    val matchResult = marginGate.evaluate(
        candidates = computeDtwDistances(mfccFeatures, templates),
        absoluteThreshold = 6.50f,
        marginThreshold = 0.32f
    )

    if (matchResult.isMatch) {
        playTranslation(matchResult.matchedWord!!)
    }
}
```

---

## 8. Performance Notes

| Metric | Value |
|---|---|
| Expected TFLite latency (flagship) | ~30-50 ms |
| Expected TFLite latency (mid-range) | ~80-150 ms |
| Model load time (first call) | ~200-500 ms |
| Memory footprint | Stays memory-mapped, minimal heap |
| Thread safety | Run on same background thread as audio capture |

**Optimization tips:**
- Load `SpeakerVerifier` once in `onCreate()`, reuse for all inferences
- The TFLite interpreter supports `resize_tensor_input` for variable-length audio
- Allocate input/output arrays once and reuse (avoid GC pressure)
- Consider NNAPI delegate for hardware acceleration on supported devices

---

## 9. Validation Checklist for Dev B

- [ ] Place `ecapa_speaker_id.tflite` in `app/src/main/assets/`
- [ ] Implement `computeLogMelSpectrogram()` with **80 mel bands, Hann window, n_fft=400**
- [ ] Verify output shape from TFLite is `[1, 192]`
- [ ] Test cosine similarity: same-speaker recordings > 0.5, different-speaker < 0.3
- [ ] Add `enrolledEmbedding: String` column to `ChildProfile` entity (JSON FloatArray)
- [ ] Implement `enrollSpeaker()` that averages 5 embeddings and stores in DB
- [ ] Integrate speaker check before DTW word matching in `VoxaListenerService`
- [ ] Add speaker verification to `AiBridgeTest.kt` unit tests
- [ ] Test with at least 2 different speakers to verify rejection works

---

## Appendix A: Database Schema Update

The `ChildProfile` entity needs a new column:

```kotlin
@Entity(tableName = "child_profiles")
data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String,
    val isActive: Boolean = false,
    val avatarEmoji: String = "👦",
    val speakerEmbedding: String? = null  // NEW: JSON-serialized FloatArray(192)
)
```

> [!WARNING]
> Adding this column requires incrementing `VoxaDatabase` version from 2 to 3.
> Since `fallbackToDestructiveMigration` is enabled, existing data will be wiped.
> This is acceptable during development but must be handled with a proper migration for production.
