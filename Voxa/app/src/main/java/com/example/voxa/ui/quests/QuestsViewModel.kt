package com.example.voxa.ui.quests

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxa.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 🧠 QuestsViewModel
 * Manages the state for the Quests and Rewards feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VoxaDatabase.getDatabase(application)
    private val voxaDao = db.voxaDao()
    private val questDao = db.questDao()

    // Reactively observe the active profile from VoxaDao
    val activeProfile: StateFlow<ChildProfile?> = voxaDao.getAllProfilesFlow()
        .map { profiles -> profiles.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Child sees only PENDING quests
    val childActiveQuests: StateFlow<List<QuestEntity>> = activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> questDao.getQuestsByStatusFlow(profile.id, QuestStatus.PENDING) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Parent sees PENDING, SUBMITTED, and UNABLE quests as actionable
    val parentActionableQuests: StateFlow<List<QuestEntity>> = activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> questDao.getQuestsByStatusesFlow(profile.id, listOf(QuestStatus.PENDING, QuestStatus.SUBMITTED, QuestStatus.UNABLE)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Automatically fetch approved quests for the active profile
    val approvedQuests: StateFlow<List<QuestEntity>> = activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> questDao.getQuestsByStatusFlow(profile.id, QuestStatus.APPROVED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Automatically fetch rewards for the active profile
    val rewards: StateFlow<List<RewardEntity>> = activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> questDao.getRewardsForProfileFlow(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unfulfilled purchased rewards for the parent to track
    val unfulfilledPurchases: StateFlow<List<PurchasedRewardEntity>> = activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> questDao.getPurchasedRewardsFlow(profile.id, isFulfilled = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _childPoints = MutableStateFlow(0)
    val childPoints: StateFlow<Int> = _childPoints.asStateFlow()

    // Setup state
    private val _isPinSetup = MutableStateFlow(QuestPrefs.isPinSetup(application))
    val isPinSetup: StateFlow<Boolean> = _isPinSetup.asStateFlow()

    private val _isParentAuthenticated = MutableStateFlow(false)
    val isParentAuthenticated: StateFlow<Boolean> = _isParentAuthenticated.asStateFlow()

    init {
        // Keep child points synchronized when profile changes
        viewModelScope.launch {
            activeProfile.collect { profile ->
                if (profile != null) {
                    _childPoints.value = QuestPrefs.getChildPoints(getApplication(), profile.id)
                } else {
                    _childPoints.value = 0
                }
            }
        }
    }

    fun setupPin(pin: String) {
        QuestPrefs.setCaregiverPin(getApplication(), pin)
        _isPinSetup.value = true
        _isParentAuthenticated.value = true // Automatically authenticate after setup
    }

    fun resetPin() {
        QuestPrefs.clearCaregiverPin(getApplication())
        _isPinSetup.value = false
        _isParentAuthenticated.value = false
    }

    fun authenticateParent(pin: String): Boolean {
        val isValid = QuestPrefs.validatePin(getApplication(), pin)
        if (isValid) {
            _isParentAuthenticated.value = true
        }
        return isValid
    }

    fun logoutParent() {
        _isParentAuthenticated.value = false
    }

    fun createQuest(title: String, description: String, icon: String, points: Int) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            questDao.insertQuest(
                QuestEntity(
                    profileId = profileId,
                    title = title,
                    description = description,
                    icon = icon,
                    points = points,
                    status = QuestStatus.PENDING
                )
            )
        }
    }
    
    fun markQuestSubmitted(quest: QuestEntity) {
        viewModelScope.launch {
            questDao.updateQuestStatus(quest.id, QuestStatus.SUBMITTED, null)
        }
    }
    
    fun markQuestUnable(quest: QuestEntity) {
        viewModelScope.launch {
            questDao.updateQuestStatus(quest.id, QuestStatus.UNABLE, null)
        }
    }

    fun approveQuest(quest: QuestEntity, note: String?) {
        viewModelScope.launch {
            questDao.updateQuestStatus(quest.id, QuestStatus.APPROVED, note)
            // Add points to child's wallet
            QuestPrefs.addPoints(getApplication(), quest.profileId, quest.points)
            // Update local state instantly
            _childPoints.value = QuestPrefs.getChildPoints(getApplication(), quest.profileId)
        }
    }

    fun rejectQuest(quest: QuestEntity, note: String?) {
        viewModelScope.launch {
            questDao.updateQuestStatus(quest.id, QuestStatus.REJECTED, note)
        }
    }

    fun deleteQuest(quest: QuestEntity) {
        viewModelScope.launch {
            questDao.deleteQuest(quest)
        }
    }

    fun createReward(title: String, cost: Int, icon: String) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            questDao.insertReward(
                RewardEntity(
                    profileId = profileId,
                    title = title,
                    cost = cost,
                    icon = icon
                )
            )
        }
    }

    fun deleteReward(reward: RewardEntity) {
        viewModelScope.launch {
            questDao.deleteReward(reward)
        }
    }

    fun redeemReward(reward: RewardEntity): Boolean {
        val profileId = activeProfile.value?.id ?: return false
        val success = QuestPrefs.deductPoints(getApplication(), profileId, reward.cost)
        if (success) {
            // Record the purchase transaction
            viewModelScope.launch {
                questDao.insertPurchasedReward(
                    PurchasedRewardEntity(
                        profileId = profileId,
                        rewardId = reward.id,
                        title = reward.title,
                        cost = reward.cost,
                        icon = reward.icon
                    )
                )
            }
            // Update local state instantly
            _childPoints.value = QuestPrefs.getChildPoints(getApplication(), profileId)
        }
        return success
    }

    fun fulfillPurchasedReward(purchase: PurchasedRewardEntity) {
        viewModelScope.launch {
            questDao.markRewardFulfilled(purchase.id)
        }
    }
}
