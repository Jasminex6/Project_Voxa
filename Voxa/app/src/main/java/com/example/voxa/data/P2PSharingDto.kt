package com.example.voxa.data

/**
 * 📦 P2PSharingDto
 * The complete data bundle transferred during P2P profile sharing.
 * Contains everything needed to reconstruct a working profile on the receiver:
 * - Child profile metadata
 * - Caregiver contact info
 * - Full intent library with acoustic templates (for classification)
 * - NO raw PCM files (those stay on the sender's device)
 */
data class P2PSharingDto(
    // ── Child Profile ──
    val childName: String,
    val childGender: String,         // "Male" or "Female"
    val childAvatarEmoji: String,
    val speakerEmbedding: String?,   // ECAPA-TDNN embedding (nullable)

    // ── Caregiver Data ──
    val caregiverName: String,
    val caregiverPhone: String,
    val caregiverRelation: String,

    // ── Intent Library (with acoustic data) ──
    val intents: List<IntentWithTemplates>
)

/**
 * A single intent entry bundled with its acoustic template centroids.
 * The templates contain the serialized MFCC/YAMNet feature vectors —
 * these are the mathematical embeddings, NOT the raw audio.
 * This means classification works on the receiver, but audio preview does not.
 */
data class IntentWithTemplates(
    val intentName: String,
    val outputPhrase: String,
    val audioAssetPath: String,       // Kept for voice pack playback resolution
    val oodThreshold: Float,
    val templateFeatures: List<String> // Each string is a serialized float array centroid
)
