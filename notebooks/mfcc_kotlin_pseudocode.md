# Voxa MFCC Feature Extractor - Kotlin Port Pseudocode for Dev B

> **Author:** Dev A
> **Date:** June 28, 2026
> **Purpose:** Detailed implementation guide for porting `mfcc_extractor.py` to Kotlin/Android.
> Dev B should use this guide to produce byte-identical 40-dimensional feature vectors on-device.

---

## 1. Configuration Data Class

All parameters must match the Python `MFCC_CONFIG` dictionary exactly. A single mismatch (e.g., using `n_fft = 256` instead of `512`) will produce feature vectors that are incomparable to enrolled templates.

```kotlin
data class MfccConfig(
    val sampleRate: Int       = 16000,
    val frameSizeMs: Int      = 25,       // → 400 samples
    val hopSizeMs: Int        = 10,       // → 160 samples
    val nFft: Int             = 512,
    val nMels: Int            = 40,
    val nMfcc: Int            = 13,
    val fMin: Double          = 100.0,    // Hz lower edge of mel filterbank
    val fMax: Double          = 8000.0,   // Hz upper edge of mel filterbank
    val window: String        = "hamming",
    val preEmphasis: Double   = 0.97,
    val cmvn: String          = "per_utterance", // "per_utterance" | "cmn_only" | "none"
    val deltaWindowK: Int     = 2,
    val agc: Boolean          = true,
    val agcTargetRms: Double  = 0.05
) {
    /** Frame length in samples: 16000 * 25 / 1000 = 400 */
    val frameLen: Int get() = sampleRate * frameSizeMs / 1000

    /** Hop length in samples: 16000 * 10 / 1000 = 160 */
    val hopLen: Int get() = sampleRate * hopSizeMs / 1000

    /** Number of frequency bins in the one-sided FFT spectrum: 512 / 2 + 1 = 257 */
    val nBins: Int get() = nFft / 2 + 1

    /** Total feature dimension: 13 MFCC + 13 Δ + 13 ΔΔ + 1 Energy */
    val featureDim: Int get() = nMfcc * 3 + 1   // = 40
}
```

> [!IMPORTANT]
> The `featureDim` is **40**. Every frame that comes out of `extractMfcc()` must be a `FloatArray(40)` that directly feeds into the DTW matcher from `dtw_kotlin_pseudocode.md`.

---

## 2. Adaptive Gain Control (AGC)

AGC normalizes signal loudness so the pipeline produces consistent features regardless of recording volume. This is a simple RMS normalization, not a complex compressor.

```kotlin
import kotlin.math.sqrt
import kotlin.math.min

/**
 * Normalize signal RMS to [targetRms].
 * Clamps the result to [-1.0, 1.0] to prevent clipping.
 *
 * Python equivalent: apply_agc()
 */
fun applyAgc(
    signal: DoubleArray,
    targetRms: Double = 0.05,
    maxGain: Double = 10.0
): DoubleArray {
    // Compute RMS with epsilon to prevent division by zero
    var sumSq = 0.0
    for (s in signal) sumSq += s * s
    val rms = sqrt(sumSq / signal.size + 1e-10)

    // If the signal is essentially silence, return unchanged
    if (rms < 1e-5) return signal.copyOf()

    var scale = targetRms / rms
    scale = min(scale, maxGain)

    val out = DoubleArray(signal.size)
    for (i in signal.indices) {
        val v = signal[i] * scale
        // Clip to [-1.0, 1.0]
        out[i] = when {
            v >  1.0 ->  1.0
            v < -1.0 -> -1.0
            else     ->  v
        }
    }
    return out
}
```

---

## 3. Pre-Emphasis Filter

A first-order high-pass filter that boosts high frequencies, compensating for the spectral roll-off of voiced speech. This must be applied **before** framing.

$$y[n] = x[n] - \alpha \cdot x[n-1], \quad \alpha = 0.97$$

```kotlin
/**
 * Apply pre-emphasis filter in-place-style, returning a new array.
 *
 * Python equivalent: pre_emphasis()
 *
 * Output length is the same as input length.
 * y[0] = x[0] (the very first sample has no predecessor).
 */
fun preEmphasis(signal: DoubleArray, coeff: Double = 0.97): DoubleArray {
    val out = DoubleArray(signal.size)
    out[0] = signal[0]
    for (i in 1 until signal.size) {
        out[i] = signal[i] - coeff * signal[i - 1]
    }
    return out
}
```

---

## 4. Hamming Window Generation

The Hamming window reduces spectral leakage at frame boundaries. Generate it **once** in an init block and reuse across all frames.

$$w[n] = 0.54 - 0.46 \cdot \cos\!\left(\frac{2\pi n}{N - 1}\right), \quad n = 0, 1, \ldots, N-1$$

