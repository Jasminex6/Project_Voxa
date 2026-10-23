package com.example.voxa.ai

/**
 * 🔌 IVoxaClassifierEngine — Interface contract for the classification pipeline.
 *
 * Defines the boundary between the audio capture thread (VoxaListenerService)
 * and the AI classification logic.
 */

data class ClassificationResult(
    val isMatch: Boolean,
    val intentName: String?,
    val outputPhrase: String?,
    val audioAssetPath: String?,
    val confidence: Float,
    val reason: String,
    val speakerSimilarity: Float = 0f
)

interface IVoxaClassifierEngine {
    /**
     * Processes a block of raw PCM audio and returns a classification result.
     *
     * Architecture v4 pipeline:
     * VAD → Trim → Pad/Crop 1.44s → YAMNet 2048-D → Cosine + CCP → OOD Gate → Margin Gate
     *
     * @param pcmData Raw 16-bit PCM samples at 16kHz
     * @return ClassificationResult with match details, or a rejection reason
     */
    fun processAudioBlock(pcmData: ShortArray): ClassificationResult?

    /**
     * Notifies the engine that audio playback has started (e.g. text-to-speech).
     * The engine can use this to suppress classification of its own audio output.
     */
    fun notifyAudioPlaybackStarted()

    /** Ends playback suppression and starts a cooldown for speaker echo and buffered audio. */
    fun notifyAudioPlaybackFinished()
}
