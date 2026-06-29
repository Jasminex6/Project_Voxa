package com.example.voxa.ai

import kotlin.math.*

/**
 * 🎵 MfccExtractor — 40-Dimensional MFCC Feature Extraction
 *
 * Extracts Mel-Frequency Cepstral Coefficients from raw PCM audio for DTW template matching.
 * Includes a custom radix-2 Cooley-Tukey FFT to avoid external library dependencies.
 *
 * Output: Array<FloatArray> of shape [T, 40] where:
 *   - Indices 0–12:  13 MFCC coefficients
 *   - Indices 13–25: 13 Δ (delta) MFCCs
 *   - Indices 26–38: 13 ΔΔ (delta-delta) MFCCs
 *   - Index 39:      Log RMS frame energy
 *
 * Ported from: notebooks/mfcc_extractor.py + notebooks/mfcc_kotlin_pseudocode.md (Dev A)
 */

data class MfccConfig(
    val sampleRate: Int       = 16000,
    val frameSizeMs: Int      = 25,       // → 400 samples
    val hopSizeMs: Int        = 10,       // → 160 samples
    val nFft: Int             = 512,
    val nMels: Int            = 40,
    val nMfcc: Int            = 13,
    val fMin: Double          = 100.0,
    val fMax: Double          = 8000.0,
    val preEmphasis: Double   = 0.97,
    val deltaWindowK: Int     = 2,
    val agc: Boolean          = true,
    val agcTargetRms: Double  = 0.05
) {
    val frameLen: Int get() = sampleRate * frameSizeMs / 1000   // 400
    val hopLen: Int get() = sampleRate * hopSizeMs / 1000       // 160
    val nBins: Int get() = nFft / 2 + 1                        // 257
    val featureDim: Int get() = nMfcc * 3 + 1                  // 40
}

class MfccExtractor(private val config: MfccConfig = MfccConfig()) {

    // Pre-allocated reusable buffers
    private val hammingWin = hammingWindow(config.frameLen)
    private val melFilterbank = createMelFilterbank(
        config.nMels, config.nFft, config.sampleRate, config.fMin, config.fMax
    )

    /**
     * Main entry point: extract 40-dimensional MFCC features from raw 16-bit PCM audio.
     *
     * @param pcmInt16 Raw 16-bit PCM samples at 16 kHz
     * @return Array<FloatArray> of shape [T, 40] ready for DTW matching
     */
    fun extract(pcmInt16: ShortArray): Array<FloatArray> {
        // Step 1: Normalize int16 → float64 [-1.0, 1.0]
        var signal = DoubleArray(pcmInt16.size) { pcmInt16[it].toDouble() / 32768.0 }

        // Step 2: AGC
        if (config.agc) {
            signal = applyAgc(signal, config.agcTargetRms)
        }

        // Step 3: Pre-emphasis
        signal = preEmphasis(signal, config.preEmphasis)

        // Step 4: Frame slicing
        val frames = sliceFrames(signal, config.frameLen, config.hopLen)
        val nFrames = frames.size
        if (nFrames == 0) return emptyArray()

        // Step 5: Hamming windowing
        for (i in 0 until nFrames) {
            for (s in 0 until config.frameLen) {
                frames[i][s] *= hammingWin[s]
            }
        }

        // Steps 6–10: FFT → Power → Mel → MFCC → Energy
        val mfccAll = Array(nFrames) { DoubleArray(config.nMfcc) }
        val energyAll = DoubleArray(nFrames)

        for (i in 0 until nFrames) {
            val mag = realFftMagnitude(frames[i], config.nFft)
            val power = powerSpectrum(mag, config.nFft)
            val melEnergies = applyMelFilterbank(power, melFilterbank)
            mfccAll[i] = computeMfcc(melEnergies, config.nMfcc)

            var sumPower = 0.0
            for (k in power.indices) sumPower += power[k]
            energyAll[i] = ln(sumPower + 1e-10)
        }

        // Step 11: Δ and ΔΔ
        val delta = computeDeltas(mfccAll, config.deltaWindowK)
        val deltaDelta = computeDeltas(delta, config.deltaWindowK)

        // Step 12: Concatenate → [T][40]
        val features = concatenateFeatures(mfccAll, delta, deltaDelta, energyAll)

        // Step 13: CMVN normalization
        applyCmvn(features)

        // Convert to FloatArray for DTW compatibility
        return Array(features.size) { t ->
            FloatArray(features[t].size) { d -> features[t][d].toFloat() }
        }
    }