```kotlin
import kotlin.math.cos
import kotlin.math.PI

/**
 * Generate a Hamming window of length [size].
 *
 * Matches numpy.hamming(400) exactly.
 * Pre-compute this once and store as a class member.
 */
fun hammingWindow(size: Int): DoubleArray {
    val window = DoubleArray(size)
    for (n in 0 until size) {
        window[n] = 0.54 - 0.46 * cos(2.0 * PI * n / (size - 1))
    }
    return window
}
```

> [!TIP]
> Store the window as a `val hammingWin = hammingWindow(config.frameLen)` in your extractor class constructor. Never regenerate it per-frame.

---

## 5. Frame Slicing

Extract overlapping frames from the pre-emphasized signal. With `frameLen = 400` and `hopLen = 160`, each frame overlaps the previous one by 240 samples (60%).

```kotlin
/**
 * Slice [signal] into overlapping frames.
 *
 * @return Array of DoubleArray, each of length [frameLen].
 *         Number of frames = 1 + (signal.size - frameLen) / hopLen
 */
fun sliceFrames(
    signal: DoubleArray,
    frameLen: Int,
    hopLen: Int
): Array<DoubleArray> {
    val nFrames = 1 + (signal.size - frameLen) / hopLen
    if (nFrames <= 0) return emptyArray()

    return Array(nFrames) { i ->
        val start = i * hopLen
        signal.copyOfRange(start, start + frameLen)
    }
}
```

> [!NOTE]
> For a 1-second recording (16000 samples), this produces **98 frames**: `1 + (16000 - 400) / 160 = 98`.

---

## 6. FFT (Real-Valued)

Android/Kotlin does not have `numpy.fft.rfft`. You have two options:

| Option | Library | Pros | Cons |
|--------|---------|------|------|
| **A** (Recommended) | `org.jtransforms:jtransforms:3.1` | Battle-tested, fast, handles real FFT natively | External dependency (~200 KB) |
| **B** | Custom radix-2 Cooley-Tukey | Zero dependencies | More code to maintain, must handle N=512 |

### Option A: JTransforms

Add to `build.gradle.kts`:
```kotlin
implementation("org.jtransforms:jtransforms:3.1")
```

```kotlin
import org.jtransforms.fft.DoubleFFT_1D

/**
 * Compute the one-sided real FFT of a frame, returning magnitude values.
 *
 * JTransforms packs the real FFT output as:
 *   [Re(0), Re(N/2), Re(1), Im(1), Re(2), Im(2), ... Re(N/2-1), Im(N/2-1)]
 *
 * We extract magnitudes for bins 0..N/2 (inclusive), giving nBins = N/2 + 1 = 257.
 *
 * @param frame   windowed audio frame (length frameLen, e.g. 400)
 * @param nFft    FFT size (512). Frame will be zero-padded to this length.
 * @return DoubleArray of length nFft/2 + 1 containing |FFT[k]|
 */
fun realFftMagnitude(frame: DoubleArray, nFft: Int): DoubleArray {
    // Zero-pad to nFft
    val padded = DoubleArray(nFft)
    frame.copyInto(padded, 0, 0, frame.size)

    // In-place FFT (JTransforms modifies the array)
    val fft = DoubleFFT_1D(nFft.toLong())
    fft.realForward(padded)

    // Unpack magnitudes
    val nBins = nFft / 2 + 1
    val mag = DoubleArray(nBins)

    // Bin 0: DC component (purely real)
    mag[0] = kotlin.math.abs(padded[0])

    // Bin N/2: Nyquist (purely real, stored at padded[1] by JTransforms)
    mag[nFft / 2] = kotlin.math.abs(padded[1])

    // Bins 1..(N/2 - 1): complex pairs
    for (k in 1 until nFft / 2) {
        val re = padded[2 * k]
        val im = padded[2 * k + 1]
        mag[k] = sqrt(re * re + im * im)
    }

    return mag
}
```

### Option B: Custom Radix-2 Cooley-Tukey (Zero Dependencies)

If you cannot use external libraries, implement the FFT from scratch. This is a standard in-place radix-2 decimation-in-time algorithm.

