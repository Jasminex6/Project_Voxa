package com.example.voxa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 🎁 RewardEntity
 * Represents a real-world reward that can be redeemed using points.
 */
@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val title: String,
    val cost: Int,
    val icon: String
)
