package com.example.voxa.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.AcousticTemplate
import com.example.voxa.data.VoxaDatabase
import com.example.voxa.services.VoxaListenerService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 🎓 VoxaViewModel
 * The architecture bridge connecting Room database tables to Jetpack Compose screens.
 * Extends AndroidViewModel to safely obtain application context for Room and Service control.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoxaViewModel(application: Application) : AndroidViewModel(application), IVoxaViewModel {

    // Access door to the database queries.
    private val voxaDao = VoxaDatabase.getDatabase(application).voxaDao()

    // ── 👤 CHILD PROFILE STATES ──

    // Flow of all created child profiles.
    // Analogy: This is like a live radio broadcast of the profile list. The UI tunes into this frequency (subscribes)
    // and automatically updates the list card when a profile is added or edited.
    override val allProfiles: StateFlow<List<ChildProfile>> = voxaDao.getAllProfilesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Flow of the currently active child profile.
    private val _activeProfile = MutableStateFlow<ChildProfile?>(null)
    override val activeProfile: StateFlow<ChildProfile?> = _activeProfile.asStateFlow()

    // ── 🎙️ ENROLLED INTENTS STATES ──

    // Flow of intents dynamically filtered by the active profile ID.
    // Analogy: flatMapLatest acts like a magnifying glass that automatically shifts focus when a new child
    // profile is selected. It listens for shifts in the active child profile, and immediately redirects
    // the query stream to retrieve the custom dictionary words only for that specific child.
    override val enrolledIntents: StateFlow<List<EnrolledIntent>> = activeProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                voxaDao.getIntentsForProfileFlow(profile.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── 🎙️ LISTENING SYSTEM STATES ──

    // Tracks if the microphone recorder service is actively listening.
    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // ── 📈 RECENT MATCH LOG EVENTS (In-Memory) ──

    private val _recentEvents = MutableStateFlow<List<LogEvent>>(emptyList())
    override val recentEvents: StateFlow<List<LogEvent>> = _recentEvents.asStateFlow()

    // ── INITIALIZATION ──

    init {
        // Runs immediately when the app starts, setting up our default active profile
        // and synchronizing the UI status with the actual state of the background service.
        viewModelScope.launch {
            // Find and set the active profile on startup
            val active = voxaDao.getActiveProfile()
            _activeProfile.value = active
            
            // Sync initial background service state
            _isListening.value = VoxaListenerService.isRunning
        }
    }

    // ── 👤 PROFILE ACTIONS ──

    // Adds a new child profile to the Room database.
    // If there is currently no active profile (e.g. fresh installation), this new profile is set
    // as the active profile automatically.
    override fun createProfile(name: String, gender: String, avatarEmoji: String) {
        viewModelScope.launch {
            val newProfile = ChildProfile(
                name = name,
                gender = gender,
                isActive = _activeProfile.value == null,
                avatarEmoji = avatarEmoji
            )
            val id = voxaDao.insertProfile(newProfile)
            if (_activeProfile.value == null) {
                // If it's the first profile, set it as active
                val created = newProfile.copy(id = id, isActive = true)
                _activeProfile.value = created
            }
        }
    }

    // Switches the active profile and updates database flags atomically inside a Room transaction.
    override fun selectActiveProfile(profileId: Long) {
        viewModelScope.launch {
            voxaDao.selectActiveProfile(profileId)
            _activeProfile.value = voxaDao.getActiveProfile()
        }
    }

    // ── 🎙️ INTENT ACTIONS ──

    // Enrolls a custom sound-to-meaning mapping for the currently active profile.
    // Analogy: Teaching the app a "secret word" for a specific child.
    override fun enrollIntent(intentName: String, outputPhrase: String, audioAssetPath: String) {
        enrollIntent(intentName, outputPhrase, audioAssetPath, emptyList())
    }

    override fun enrollIntent(
        intentName: String,
        outputPhrase: String,
        audioAssetPath: String,
        tempFilePaths: List<String>
    ) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            val intent = EnrolledIntent(
                profileId = profile.id,
                intentName = intentName,
                outputPhrase = outputPhrase,
                audioAssetPath = audioAssetPath
            )
            val intentId = voxaDao.insertIntent(intent)
            for (path in tempFilePaths) {
                val template = AcousticTemplate(
                    intentId = intentId,
                    templateFeatures = path
                )
                voxaDao.insertTemplate(template)
            }
        }
    }

    // Deletes an enrolled intent. The database will automatically cascade and delete its associated templates.
    // Analogy: Erasing a learned word from the app's internal vocabulary for the child.
    override fun deleteIntent(intent: EnrolledIntent) {
        viewModelScope.launch {
            voxaDao.deleteIntent(intent)
        }
    }

    // ── 🎙️ LISTENING CONTROLS ──

    // Analogy: This acts like the remote power switch for our physical microphone engine.
    // It issues an intent to either build the foreground service or tear it down.
    override fun toggleListening() {
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, VoxaListenerService::class.java)

        if (VoxaListenerService.isRunning) {
            context.stopService(serviceIntent)
            _isListening.value = false
            addLogSystemEvent("Listening session manually paused")
        } else {
            context.startForegroundService(serviceIntent)
            _isListening.value = true
            addLogSystemEvent("Listening session active — monitoring background sounds")
        }
    }

    // Refreshes the listening boolean state based on the actual service status.
    override fun updateListeningState() {
        _isListening.value = VoxaListenerService.isRunning
    }

    // ── 📈 SIMULATION & LOGGING HELPERS ──

    // Appends a system status event to the timeline list.
    override fun addLogSystemEvent(message: String) {
        val event = LogEvent(
            word = "SYSTEM",
            phrase = message,
            confidence = 1.0f,
            isMatch = true,
            detail = "System Action"
        )
        _recentEvents.value = (listOf(event) + _recentEvents.value).take(50) // limit to 50
    }

    // Simulates a vocalization detection match for development/testing (the hackathon simulator).
    // Analogy: This is like a mock injector that injects mock test data into the screen timeline,
    // so we don't have to capture real audio signals to test the UI response.
    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {
        val event = LogEvent(
            word = word,
            phrase = phrase,
            confidence = confidence,
            isMatch = isMatch,
            detail = reason
        )
        _recentEvents.value = (listOf(event) + _recentEvents.value).take(50)
    }

    // Clears the timeline.
    override fun clearLogs() {
        _recentEvents.value = emptyList()
    }

    override fun deleteProfile(profile: ChildProfile) {
        viewModelScope.launch {
            voxaDao.deleteProfile(profile)
            if (_activeProfile.value?.id == profile.id) {
                // Find another profile to activate if available
                val remaining = allProfiles.value.filter { it.id != profile.id }
                if (remaining.isNotEmpty()) {
                    selectActiveProfile(remaining.first().id)
                } else {
                    _activeProfile.value = null
                }
            }
        }
    }

    override fun updateProfileGender(profile: ChildProfile, newGender: String) {
        viewModelScope.launch {
            val updated = profile.copy(gender = newGender)
            voxaDao.updateProfile(updated)
            if (_activeProfile.value?.id == profile.id) {
                _activeProfile.value = updated
            }
        }
    }
}
