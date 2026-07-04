package com.example.voxa.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.AcousticTemplate
import com.example.voxa.data.PracticeStats
import com.example.voxa.data.VoxaDatabase
import com.example.voxa.services.VoxaListenerService
import android.widget.Toast
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🎓 VoxaViewModel
 * The architecture bridge connecting Room database tables to Jetpack Compose screens.
 * Extends AndroidViewModel to safely obtain application context for Room and Service control.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoxaViewModel(application: Application) : AndroidViewModel(application), IVoxaViewModel {

    // Access door to the database queries.
    private val voxaDao = VoxaDatabase.getDatabase(application).voxaDao()

    // YAMNet encoder for prototypical matching enrollment
    private val yamnetEncoder = com.example.voxa.ai.YamnetEncoder(application)

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

    override val practiceStats: StateFlow<List<PracticeStats>> = activeProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                voxaDao.getPracticeStatsForProfileFlow(profile.id)
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

    // ── 🎙️ LIVE MICROPHONE VOLUME LEVEL ──

    private val _volumeLevel = MutableStateFlow(0f)
    override val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    // ── ⚠️ BIFURCATION WARNING STATE ──
    private val _bifurcationWarningTriggered = MutableStateFlow<String?>(null)
    override val bifurcationWarningTriggered: StateFlow<String?> = _bifurcationWarningTriggered.asStateFlow()

    override fun clearBifurcationWarning() {
        _bifurcationWarningTriggered.value = null
    }

    // ── BROADCAST RECEIVER FOR REAL-TIME RESULTS & VOLUME ──

    private val classificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoxaListenerService.ACTION_CLASSIFICATION_RESULT -> {
                    val isMatch = intent.getBooleanExtra(VoxaListenerService.EXTRA_IS_MATCH, false)
                    val intentName = intent.getStringExtra(VoxaListenerService.EXTRA_INTENT_NAME) ?: "Unknown"
                    val outputPhrase = intent.getStringExtra(VoxaListenerService.EXTRA_OUTPUT_PHRASE) ?: ""
                    val confidence = intent.getFloatExtra(VoxaListenerService.EXTRA_CONFIDENCE, 0f)
                    val reason = intent.getStringExtra(VoxaListenerService.EXTRA_REASON) ?: ""

                    simulateVoiceMatch(
                        word = intentName,
                        phrase = outputPhrase,
                        confidence = confidence,
                        isMatch = isMatch,
                        reason = reason
                    )
                }
                VoxaListenerService.ACTION_VOLUME_UPDATE -> {
                    val vol = intent.getFloatExtra(VoxaListenerService.EXTRA_VOLUME, 0f)
                    _volumeLevel.value = vol
                }
            }
        }
    }

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

        // Register broadcast receiver for real-time events and volume levels
        val filter = IntentFilter().apply {
            addAction(VoxaListenerService.ACTION_CLASSIFICATION_RESULT)
            addAction(VoxaListenerService.ACTION_VOLUME_UPDATE)
        }
        getApplication<Application>().registerReceiver(classificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(classificationReceiver)
        } catch (_: Exception) { /* already unregistered */ }
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
        android.util.Log.e("VoxaDebug", "enrollIntent called! tempFilePaths size: ${tempFilePaths.size}")
        val profile = _activeProfile.value
        if (profile == null) {
            android.util.Log.e("VoxaDebug", "enrollIntent aborted: _activeProfile.value is null!")
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.e("VoxaDebug", "Starting enrollment coroutine for ${intentName.trim()}")
                // Clear any previous warning
                _bifurcationWarningTriggered.value = null

                // 1. Load and process all PCM recordings to extract YAMNet embeddings
                val embeddings = mutableListOf<FloatArray>()
                for (path in tempFilePaths) {
                    try {
                        val pcmData = com.example.voxa.utils.AudioFileHelper.readPcmFile(java.io.File(path))
                        // Extract embedding using the YamnetEncoder helper directly (pcmData is already VAD-extracted)
                        val emb = yamnetEncoder.extractFromPcm(pcmData)
                        embeddings.add(emb)
                    } catch (e: Exception) {
                        android.util.Log.e("VoxaViewModel", "Embedding extraction failed for $path: ${e.message}")
                    }
                }

                if (embeddings.isEmpty()) {
                    android.util.Log.e("VoxaViewModel", "No valid audio embeddings extracted for enrollment")
                    return@launch
                }

                // 2. Run the PrototypicalMatcher's enrollment centroid pipeline
                val protoResult = com.example.voxa.ai.PrototypicalMatcher.computeEnrollmentCentroids(embeddings)

                // 3. Create and insert the EnrolledIntent
                val intent = EnrolledIntent(
                    profileId = profile.id,
                    intentName = intentName.trim(),
                    outputPhrase = outputPhrase.trim(),
                    audioAssetPath = audioAssetPath,
                    oodThreshold = protoResult.oodThreshold
                )
                val intentId = voxaDao.insertIntent(intent)

                // 4. Serialize centroids and store in AcousticTemplate
                val serializedCentroids = com.example.voxa.ai.PrototypicalMatcher.serializeCentroids(protoResult.centroids)
                val template = AcousticTemplate(
                    intentId = intentId,
                    templateFeatures = serializedCentroids
                )
                voxaDao.insertTemplate(template)

                // 5. If K-Means bifurcation was triggered, notify UI
                if (protoResult.bifurcated) {
                    _bifurcationWarningTriggered.value = "These recordings sound very different. For best results, try recording when ${profile.name} is calm, or record the stressed version separately."
                }

                android.util.Log.d("VoxaViewModel", "Successfully enrolled intent '$intentName' with ${protoResult.centroids.size} centroids (bifurcated=${protoResult.bifurcated})")
            } catch (e: Exception) {
                android.util.Log.e("VoxaViewModel", "Failed to enroll intent: ${e.message}", e)
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

    override fun exportProfileData(context: Context) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            try {
                val rootJson = org.json.JSONObject()

                // 1. Serialize Profile
                val profileJson = org.json.JSONObject().apply {
                    put("name", profile.name)
                    put("gender", profile.gender)
                    put("avatarEmoji", profile.avatarEmoji)
                    put("speakerEmbedding", profile.speakerEmbedding ?: "")
                }
                rootJson.put("profile", profileJson)

                // 2. Serialize Intents & Templates
                val intentsJsonArray = org.json.JSONArray()
                val intents = voxaDao.getIntentsForProfile(profile.id)
                for (intent in intents) {
                    val intentJson = org.json.JSONObject().apply {
                        put("intentName", intent.intentName)
                        put("outputPhrase", intent.outputPhrase)
                        put("audioAssetPath", intent.audioAssetPath)
                    }

                    val templates = voxaDao.getTemplatesForIntent(intent.id)
                    val templatesJsonArray = org.json.JSONArray()
                    for (template in templates) {
                        templatesJsonArray.put(template.templateFeatures)
                    }
                    intentJson.put("templates", templatesJsonArray)
                    intentsJsonArray.put(intentJson)
                }
                rootJson.put("intents", intentsJsonArray)

                // 3. Share the JSON string
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Voxa Profile Export - ${profile.name}")
                    putExtra(Intent.EXTRA_TEXT, rootJson.toString(2))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Profile via").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                android.util.Log.e("VoxaViewModel", "Failed to export profile: ${e.message}", e)
            }
        }
    }

    override fun importProfileData(
        context: Context,
        uri: android.net.Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Read JSON string from URI
                val contentResolver = context.contentResolver
                val stringBuilder = StringBuilder()
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stringBuilder.append(line)
                            }
                        }
                    }
                }

                val jsonString = stringBuilder.toString()
                if (jsonString.isBlank()) {
                    onError("Selected file is empty")
                    return@launch
                }

                val rootJson = org.json.JSONObject(jsonString)

                // 2. Parse Profile
                val profileJson = rootJson.getJSONObject("profile")
                val name = profileJson.getString("name")
                val gender = profileJson.getString("gender")
                val avatarEmoji = profileJson.getString("avatarEmoji")
                val speakerEmbeddingVal = profileJson.optString("speakerEmbedding", "")
                val speakerEmbedding = if (speakerEmbeddingVal.isNotBlank()) speakerEmbeddingVal else null

                // Insert profile (will be set as active if it's the only one, or we select it)
                val newProfile = ChildProfile(
                    name = "$name (Imported)",
                    gender = gender,
                    avatarEmoji = avatarEmoji,
                    speakerEmbedding = speakerEmbedding,
                    isActive = false
                )
                val newProfileId = voxaDao.insertProfile(newProfile)

                // 3. Parse and Insert Intents & Templates
                val intentsJsonArray = rootJson.getJSONArray("intents")
                for (i in 0 until intentsJsonArray.length()) {
                    val intentJson = intentsJsonArray.getJSONObject(i)
                    val intentName = intentJson.getString("intentName")
                    val outputPhrase = intentJson.getString("outputPhrase")
                    val audioAssetPath = intentJson.getString("audioAssetPath")

                    val enrolledIntent = EnrolledIntent(
                        profileId = newProfileId,
                        intentName = intentName,
                        outputPhrase = outputPhrase,
                        audioAssetPath = audioAssetPath
                    )
                    val newIntentId = voxaDao.insertIntent(enrolledIntent)

                    val templatesJsonArray = intentJson.getJSONArray("templates")
                    for (j in 0 until templatesJsonArray.length()) {
                        val templateFeatures = templatesJsonArray.getString(j)
                        val template = AcousticTemplate(
                            intentId = newIntentId,
                            templateFeatures = templateFeatures
                        )
                        voxaDao.insertTemplate(template)
                    }
                }

                // Automatically select the newly imported profile
                selectActiveProfile(newProfileId)
                addLogSystemEvent("Successfully imported profile '$name' with ${intentsJsonArray.length()} intents")
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("VoxaViewModel", "Failed to import profile: ${e.message}", e)
                onError(e.message ?: "Unknown error occurred during import")
            }
        }
    }

    override fun playRecordedSample(intent: EnrolledIntent) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanIntentName = intent.intentName.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}_]"), "_")
                val file = java.io.File(getApplication<Application>().cacheDir, "template_${intent.profileId}_${cleanIntentName}_0.pcm")
                if (file.exists()) {
                    val pcmData = com.example.voxa.utils.AudioFileHelper.readPcmFile(file)
                    
                    val minBufSize = android.media.AudioTrack.getMinBufferSize(
                        16000,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT
                    )
                    
                    // AudioTrack static mode allows playing the ShortArray buffer directly
                    val audioTrack = android.media.AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        16000,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBufSize, pcmData.size * 2),
                        android.media.AudioTrack.MODE_STATIC
                    )
                    audioTrack.write(pcmData, 0, pcmData.size)
                    audioTrack.play()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "No local recorded sample file available for preview", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoxaViewModel", "Failed to play recorded sample preview: ${e.message}", e)
            }
        }
    }

    override fun recordPracticeAttempt(word: String, score: Int, stars: Int) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            voxaDao.insertPracticeStats(
                PracticeStats(
                    profileId = profile.id,
                    word = word,
                    score = score,
                    stars = stars
                )
            )
        }
    }

    override fun updateCaregiverPhones(phone1: String, phone2: String, phone3: String) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            val updated = profile.copy(caregiverPhone1 = phone1, caregiverPhone2 = phone2, caregiverPhone3 = phone3)
            voxaDao.updateProfile(updated)
            _activeProfile.value = updated
        }
    }
}
