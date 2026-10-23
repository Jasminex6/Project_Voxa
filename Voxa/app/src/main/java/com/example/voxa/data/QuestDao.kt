package com.example.voxa.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 🎯 QuestDao
 * Data Access Object for Quests and Rewards.
 */
@Dao
interface QuestDao {
    @Insert
    suspend fun insertTaskObservation(observation: TaskObservation)

    @Query("SELECT * FROM task_observations WHERE profileId = :profileId ORDER BY timestamp, id")
    suspend fun getTaskObservations(profileId: Long): List<TaskObservation>

    @Query("SELECT * FROM quests WHERE id = :questId")
    suspend fun getQuest(questId: Long): QuestEntity?

    @Transaction
    suspend fun createObservedQuest(quest: QuestEntity): Long {
        val id = insertQuest(quest)
        insertTaskObservation(TaskObservation(profileId = quest.profileId, taskId = id,
            fromStatus = null, toStatus = "pending", timestamp = quest.timestamp))
        return id
    }

    @Transaction
    suspend fun transitionObservedQuest(questId: Long, status: QuestStatus, note: String?, support: String = "notRecorded", at: Long = System.currentTimeMillis()): Boolean {
        require(support in listOf("independent", "prompted", "assisted", "notRecorded"))
        val quest = getQuest(questId) ?: return false
        if (quest.status == status) return false
        updateQuestStatus(questId, status, note)
        insertTaskObservation(TaskObservation(profileId = quest.profileId, taskId = questId,
            fromStatus = quest.status.name.lowercase(), toStatus = status.name.lowercase(),
            support = if (status == QuestStatus.APPROVED) support else "notRecorded", timestamp = at))
        return true
    }
    @Query("SELECT * FROM quests WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getQuestsForProfile(profileId: Long): List<QuestEntity>

    // ==========================================
    // 🎯 QUEST QUERIES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuest(quest: QuestEntity): Long

    @Update
    suspend fun updateQuest(quest: QuestEntity)

    @Delete
    suspend fun deleteQuest(quest: QuestEntity)

    @Query("SELECT * FROM quests WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getQuestsForProfileFlow(profileId: Long): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE profileId = :profileId AND status = :status ORDER BY timestamp DESC")
    fun getQuestsByStatusFlow(profileId: Long, status: QuestStatus): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE profileId = :profileId AND status IN (:statuses) ORDER BY timestamp DESC")
    fun getQuestsByStatusesFlow(profileId: Long, statuses: List<QuestStatus>): Flow<List<QuestEntity>>

    @Query("UPDATE quests SET status = :status, parentNote = :note WHERE id = :questId")
    suspend fun updateQuestStatus(questId: Long, status: QuestStatus, note: String?)

    @Query("DELETE FROM quests WHERE profileId = :profileId AND status = :status")
    suspend fun deleteQuestsByStatus(profileId: Long, status: QuestStatus)

    // ==========================================
    // 🎁 REWARD QUERIES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: RewardEntity): Long

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Delete
    suspend fun deleteReward(reward: RewardEntity)

    @Query("SELECT * FROM rewards WHERE profileId = :profileId")
    fun getRewardsForProfileFlow(profileId: Long): Flow<List<RewardEntity>>

    // ==========================================
    // 🛍️ PURCHASE QUERIES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchasedReward(purchasedReward: PurchasedRewardEntity)

    @Query("SELECT * FROM purchased_rewards WHERE profileId = :profileId AND isFulfilled = :isFulfilled ORDER BY timestamp DESC")
    fun getPurchasedRewardsFlow(profileId: Long, isFulfilled: Boolean): Flow<List<PurchasedRewardEntity>>

    @Query("UPDATE purchased_rewards SET isFulfilled = 1 WHERE id = :purchaseId")
    suspend fun markRewardFulfilled(purchaseId: Long)
}