```kotlin
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place radix-2 Cooley-Tukey FFT.
 *
 * @param re  real parts, length N (must be power of 2)
 * @param im  imaginary parts, length N (must be power of 2)
 *
 * After return, re[k] and im[k] hold the complex FFT output X[k].
 */
fun fftInPlace(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    require(n > 0 && (n and (n - 1)) == 0) { "N must be a power of 2" }

    // Bit-reversal permutation
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            // Swap re[i] <-> re[j], im[i] <-> im[j]
            var tmp = re[i]; re[i] = re[j]; re[j] = tmp
            tmp = im[i]; im[i] = im[j]; im[j] = tmp
        }
    }

    // Butterfly stages
    var len = 2
    while (len <= n) {
        val halfLen = len / 2
        val angle = -2.0 * PI / len
        val wRe = cos(angle)
        val wIm = sin(angle)

        var i = 0
        while (i < n) {
            var curRe = 1.0
            var curIm = 0.0
            for (k in 0 until halfLen) {
                val evenIdx = i + k
                val oddIdx  = i + k + halfLen

                // Twiddle multiply: t = W * X[oddIdx]
                val tRe = curRe * re[oddIdx] - curIm * im[oddIdx]
                val tIm = curRe * im[oddIdx] + curIm * re[oddIdx]

                // Butterfly
                re[oddIdx] = re[evenIdx] - tRe
                im[oddIdx] = im[evenIdx] - tIm
                re[evenIdx] = re[evenIdx] + tRe
                im[evenIdx] = im[evenIdx] + tIm

                // Advance twiddle factor
                val newRe = curRe * wRe - curIm * wIm
                val newIm = curRe * wIm + curIm * wRe
                curRe = newRe
                curIm = newIm
            }
            i += len
        }
        len = len shl 1
    }
}

/**
 * Compute one-sided real FFT magnitude using the custom Cooley-Tukey FFT.
 *
 * @param frame  windowed frame of length frameLen
 * @param nFft   FFT size (must be power of 2, e.g. 512)
 * @return DoubleArray of length nFft/2 + 1 = 257 with |X[k]|
 */
fun realFftMagnitudeCustom(frame: DoubleArray, nFft: Int): DoubleArray {
    val re = DoubleArray(nFft)
    val im = DoubleArray(nFft) // all zeros (real input)
    frame.copyInto(re, 0, 0, frame.size) // zero-pad remainder

    fftInPlace(re, im)

    val nBins = nFft / 2 + 1
    val mag = DoubleArray(nBins)
    for (k in 0 until nBins) {
        mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
    }
    return mag
}
```

> [!IMPORTANT]
> Whichever FFT option you choose, the output must be **257 magnitude values** (bins 0 through 256) for `nFft = 512`. Both the JTransforms and custom implementations above produce this.

---

## 7. Power Spectrum

Convert FFT magnitudes to a power spectrum. This normalizes by the FFT size so that values are independent of zero-padding.

$$P[k] = \frac{|X[k]|^2}{N_{\text{FFT}}}$$

```kotlin
/**
 * Compute power spectrum from FFT magnitudes.
 *
 * @param mag  magnitude array of length nBins (257)
 * @param nFft FFT size (512)
 * @return DoubleArray of length nBins with power values
 */
fun powerSpectrum(mag: DoubleArray, nFft: Int): DoubleArray {
    val power = DoubleArray(mag.size)
    for (k in mag.indices) {
        power[k] = (mag[k] * mag[k]) / nFft.toDouble()
    }
    return power
}
```

---

## 8. Mel Filterbank

The mel filterbank maps the linear-frequency power spectrum onto the perceptually-motivated mel scale using 40 overlapping triangular filters spanning 100 Hz to 8000 Hz.

### 8.1 Scale Conversion Helpers

```kotlin
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.floor

/**
 * Convert frequency in Hz to the Mel scale.
 * Formula: mel = 2595 * log10(1 + hz / 700)
 */
fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)

/**
 * Convert Mel scale value back to Hz.
 * Formula: hz = 700 * (10^(mel / 2595) - 1)
 */
fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
```

### 8.2 Filterbank Matrix Construction

The filterbank is a `[nMels × nBins]` matrix (40 × 257) where each row is a triangular filter. Build it **once** and reuse.

```kotlin
/**
 * Create the triangular Mel filterbank matrix.
 *
 * @param nFilters   number of mel filters (40)
 * @param nFft       FFT size (512)
 * @param sampleRate sample rate (16000)
 * @param fMin       lower frequency edge (100 Hz)
 * @param fMax       upper frequency edge (8000 Hz)
 * @return 2D array [nFilters][nBins] where nBins = nFft / 2 + 1
 *
 * Python equivalent: create_mel_filterbank()
 */
fun createMelFilterbank(
    nFilters: Int,
    nFft: Int,
    sampleRate: Int,
    fMin: Double,
    fMax: Double
): Array<DoubleArray> {
    val nBins = nFft / 2 + 1  // 257

    // 1. Compute nFilters + 2 equally spaced points on the mel scale
    val melMin = hzToMel(fMin)
    val melMax = hzToMel(fMax)
    val melPoints = DoubleArray(nFilters + 2) { i ->
        melMin + i.toDouble() * (melMax - melMin) / (nFilters + 1)
    }

    // 2. Convert mel points back to Hz
    val hzPoints = DoubleArray(melPoints.size) { melToHz(melPoints[it]) }

    // 3. Map Hz frequencies to FFT bin indices
    val bins = IntArray(hzPoints.size) { i ->
        floor((nFft + 1).toDouble() * hzPoints[i] / sampleRate).toInt()
    }

    // 4. Build triangular filters
    val filterbank = Array(nFilters) { DoubleArray(nBins) }

    for (i in 0 until nFilters) {
        val left   = bins[i]
        val center = bins[i + 1]
        val right  = bins[i + 2]

        // Rising slope: left → center
        for (j in left until center) {
            if (j in 0 until nBins) {
                filterbank[i][j] = (j - left).toDouble() / (center - left).toDouble()
            }
        }

        // Falling slope: center → right
        for (j in center until right) {
            if (j in 0 until nBins) {
                filterbank[i][j] = (right - j).toDouble() / (right - center).toDouble()
            }
        }
    }

    return filterbank
}
```