    companion object {

        // ── AGC ──

        fun applyAgc(signal: DoubleArray, targetRms: Double = 0.05, maxGain: Double = 10.0): DoubleArray {
            var sumSq = 0.0
            for (s in signal) sumSq += s * s
            val rms = sqrt(sumSq / signal.size + 1e-10)
            if (rms < 1e-5) return signal.copyOf()

            var scale = targetRms / rms
            scale = min(scale, maxGain)

            val out = DoubleArray(signal.size)
            for (i in signal.indices) {
                val v = signal[i] * scale
                out[i] = v.coerceIn(-1.0, 1.0)
            }
            return out
        }

        // ── Pre-emphasis ──

        fun preEmphasis(signal: DoubleArray, coeff: Double = 0.97): DoubleArray {
            val out = DoubleArray(signal.size)
            out[0] = signal[0]
            for (i in 1 until signal.size) {
                out[i] = signal[i] - coeff * signal[i - 1]
            }
            return out
        }

        // ── Hamming Window ──

        fun hammingWindow(size: Int): DoubleArray {
            val window = DoubleArray(size)
            for (n in 0 until size) {
                window[n] = 0.54 - 0.46 * cos(2.0 * PI * n / (size - 1))
            }
            return window
        }

        // ── Frame Slicing ──

        fun sliceFrames(signal: DoubleArray, frameLen: Int, hopLen: Int): Array<DoubleArray> {
            val nFrames = 1 + (signal.size - frameLen) / hopLen
            if (nFrames <= 0) return emptyArray()
            return Array(nFrames) { i ->
                val start = i * hopLen
                signal.copyOfRange(start, start + frameLen)
            }
        }

        // ── FFT (Radix-2 Cooley-Tukey) ──

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
                        val oddIdx = i + k + halfLen

                        val tRe = curRe * re[oddIdx] - curIm * im[oddIdx]
                        val tIm = curRe * im[oddIdx] + curIm * re[oddIdx]

                        re[oddIdx] = re[evenIdx] - tRe
                        im[oddIdx] = im[evenIdx] - tIm
                        re[evenIdx] = re[evenIdx] + tRe
                        im[evenIdx] = im[evenIdx] + tIm

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

        fun realFftMagnitude(frame: DoubleArray, nFft: Int): DoubleArray {
            val re = DoubleArray(nFft)
            val im = DoubleArray(nFft)
            frame.copyInto(re, 0, 0, frame.size) // zero-pad remainder

            fftInPlace(re, im)

            val nBins = nFft / 2 + 1
            val mag = DoubleArray(nBins)
            for (k in 0 until nBins) {
                mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
            }
            return mag
        }

        // ── Power Spectrum ──

        fun powerSpectrum(mag: DoubleArray, nFft: Int): DoubleArray {
            val power = DoubleArray(mag.size)
            for (k in mag.indices) {
                power[k] = (mag[k] * mag[k]) / nFft.toDouble()
            }
            return power
        }

        // ── Mel Filterbank ──

        fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        fun createMelFilterbank(
            nFilters: Int, nFft: Int, sampleRate: Int,
            fMin: Double, fMax: Double
        ): Array<DoubleArray> {
            val nBins = nFft / 2 + 1

            val melMin = hzToMel(fMin)
            val melMax = hzToMel(fMax)
            val melPoints = DoubleArray(nFilters + 2) { i ->
                melMin + i.toDouble() * (melMax - melMin) / (nFilters + 1)
            }

            val hzPoints = DoubleArray(melPoints.size) { melToHz(melPoints[it]) }
            val bins = IntArray(hzPoints.size) { i ->
                floor((nFft + 1).toDouble() * hzPoints[i] / sampleRate).toInt()
            }

            val filterbank = Array(nFilters) { DoubleArray(nBins) }
            for (i in 0 until nFilters) {
                val left = bins[i]
                val center = bins[i + 1]
                val right = bins[i + 2]

                for (jj in left until center) {
                    if (jj in 0 until nBins && center > left) {
                        filterbank[i][jj] = (jj - left).toDouble() / (center - left).toDouble()
                    }
                }
                for (jj in center until right) {
                    if (jj in 0 until nBins && right > center) {
                        filterbank[i][jj] = (right - jj).toDouble() / (right - center).toDouble()
                    }
                }
            }
            return filterbank
        }

        fun applyMelFilterbank(power: DoubleArray, filterbank: Array<DoubleArray>): DoubleArray {
            val nMels = filterbank.size
            val melEnergies = DoubleArray(nMels)
            for (m in 0 until nMels) {
                var sum = 0.0
                val filter = filterbank[m]
                for (k in filter.indices) {
                    sum += power[k] * filter[k]
                }
                melEnergies[m] = maxOf(sum, 1e-10)
            }
            return melEnergies
        }

        // ── DCT-II (Orthonormal) ──

        fun dctII(x: DoubleArray, nCoeffs: Int): DoubleArray {
            val n = x.size
            val coeffs = DoubleArray(nCoeffs)
            val scaleFactor = sqrt(2.0 / n)

            for (k in 0 until nCoeffs) {
                var sum = 0.0
                for (i in 0 until n) {
                    sum += x[i] * cos(PI * k * (i + 0.5) / n)
                }
                coeffs[k] = sum * scaleFactor
            }

            // Ortho normalization: scale k=0 by 1/sqrt(2)
            coeffs[0] *= 1.0 / sqrt(2.0)
            return coeffs
        }

        fun computeMfcc(melEnergies: DoubleArray, nMfcc: Int): DoubleArray {
            val logMel = DoubleArray(melEnergies.size) { ln(melEnergies[it]) }
            return dctII(logMel, nMfcc)
        }

        // ── Deltas ──

        fun computeDeltas(features: Array<DoubleArray>, K: Int = 2): Array<DoubleArray> {
            val T = features.size
            val D = features[0].size
            val deltas = Array(T) { DoubleArray(D) }

            var denom = 0.0
            for (n in 1..K) denom += (n * n).toDouble()
            denom *= 2.0

            for (t in 0 until T) {
                for (n in 1..K) {
                    val tPlus = minOf(t + n, T - 1)
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

        // ── Feature Concatenation ──

        fun concatenateFeatures(
            mfcc: Array<DoubleArray>,
            delta: Array<DoubleArray>,
            deltaDelta: Array<DoubleArray>,
            energy: DoubleArray
        ): Array<DoubleArray> {
            val T = mfcc.size
            val nMfcc = mfcc[0].size

            return Array(T) { t ->
                val vec = DoubleArray(nMfcc * 3 + 1)
                mfcc[t].copyInto(vec, destinationOffset = 0)
                delta[t].copyInto(vec, destinationOffset = nMfcc)
                deltaDelta[t].copyInto(vec, destinationOffset = nMfcc * 2)
                vec[nMfcc * 3] = energy[t]
                vec
            }
        }

        // ── CMVN ──

        fun applyCmvn(features: Array<DoubleArray>) {
            if (features.isEmpty()) return
            val T = features.size
            val D = features[0].size

            val mean = DoubleArray(D)
            for (t in 0 until T) {
                for (d in 0 until D) {
                    mean[d] += features[t][d]
                }
            }
            for (d in 0 until D) mean[d] /= T.toDouble()

            val std = DoubleArray(D)
            for (t in 0 until T) {
                for (d in 0 until D) {
                    val diff = features[t][d] - mean[d]
                    std[d] += diff * diff
                }
            }
            for (d in 0 until D) std[d] = sqrt(std[d] / T.toDouble())

            for (t in 0 until T) {
                for (d in 0 until D) {
                    features[t][d] = (features[t][d] - mean[d]) / (std[d] + 1e-10)
                }
            }
        }
    }
}
