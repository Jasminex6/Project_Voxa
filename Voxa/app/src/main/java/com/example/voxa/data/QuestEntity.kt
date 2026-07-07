package com.example.voxa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 🎯 QuestEntity
 * Represents a single real-world task assigned to a child profile.
 */
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val title: String,
    val description: String,
    val icon: String, // Emoji or icon identifier
    val points: Int,
    val status: QuestStatus = QuestStatus.PENDING,
    val parentNote: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class QuestStatus {
    PENDING,
    SUBMITTED,
    UNABLE,
    APPROVED,
    REJECTED
}
