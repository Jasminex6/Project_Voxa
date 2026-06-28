# Voxa VAD — Kotlin Port Pseudocode for Dev B

> **Author:** Dev A
> **Date:** June 16, 2026
> **Purpose:** Step-by-step implementation guide for porting `vad.py` to Kotlin.
> Dev B should use this document to build the Kotlin VAD without needing to read the Python.

---

## 1. Configuration Data Class

```kotlin
data class VADConfig(
    val sampleRate: Int = 16_000,
    val frameMs: Int = 20,                  // 20ms per frame
    val aggressiveness: Int = 2,            // 0–3
    val speechTriggerFrames: Int = 8,       // M = 8 consecutive speech → trigger
    val silenceBoundaryFrames: Int = 15,    // N = 15 consecutive silent → end segment
    val minSegmentMs: Int = 400,
    val maxSegmentMs: Int = 2000
) {
    val frameSize: Int get() = sampleRate * frameMs / 1000      // 320 samples
    val minSamples: Int get() = sampleRate * minSegmentMs / 1000 // 6400
    val maxSamples: Int get() = sampleRate * maxSegmentMs / 1000 // 32000
}
```

## 2. State Machine — States

```kotlin
enum class VADState {
    SILENCE,
    SPEECH_COLLECTING,
    SEGMENT_COMPLETE,
    DISCARDED
}
```

## 3. Core Class Structure

```kotlin
class VoxaVAD(private val config: VADConfig = VADConfig()) {
    
    private var state: VADState = VADState.SILENCE
    private var speechFrameCount: Int = 0
    private var silenceFrameCount: Int = 0
    private val segmentBuffer: MutableList<ShortArray> = mutableListOf()
    private var segmentSampleCount: Int = 0
    
    fun reset() {
        state = VADState.SILENCE
        speechFrameCount = 0
        silenceFrameCount = 0
        segmentBuffer.clear()
        segmentSampleCount = 0
    }
    
    // ... methods below
}
```

## 4. Frame-Level Processing (CORE ALGORITHM)

```
FUNCTION processFrame(frameBytes: ByteArray) → (VADState, ShortArray?)
    
    isSpeech = webRtcVad.isSpeech(frameBytes, sampleRate)
    frameSamples = convertBytesToShortArray(frameBytes)
    
    SWITCH state:
    
    CASE SILENCE:
        IF isSpeech:
            speechFrameCount++
            segmentBuffer.add(frameSamples)
            segmentSampleCount += frameSamples.size
            
            IF speechFrameCount >= config.speechTriggerFrames:  // M = 8
                state = SPEECH_COLLECTING
                silenceFrameCount = 0
        ELSE:
            speechFrameCount = 0
            segmentBuffer.clear()
            segmentSampleCount = 0
    
    CASE SPEECH_COLLECTING:
        segmentBuffer.add(frameSamples)
        segmentSampleCount += frameSamples.size
        
        IF NOT isSpeech:
            silenceFrameCount++
            
            IF silenceFrameCount >= config.silenceBoundaryFrames:  // N = 15
                // Segment ended — validate length
                segment = concatenateBuffers(segmentBuffer)
                reset()
                
                IF config.minSamples <= segment.size <= config.maxSamples:
                    RETURN (SEGMENT_COMPLETE, segment)
                ELSE:
                    RETURN (DISCARDED, null)
        ELSE:
            silenceFrameCount = 0
        
        // Safety: reject if too long
        IF segmentSampleCount > config.maxSamples:
            reset()
            RETURN (DISCARDED, null)
    
    RETURN (state, null)
```

## 5. Full Audio Processing

```
FUNCTION processAudio(pcmInt16: ShortArray) → List<ShortArray>
    
    reset()
    segments = emptyList<ShortArray>()
    frameSize = config.frameSize  // 320 samples
    
    FOR start IN 0 .. (pcmInt16.size - frameSize) STEP frameSize:
        frame = pcmInt16.sliceArray(start until start + frameSize)
        frameBytes = shortArrayToByteArray(frame)  // Little-endian 16-bit
        
        (state, segment) = processFrame(frameBytes)
        IF segment != null:
            segments.add(segment)
    
    RETURN segments
```

## 6. WebRTC VAD Integration on Android

```kotlin
// Option A: Use the Android WebRTC VAD library
// Add to build.gradle:
//   implementation("io.github.nicusor-otel:webrtcvad-android:1.0.0")
//
// Or use the C library via JNI — WebRTC's built-in VAD is in:
//   webrtc/common_audio/vad/

// Option B: If WebRTC is unavailable, use energy-based fallback:
fun isSpeechEnergy(frameShorts: ShortArray, threshold: Double = 500.0): Boolean {
    var sumSquares = 0.0
    for (s in frameShorts) {
        sumSquares += s.toDouble() * s.toDouble()
    }
    val rms = sqrt(sumSquares / frameShorts.size)
    return rms > threshold
}
```

## 7. Byte Conversion Utilities

```kotlin
// ShortArray → ByteArray (Little Endian)
fun shortArrayToByteArray(shorts: ShortArray): ByteArray {
    val bytes = ByteArray(shorts.size * 2)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (s in shorts) {
        buffer.putShort(s)
    }
    return bytes
}

// ByteArray → ShortArray (Little Endian)
fun byteArrayToShortArray(bytes: ByteArray): ShortArray {
    val shorts = ShortArray(bytes.size / 2)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in shorts.indices) {
        shorts[i] = buffer.getShort()
    }
    return shorts
}

// Concatenate list of ShortArrays
fun concatenateBuffers(buffers: List<ShortArray>): ShortArray {
    val totalSize = buffers.sumOf { it.size }
    val result = ShortArray(totalSize)
    var offset = 0
    for (buf in buffers) {
        System.arraycopy(buf, 0, result, offset, buf.size)
        offset += buf.size
    }
    return result
}
```

## 8. Integration Point

This VAD sits at the start of `IVoxaClassifierEngine.classifyUtterance()`:

```kotlin
class VoxaClassifierEngine : IVoxaClassifierEngine {
    
    private val vad = VoxaVAD()
    
    override fun classifyUtterance(
        pcmData: ShortArray,
        enrolledIntents: List<EnrolledIntent>
    ): String {
        // Step 1: VAD — extract speech segments
        val segments = vad.processAudio(pcmData)
        if (segments.isEmpty()) return "unknown"
        
        // Step 2: Speaker Verification (ECAPA-TDNN)
        // Step 3: MFCC extraction
        // Step 4: DTW matching
        // Step 5: Margin Gate
        // ...
    }
}
```

## 9. Performance Notes for Android

- **Memory:** Pre-allocate the segment buffer to `maxSamples` to avoid GC pressure
- **Threading:** VAD runs on the recording thread (already high priority)
- **CPU:** WebRTC VAD is extremely lightweight (~0.1ms per 20ms frame)
- **Battery:** VAD prevents expensive MFCC/DTW computation on silence → major battery saving

## 10. Test Cases Dev B Should Validate

| Test | Input | Expected |
|------|-------|----------|
| Pure silence (2s) | `ShortArray(32000) { 0 }` | Empty list |
| Valid 1s tone | 1s of 300Hz sine at amplitude 8000 | 1 segment, ~1000ms |
| 200ms burst | Very short tone | Empty list (below 400ms minimum) |
| 3s continuous tone | 3s of tone | Empty list (above 2000ms maximum) |
| Two separate 800ms bursts | Tone—silence—tone | 2 segments |
