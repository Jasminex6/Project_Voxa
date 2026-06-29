package com.example.voxa.ui

import android.content.Context
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import kotlinx.coroutines.flow.StateFlow

// Exposing LogEvent at the package level for clean modular boundaries.
data class LogEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val word: String,
    val phrase: String,
    val confidence: Float,
    val isMatch: Boolean,
    val detail: String
)

/**
 * 🔌 IVoxaViewModel
 * An abstract contract/delegate interface for the Voxa UI state brain.
 * By using this contract, Jetpack Compose screens can bind to either the production database ViewModel
 * or a static mock ViewModel during design layout previews without database crashes.
 */
interface IVoxaViewModel {
    val allProfiles: StateFlow<List<ChildProfile>>
    val activeProfile: StateFlow<ChildProfile?>
    val enrolledIntents: StateFlow<List<EnrolledIntent>>
    val isListening: StateFlow<Boolean>
    val recentEvents: StateFlow<List<LogEvent>>
    val volumeLevel: StateFlow<Float>

    fun createProfile(name: String, gender: String, avatarEmoji: String)
    fun selectActiveProfile(profileId: Long)
    fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String)
    fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String, tempFilePaths: List<String>) {}
    fun exportProfileData(context: Context)
    fun importProfileData(context: Context, uri: android.net.Uri, onSuccess: () -> Unit, onError: (String) -> Unit)
    fun deleteIntent(intent: EnrolledIntent)
    fun toggleListening()
    fun updateListeningState()
    fun addLogSystemEvent(message: String)
    fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String)
    fun clearLogs()
    fun playRecordedSample(intent: EnrolledIntent)
    fun deleteProfile(profile: ChildProfile) {}
    fun updateProfileGender(profile: ChildProfile, newGender: String) {}
}
