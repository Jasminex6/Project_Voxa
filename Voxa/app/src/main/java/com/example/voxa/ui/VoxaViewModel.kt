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
import com.example.voxa.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

    override val allProfiles: StateFlow<List<ChildProfile>> = voxaDao.getAllProfilesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeProfile = MutableStateFlow<ChildProfile?>(null)
    override val activeProfile: StateFlow<ChildProfile?> = _activeProfile.asStateFlow()

    // ── 🎙️ ENROLLED INTENTS STATES ──

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

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // ── 📈 RECENT MATCH LOG EVENTS (In-Memory) ──

    private val _recentEvents = MutableStateFlow<List<LogEvent>>(emptyList())
    override val recentEvents: StateFlow<List<LogEvent>> = combine(_recentEvents, activeProfile) { events, profile ->
        val currentProfileId = profile?.id ?: 0L
        events.filter { it.profileId == currentProfileId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ── 🎙️ LIVE MICROPHONE VOLUME LEVEL ──

    private val _volumeLevel = MutableStateFlow(0f)
    override val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    // ── ⚠️ BIFURCATION WARNING STATE ──
    private val _bifurcationWarningTriggered = MutableStateFlow<String?>(null)
    override val bifurcationWarningTriggered: StateFlow<String?> = _bifurcationWarningTriggered.asStateFlow()

    override fun clearBifurcationWarning() {
        _bifurcationWarningTriggered.value = null
    }

    // ── 🌐 APP LANGUAGE STATE ──
    private val _appLanguage = MutableStateFlow("en")
    override val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    // ── BROADCAST RECEIVER FOR REAL-TIME RESULTS & VOLUME ──

    private val classificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoxaListenerService.ACTION_CLASSIFICATION_RESULT -> {
                    val isMatch = intent.getBooleanExtra(VoxaListenerService.EXTRA_IS_MATCH, false)
                    if (isMatch) {
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
                }
                VoxaListenerService.ACTION_VOLUME_UPDATE -> {
                    val vol = intent.getFloatExtra(VoxaListenerService.EXTRA_VOLUME, 0f)
                    _volumeLevel.value = vol
                }
                VoxaListenerService.ACTION_SERVICE_DESTROYED -> {
                    _isListening.value = false
                }
            }
        }
    }

    // ── INITIALIZATION ──

    init {
        val prefs = application.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("language", "en") ?: "en"
        _appLanguage.value = savedLang

        viewModelScope.launch {
            val active = voxaDao.getActiveProfile()
            _activeProfile.value = active
            _isListening.value = VoxaListenerService.isRunning
        }

        val filter = IntentFilter().apply {
            addAction(VoxaListenerService.ACTION_CLASSIFICATION_RESULT)
            addAction(VoxaListenerService.ACTION_VOLUME_UPDATE)
            addAction(VoxaListenerService.ACTION_SERVICE_DESTROYED)
        }
        getApplication<Application>().registerReceiver(classificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private var testAudioPlayer: com.example.voxa.utils.AudioPlayer? = null

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(classificationReceiver)
        } catch (_: Exception) { /* already unregistered */ }
        testAudioPlayer?.release()
        testAudioPlayer = null
    }

    // ── 👤 PROFILE ACTIONS ──

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
                val created = newProfile.copy(id = id, isActive = true)
                _activeProfile.value = created
            }
        }
    }

    override fun selectActiveProfile(profileId: Long) {
        viewModelScope.launch {
            voxaDao.selectActiveProfile(profileId)
            _activeProfile.value = voxaDao.getActiveProfile()
            
            if (VoxaListenerService.isRunning) {
                val context = getApplication<Application>()
                val serviceIntent = Intent(context, VoxaListenerService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    // ── 🎙️ INTENT ACTIONS ──

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
                _bifurcationWarningTriggered.value = null

                val embeddings = mutableListOf<FloatArray>()
                for (path in tempFilePaths) {
                    try {
                        val pcmData = com.example.voxa.utils.AudioFileHelper.readPcmFile(java.io.File(path))
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

                val protoResult = com.example.voxa.ai.PrototypicalMatcher.computeEnrollmentCentroids(embeddings)

                val intent = EnrolledIntent(
                    profileId = profile.id,
                    intentName = intentName.trim(),
                    outputPhrase = outputPhrase.trim(),
                    audioAssetPath = audioAssetPath,
                    oodThreshold = protoResult.oodThreshold
                )
                val intentId = voxaDao.insertIntent(intent)

                val serializedCentroids = com.example.voxa.ai.PrototypicalMatcher.serializeCentroids(protoResult.centroids)
                val template = AcousticTemplate(
                    intentId = intentId,
                    templateFeatures = serializedCentroids
                )
                voxaDao.insertTemplate(template)

                if (protoResult.bifurcated) {
                    _bifurcationWarningTriggered.value = "These recordings sound very different. For best results, try recording when ${profile.name} is calm, or record the stressed version separately."
                }

                android.util.Log.d("VoxaViewModel", "Successfully enrolled intent '$intentName' with ${protoResult.centroids.size} centroids (bifurcated=${protoResult.bifurcated})")
            } catch (e: Exception) {
                android.util.Log.e("VoxaViewModel", "Failed to enroll intent: ${e.message}", e)
            }

            if (VoxaListenerService.isRunning) {
                val context = getApplication<Application>()
                val serviceIntent = Intent(context, VoxaListenerService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    override fun deleteIntent(intent: EnrolledIntent) {
        viewModelScope.launch {
            voxaDao.deleteIntent(intent)
            
            val remainingIntents = voxaDao.getIntentsForProfile(intent.profileId)
            if (remainingIntents.isEmpty()) {
                val active = voxaDao.getActiveProfile()
                if (active != null && active.id == intent.profileId) {
                    val updated = active.copy(speakerEmbedding = null)
                    voxaDao.updateProfile(updated)
                    _activeProfile.value = updated
                    android.util.Log.d("VoxaViewModel", "Library is empty, cleared speaker embedding for ${active.name}")
                }
            }

            if (VoxaListenerService.isRunning) {
                val context = getApplication<Application>()
                val serviceIntent = Intent(context, VoxaListenerService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    // ── 🎙️ LISTENING CONTROLS ──

    override fun toggleListening() {
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, VoxaListenerService::class.java)

        if (VoxaListenerService.isRunning) {
            context.stopService(serviceIntent)
            _isListening.value = false
            addLogSystemEvent("log_listening_paused")
        } else {
            context.startForegroundService(serviceIntent)
            _isListening.value = true
            addLogSystemEvent("log_listening_active")
        }
    }

    override fun updateListeningState() {
        _isListening.value = VoxaListenerService.isRunning
    }

    // ── 📈 SIMULATION & LOGGING HELPERS ──

    override fun addLogSystemEvent(message: String) {
        val profileId = _activeProfile.value?.id ?: 0L
        val event = LogEvent(
            profileId = profileId,
            word = "SYSTEM",
            phrase = message,
            confidence = 1.0f,
            isMatch = true,
            detail = "System Action"
        )
        _recentEvents.value = (listOf(event) + _recentEvents.value).take(50)
    }

    override fun simulateVoiceMatch(word: String, phrase: String, confidence: Float, isMatch: Boolean, reason: String) {
        val profileId = _activeProfile.value?.id ?: 0L
        val event = LogEvent(
            profileId = profileId,
            word = word,
            phrase = phrase,
            confidence = confidence,
            isMatch = isMatch,
            detail = reason
        )
        _recentEvents.value = (listOf(event) + _recentEvents.value).take(50)
    }

    override fun clearLogs() {
        _recentEvents.value = emptyList()
    }

    override fun deleteProfile(profile: ChildProfile) {
        viewModelScope.launch {
            voxaDao.deleteProfile(profile)
            if (_activeProfile.value?.id == profile.id) {
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

    override fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        val prefs = getApplication<Application>().getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("language", lang).apply()
        val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(lang)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
    }

    override fun updateCaregivers(profile: ChildProfile, caregiversJson: String) {
        viewModelScope.launch {
            val updated = profile.copy(caregiverContactsJson = caregiversJson)
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

                val profileJson = org.json.JSONObject().apply {
                    put("name", profile.name)
                    put("gender", profile.gender)
                    put("avatarEmoji", profile.avatarEmoji)
                    put("speakerEmbedding", profile.speakerEmbedding ?: "")
                    put("caregiverContactsJson", profile.caregiverContactsJson ?: "")
                }
                rootJson.put("profile", profileJson)

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

                val profileJson = rootJson.getJSONObject("profile")
                val name = profileJson.getString("name")
                val gender = profileJson.getString("gender")
                val avatarEmoji = profileJson.getString("avatarEmoji")
                val speakerEmbeddingVal = profileJson.optString("speakerEmbedding", "")
                val speakerEmbedding = if (speakerEmbeddingVal.isNotBlank()) speakerEmbeddingVal else null
                val caregiverContactsVal = profileJson.optString("caregiverContactsJson", "")
                val caregiverContacts = if (caregiverContactsVal.isNotBlank()) caregiverContactsVal else null

                val newProfile = ChildProfile(
                    name = "$name (Imported)",
                    gender = gender,
                    avatarEmoji = avatarEmoji,
                    speakerEmbedding = speakerEmbedding,
                    caregiverContactsJson = caregiverContacts,
                    isActive = false
                )
                val newProfileId = voxaDao.insertProfile(newProfile)

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

                selectActiveProfile(newProfileId)
                addLogSystemEvent("log_profile_imported|$name|${intentsJsonArray.length()}")
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
                val active = voxaDao.getActiveProfile()
                val gender = active?.gender ?: "Male"
                if (testAudioPlayer == null) {
                    testAudioPlayer = com.example.voxa.utils.AudioPlayer(getApplication())
                }

                val cleanIntentName = intent.intentName.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}_]"), "_")
                val file = java.io.File(
                    getApplication<Application>().cacheDir,
                    "template_${intent.profileId}_${cleanIntentName}_0.pcm"
                )

                android.util.Log.d(
                    "VoxaViewModel",
                    "Library preview request: intent=${intent.intentName}, file=${file.absolutePath}, exists=${file.exists()}, gender=$gender"
                )

                val playedRecordedSample = testAudioPlayer?.playPcmFile(file) == true

                if (!playedRecordedSample) {
                    withContext(Dispatchers.Main) {
                        val localizedContext = com.example.voxa.utils.LocaleHelper.wrap(getApplication(), _appLanguage.value)
                        Toast.makeText(
                            getApplication(),
                            localizedContext.getString(R.string.toast_no_preview_sample),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    delay(150)
                    testAudioPlayer?.speakFallback(intent.outputPhrase, "ar", gender)
                }
            } catch (e: Exception) {
                android.util.Log.e("VoxaViewModel", "Failed to play library preview: ${e.message}", e)
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
        // Obsolete: updateCaregivers is used instead. Left as empty implementation to fulfill interface.
    }

    override fun testTtsVoice(gender: String) {
        if (testAudioPlayer == null) {
            testAudioPlayer = com.example.voxa.utils.AudioPlayer(getApplication())
        }
        val sampleText = "مرحبا بك في فوكسا"
        viewModelScope.launch {
            delay(150)
            testAudioPlayer?.speakFallback(sampleText, "ar", gender)
        }
    }
}