### 8.3 Applying the Filterbank

Matrix-vector multiply: for each frame, dot-product the 257-element power spectrum with each of the 40 filter rows.

```kotlin
/**
 * Apply mel filterbank to a single power spectrum frame.
 *
 * @param power      power spectrum, length nBins (257)
 * @param filterbank mel filterbank matrix [nMels][nBins]
 * @return DoubleArray of length nMels (40) with mel energies
 */
fun applyMelFilterbank(
    power: DoubleArray,
    filterbank: Array<DoubleArray>
): DoubleArray {
    val nMels = filterbank.size
    val melEnergies = DoubleArray(nMels)

    for (m in 0 until nMels) {
        var sum = 0.0
        val filter = filterbank[m]
        for (k in filter.indices) {
            sum += power[k] * filter[k]
        }
        // Floor to prevent log(0)
        melEnergies[m] = maxOf(sum, 1e-10)
    }

    return melEnergies
}
```

---

## 9. Log Mel + DCT-II → MFCCs

After computing log mel energies, a Type-II DCT (with orthonormal normalization) extracts the 13 MFCCs. This is the cepstral transform — it decorrelates the mel features.

### 9.1 Explicit DCT-II Implementation

Android has no `scipy.fftpack.dct`. Implement it directly from the formula:

$$C[k] = \sqrt{\frac{2}{N}} \sum_{n=0}^{N-1} x[n] \cos\!\left[\frac{\pi}{N}\left(n + 0.5\right) k \right]$$

With the ortho normalization, the $k = 0$ coefficient is scaled by $\frac{1}{\sqrt{2}}$:

$$C[0] = \sqrt{\frac{1}{N}} \sum_{n=0}^{N-1} x[n]$$

```kotlin
/**
 * Compute the Type-II DCT with orthonormal normalization.
 *
 * Matches scipy.fftpack.dct(x, type=2, norm='ortho') exactly.
 *
 * @param x       input vector of length N (log mel energies, N = 40)
 * @param nCoeffs number of DCT coefficients to return (13)
 * @return DoubleArray of length nCoeffs
 */
fun dctII(x: DoubleArray, nCoeffs: Int): DoubleArray {
    val n = x.size  // 40 (number of mel filters)
    val coeffs = DoubleArray(nCoeffs)
    val scaleFactor = sqrt(2.0 / n)

    for (k in 0 until nCoeffs) {
        var sum = 0.0
        for (i in 0 until n) {
            sum += x[i] * cos(PI * k * (i + 0.5) / n)
        }
        coeffs[k] = sum * scaleFactor
    }

    // Ortho normalization: scale the k=0 coefficient by 1/sqrt(2)
    coeffs[0] *= 1.0 / sqrt(2.0)

    return coeffs
}
```

### 9.2 Computing MFCCs for One Frame

```kotlin
import kotlin.math.ln

/**
 * Compute 13 MFCCs from a mel energy vector.
 *
 * @param melEnergies  mel energies of length nMels (40), already floored to 1e-10
 * @param nMfcc        number of cepstral coefficients to keep (13)
 * @return DoubleArray of length nMfcc
 */
fun computeMfcc(melEnergies: DoubleArray, nMfcc: Int): DoubleArray {
    // 1. Apply natural log
    val logMel = DoubleArray(melEnergies.size) { ln(melEnergies[it]) }

    // 2. Apply DCT-II to get cepstral coefficients
    return dctII(logMel, nMfcc)
}
```

> [!WARNING]
> The Python code uses `np.log` which is the **natural logarithm** (base $e$). Use Kotlin's `kotlin.math.ln()`, NOT `kotlin.math.log10()`. Using the wrong log base will silently produce wrong MFCCs.

---

## 10. Delta Computation

Deltas capture the rate of change of features across time. We use the standard regression formula with window $K = 2$:

$$\Delta[t] = \frac{\sum_{n=1}^{K} n \cdot (c[t+n] - c[t-n])}{2 \sum_{n=1}^{K} n^2}$$

For $K = 2$: denominator $= 2 \cdot (1^2 + 2^2) = 10$.

