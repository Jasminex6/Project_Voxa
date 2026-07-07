package com.example.voxa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 🛍️ PurchasedRewardEntity
 * Represents a transaction where a child has spent points to buy a reward.
 * The parent must eventually fulfill this reward in the real world.
 */
@Entity(tableName = "purchased_rewards")
data class PurchasedRewardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val rewardId: Long,
    val title: String,
    val cost: Int,
    val icon: String,
    val isFulfilled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