```kotlin
/**
 * Compute delta (derivative) features using regression formula.
 *
 * @param features  2D array [T][D] — T frames, D dimensions
 * @param K         delta window size (default 2)
 * @return 2D array [T][D] of delta features
 *
 * Python equivalent: compute_deltas()
 */
fun computeDeltas(features: Array<DoubleArray>, K: Int = 2): Array<DoubleArray> {
    val T = features.size
    val D = features[0].size
    val deltas = Array(T) { DoubleArray(D) }

    // Denominator: 2 * sum(n^2 for n in 1..K)
    var denom = 0.0
    for (n in 1..K) denom += (n * n).toDouble()
    denom *= 2.0   // For K=2: denom = 10.0

    for (t in 0 until T) {
        for (n in 1..K) {
            val tPlus  = minOf(t + n, T - 1)
            val tMinus = maxOf(t - n, 0)

            for (d in 0 until D) {
                deltas[t][d] += n * (features[tPlus][d] - features[tMinus][d])
            }
        }
        for (d in 0 until D) {
            deltas[t][d] /= denom
        }
    }

    return deltas
}
```

---

## 11. Feature Concatenation

Each frame's final 40-dimensional feature vector is assembled as:

| Indices | Content | Dimension |
|---------|---------|-----------|
| 0–12    | MFCC coefficients | 13 |
| 13–25   | Δ MFCC | 13 |
| 26–38   | ΔΔ MFCC | 13 |
| 39      | Log RMS frame energy | 1 |
| **Total** | | **40** |

```kotlin
/**
 * Concatenate MFCC, delta, delta-delta, and energy into a 40-dim vector.
 *
 * @param mfcc       [T][13] MFCC coefficients
 * @param delta      [T][13] delta MFCCs
 * @param deltaDelta [T][13] delta-delta MFCCs
 * @param energy     [T] log RMS energy per frame
 * @return Array<FloatArray> of shape [T][40], ready for DTW
 */
fun concatenateFeatures(
    mfcc: Array<DoubleArray>,
    delta: Array<DoubleArray>,
    deltaDelta: Array<DoubleArray>,
    energy: DoubleArray
): Array<DoubleArray> {
    val T = mfcc.size
    val nMfcc = mfcc[0].size  // 13

    return Array(T) { t ->
        val vec = DoubleArray(nMfcc * 3 + 1)  // 40

        // Copy 13 MFCCs → indices 0..12
        mfcc[t].copyInto(vec, destinationOffset = 0)

        // Copy 13 Deltas → indices 13..25
        delta[t].copyInto(vec, destinationOffset = nMfcc)

        // Copy 13 Delta-Deltas → indices 26..38
        deltaDelta[t].copyInto(vec, destinationOffset = nMfcc * 2)

        // Copy 1 Energy → index 39
        vec[nMfcc * 3] = energy[t]

        vec
    }
}
```

---

## 12. CMVN Normalization

Per-utterance Cepstral Mean and Variance Normalization. This removes channel and speaker-level bias so that features are zero-mean, unit-variance across each utterance.

```kotlin
/**
 * Apply per-utterance CMVN normalization in-place.
 *
 * For each feature dimension d:
 *   x'[t][d] = (x[t][d] - mean_d) / (std_d + 1e-10)
 *
 * @param features [T][40] feature matrix — MODIFIED IN PLACE
 * @param mode     "per_utterance" (mean + variance) or "cmn_only" (mean only)
 */
fun applyCmvn(features: Array<DoubleArray>, mode: String = "per_utterance") {
    if (features.isEmpty()) return
    val T = features.size
    val D = features[0].size  // 40

    // 1. Compute mean for each dimension
    val mean = DoubleArray(D)
    for (t in 0 until T) {
        for (d in 0 until D) {
            mean[d] += features[t][d]
        }
    }
    for (d in 0 until D) mean[d] /= T.toDouble()

    if (mode == "per_utterance") {
        // 2. Compute standard deviation for each dimension
        val std = DoubleArray(D)
        for (t in 0 until T) {
            for (d in 0 until D) {
                val diff = features[t][d] - mean[d]
                std[d] += diff * diff
            }
        }
        for (d in 0 until D) std[d] = sqrt(std[d] / T.toDouble())

        // 3. Normalize: (x - mean) / (std + eps)
        for (t in 0 until T) {
            for (d in 0 until D) {
                features[t][d] = (features[t][d] - mean[d]) / (std[d] + 1e-10)
            }
        }
    } else if (mode == "cmn_only") {
        // Subtract mean only
        for (t in 0 until T) {
            for (d in 0 until D) {
                features[t][d] -= mean[d]
            }
        }
    }
}
```

> [!NOTE]
> The Python uses `np.std()` which computes the **population** standard deviation (divides by $N$, not $N-1$). The Kotlin code above matches this by dividing by `T`, not `T - 1`.

---

## 13. Full `extractMfcc` Function

This is the main entry point. It mirrors the Python `extract_mfcc()` function step-by-step.

```kotlin
/**
 * Extract 40-dimensional MFCC + Δ + ΔΔ + Energy features from raw 16-bit PCM audio.
 *
 * Pipeline:
 *   1. int16 → float64 normalization
 *   2. AGC (optional)
 *   3. Pre-emphasis
 *   4. Frame slicing
 *   5. Hamming windowing
 *   6. FFT → magnitude
 *   7. Power spectrum
 *   8. Mel filterbank
 *   9. Log mel → DCT-II → 13 MFCCs
 *  10. Frame energy
 *  11. Δ and ΔΔ computation
 *  12. Feature concatenation → 40-dim
 *  13. CMVN normalization
 *
 * @param pcmInt16  raw 16-bit PCM samples at 16 kHz (ShortArray)
 * @param config    extraction parameters (use default MfccConfig)
 * @return Array<FloatArray> of shape [T, 40] ready for DTW matching
 *
 * Python equivalent: extract_mfcc()
 */
fun extractMfcc(pcmInt16: ShortArray, config: MfccConfig = MfccConfig()): Array<FloatArray> {
    // -----------------------------------------------------------
    //  Step 1: Normalize int16 PCM → float64 in range [-1.0, 1.0]
    // -----------------------------------------------------------
    var signal = DoubleArray(pcmInt16.size) { pcmInt16[it].toDouble() / 32768.0 }

    // -----------------------------------------------------------
    //  Step 2: Optional Adaptive Gain Control
    // -----------------------------------------------------------
    if (config.agc) {
        signal = applyAgc(signal, targetRms = config.agcTargetRms)
    }

    // -----------------------------------------------------------
    //  Step 3: Pre-emphasis
    // -----------------------------------------------------------
    signal = preEmphasis(signal, config.preEmphasis)

    // -----------------------------------------------------------
    //  Step 4: Frame slicing
    // -----------------------------------------------------------
    val frames = sliceFrames(signal, config.frameLen, config.hopLen)
    val nFrames = frames.size
    if (nFrames == 0) return emptyArray()

    // -----------------------------------------------------------
    //  Step 5: Hamming windowing (pre-compute window once)
    // -----------------------------------------------------------
    val window = hammingWindow(config.frameLen)
    for (i in 0 until nFrames) {
        for (s in 0 until config.frameLen) {
            frames[i][s] *= window[s]
        }
    }

    // -----------------------------------------------------------
    //  Step 6 + 7: FFT → Magnitude → Power spectrum
    //  Step 8: Mel filterbank application
    //  Step 9: Log + DCT → 13 MFCCs
    //  Step 10: Frame energy
    // -----------------------------------------------------------
    val filterbank = createMelFilterbank(
        config.nMels, config.nFft, config.sampleRate, config.fMin, config.fMax
    )

    val mfccAll  = Array(nFrames) { DoubleArray(config.nMfcc) }
    val energyAll = DoubleArray(nFrames)

    for (i in 0 until nFrames) {
        // 6. FFT magnitude
        val mag = realFftMagnitude(frames[i], config.nFft)

        // 7. Power spectrum
        val power = powerSpectrum(mag, config.nFft)

        // 8. Mel filterbank
        val melEnergies = applyMelFilterbank(power, filterbank)

        // 9. Log + DCT → MFCCs
        mfccAll[i] = computeMfcc(melEnergies, config.nMfcc)

        // 10. Frame energy: log(sum(power) + eps)
        var sumPower = 0.0
        for (k in power.indices) sumPower += power[k]
        energyAll[i] = ln(sumPower + 1e-10)
    }

    // -----------------------------------------------------------
    //  Step 11: Δ and ΔΔ
    // -----------------------------------------------------------
    val delta      = computeDeltas(mfccAll, K = config.deltaWindowK)
    val deltaDelta = computeDeltas(delta,   K = config.deltaWindowK)

    // -----------------------------------------------------------
    //  Step 12: Concatenate → [T][40]
    // -----------------------------------------------------------
    val features = concatenateFeatures(mfccAll, delta, deltaDelta, energyAll)

    // -----------------------------------------------------------
    //  Step 13: CMVN normalization
    // -----------------------------------------------------------
    applyCmvn(features, mode = config.cmvn)

    // -----------------------------------------------------------
    //  Convert DoubleArray → FloatArray for DTW compatibility
    //  (DTW matcher uses FloatArray per the dtw_kotlin_pseudocode)
    // -----------------------------------------------------------
    return Array(features.size) { t ->
        FloatArray(features[t].size) { d -> features[t][d].toFloat() }
    }
}
```

---

## 14. Performance Notes for Android

> [!TIP]
> These tips are critical for real-time performance on mid-range Android devices.

### 14.1 Pre-Allocate Everything

```kotlin
class MfccExtractor(private val config: MfccConfig = MfccConfig()) {

    // Pre-allocated reusable buffers (avoid GC pressure in audio loops)
    private val hammingWin   = hammingWindow(config.frameLen)
    private val melFilterbank = createMelFilterbank(
        config.nMels, config.nFft, config.sampleRate, config.fMin, config.fMax
    )

    // Reusable per-frame scratch buffers
    private val paddedFrame = DoubleArray(config.nFft)
    private val magBuffer   = DoubleArray(config.nBins)
    private val powerBuffer = DoubleArray(config.nBins)
    private val melBuffer   = DoubleArray(config.nMels)
    private val logMelBuffer = DoubleArray(config.nMels)

    fun extract(pcmInt16: ShortArray): Array<FloatArray> {
        // Use the pre-allocated buffers instead of allocating new arrays per frame.
        // ... (same pipeline as extractMfcc, but reuse buffers)
    }
}
```

### 14.2 Memory Budget

| Component | Allocation | Size (bytes) | Frequency |
|-----------|-----------|------|-----------|
| Hamming window | `DoubleArray(400)` | 3.2 KB | Once |
| Mel filterbank | `Array(40) { DoubleArray(257) }` | 82 KB | Once |
| FFT padded frame | `DoubleArray(512)` | 4.1 KB | Reuse |
| Power spectrum | `DoubleArray(257)` | 2.1 KB | Reuse |
| MFCC matrix (1s audio) | `Array(98) { DoubleArray(13) }` | 10.2 KB | Per utterance |
| Final features (1s) | `Array(98) { FloatArray(40) }` | 15.7 KB | Per utterance |

**Total static**: ~90 KB. **Per utterance (1s)**: ~26 KB. Well within budget for any Android device.

### 14.3 Threading

- Run `extractMfcc()` on a **background coroutine** (`Dispatchers.Default`), never on the UI thread.
- The FFT and filterbank loops are CPU-bound and will block for ~2–5 ms per second of audio on a modern phone.
- If profiling shows the FFT loop is the bottleneck, consider the JNI/C++ path (same strategy as the DTW JNI warning in `dtw_kotlin_pseudocode.md`).

### 14.4 Avoid Common Pitfalls

| Pitfall | Fix |
|---------|-----|
| Using `Float` instead of `Double` for intermediate math | Use `Double` internally, convert to `Float` only at the final output |
| Creating `DoubleArray` inside the per-frame loop | Pre-allocate and `.fill(0.0)` to reuse |
| Calling `kotlin.math.log()` (base-e) vs `log10()` | We need `ln()` (natural log). Triple-check. |
| Off-by-one in FFT bin indexing | Verify `nBins = nFft / 2 + 1 = 257`, not 256 |
| Using `Array<Double>` (boxed) instead of `DoubleArray` (primitive) | Always use `DoubleArray` for performance |

---

## 15. Validation Test Cases

Dev B must verify that the Kotlin port produces output matching the Python implementation within acceptable tolerance.

### 15.1 Deterministic Test Signal

Use the same synthetic signal in both Python and Kotlin to get reproducible results (no random noise):

```kotlin
/**
 * Generate a 1-second 440 Hz sine wave at 16 kHz sample rate.
 * This is deterministic — no randomness — so both platforms produce identical input.
 */
fun generateTestSignal(): ShortArray {
    val sr = 16000
    val signal = ShortArray(sr)
    for (i in 0 until sr) {
        val t = i.toDouble() / sr
        val sample = 0.5 * sin(2.0 * PI * 440.0 * t)
        signal[i] = (sample * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
    }
    return signal
}
```

Corresponding Python:

```python
import numpy as np
sr = 16000
t = np.arange(sr) / sr
audio = (0.5 * np.sin(2 * np.pi * 440 * t) * 32768).astype(np.int16)
features = extract_mfcc(audio)
```

### 15.2 Expected Shape

```
Input:  16000 samples (1.0s at 16 kHz)
Output: [98, 40]
  - 98 frames = 1 + (16000 - 400) / 160
  - 40 dims   = 13 MFCC + 13 Δ + 13 ΔΔ + 1 Energy
```

### 15.3 Validation Procedure

```kotlin
fun validateMfccPort() {
    val testSignal = generateTestSignal()
    val features = extractMfcc(testSignal)

    // 1. Shape check
    check(features.size == 98) {
        "Expected 98 frames, got ${features.size}"
    }
    check(features[0].size == 40) {
        "Expected 40 features, got ${features[0].size}"
    }

    // 2. Post-CMVN statistics check
    //    After per_utterance CMVN, each dimension should have:
    //      mean ≈ 0.0 (tolerance: |mean| < 0.01)
    //      std  ≈ 1.0 (tolerance: |std - 1.0| < 0.05)
    for (d in 0 until 40) {
        var sum = 0.0
        for (t in features.indices) sum += features[t][d]
        val mean = sum / features.size
        check(kotlin.math.abs(mean) < 0.01) {
            "Dim $d mean = $mean, expected ≈ 0.0"
        }

        var sumSq = 0.0
        for (t in features.indices) {
            val diff = features[t][d] - mean
            sumSq += diff * diff
        }
        val std = sqrt(sumSq / features.size)
        check(kotlin.math.abs(std - 1.0) < 0.05) {
            "Dim $d std = $std, expected ≈ 1.0"
        }
    }

    // 3. Cross-platform numerical check
    //    Run the Python code on the same 440 Hz sine, dump features to a CSV/JSON file.
    //    Load that file in Kotlin and compare element-wise:
    //
    //    val pythonFeatures = loadPythonReferenceFeatures("reference_440hz.json")
    //    for (t in features.indices) {
    //        for (d in 0 until 40) {
    //            val diff = abs(features[t][d] - pythonFeatures[t][d])
    //            check(diff < 1e-4) {
    //                "Mismatch at [$t][$d]: kotlin=${features[t][d]}, python=${pythonFeatures[t][d]}, diff=$diff"
    //            }
    //        }
    //    }

    println("[OK] MFCC validation passed: shape [${features.size}, ${features[0].size}]")
}
```

### 15.4 Tolerance Guidelines

| Check | Tolerance | Notes |
|-------|-----------|-------|
| Frame count | Exact match | Must be identical |
| Feature dimension | Exact (40) | Must be identical |
| Post-CMVN mean | `|mean| < 0.01` | Numerical precision |
| Post-CMVN std | `|std - 1.0| < 0.05` | Numerical precision |
| Element-wise vs Python | `|diff| < 1e-4` | FP64 → FP32 rounding |
| DTW distance on identical input | `< 0.01` | End-to-end sanity check |

> [!CAUTION]
> If the element-wise tolerance of `1e-4` is not met, debug in this order:
> 1. Verify pre-emphasis output (compare first 10 samples)
> 2. Verify a single frame's FFT magnitudes (compare all 257 bins)
> 3. Verify mel energies for frame 0 (compare all 40 values)
> 4. Verify MFCCs before CMVN (compare first frame's 13 values)
>
> The most common source of divergence is the FFT implementation. If using JTransforms, verify the unpacking convention matches Section 6.

---

## Appendix A: Pipeline Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    ShortArray (PCM int16)                     │
└────────────────────────────┬─────────────────────────────────┘
                             │  ÷ 32768
                             ▼
┌──────────────────────────────────────────────────────────────┐
│                    DoubleArray (float64)                      │
└────────────────────────────┬─────────────────────────────────┘
                             │  AGC (if enabled)
                             ▼
                        Pre-emphasis
                             │
                             ▼
                     Frame slicing (400 / 160)
                             │
                             ▼
                     Hamming windowing
                             │
                ┌────────────┼────────────┐
                ▼            │            ▼
          FFT (512)          │      Frame energy
                │            │        log(Σ power)
                ▼            │            │
          Power spectrum     │            │
          |X|²/512           │            │
                │            │            │
                ▼            │            │
          Mel filterbank     │            │
          (40 filters)       │            │
                │            │            │
                ▼            │            │
          log(mel)           │            │
                │            │            │
                ▼            │            │
          DCT-II → 13 MFCCs │            │
                │            │            │
                ▼            │            │
          Δ (K=2)            │            │
                │            │            │
                ▼            │            │
          ΔΔ (K=2)           │            │
                │            │            │
                ▼            ▼            ▼
         ┌──────────────────────────────────┐
         │  Concatenate: [13+13+13+1] = 40  │
         └──────────────────┬───────────────┘
                            │
                            ▼
                     CMVN normalization
                            │
                            ▼
              ┌──────────────────────────┐
              │  Array<FloatArray>[T][40] │
              │   → Feed to DTW matcher  │
              └──────────────────────────┘
```

---

## Appendix B: Quick Reference — Python ↔ Kotlin Mapping

| Python (numpy/scipy) | Kotlin Equivalent | Section |
|----------------------|-------------------|---------|
| `signal.astype(np.float64) / 32768.0` | `pcmInt16[it].toDouble() / 32768.0` | §13 Step 1 |
| `np.sqrt(np.mean(signal ** 2))` | Manual loop: `sqrt(sumSq / size)` | §2 |
| `np.append(signal[0], signal[1:] - coeff * signal[:-1])` | Explicit loop | §3 |
| `np.hamming(N)` | `hammingWindow(N)` | §4 |
| `np.fft.rfft(frame, n=512)` | JTransforms or custom FFT | §6 |
| `np.abs(X) ** 2 / n_fft` | `mag[k] * mag[k] / nFft` | §7 |
| `np.dot(power, filterbank.T)` | Explicit dot-product loop | §8.3 |
| `np.log(x)` | `kotlin.math.ln(x)` | §9 |
| `scipy.fftpack.dct(x, type=2, norm='ortho')` | `dctII(x, nCoeffs)` | §9.1 |
| `np.mean(features, axis=0)` | Column-wise mean loop | §12 |
| `np.std(features, axis=0)` | Column-wise population std loop | §12 |
