package com.example.voxa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 🎮 PracticeStats
 *
 * Database entity storing the result of a single pronunciation practice attempt.
 * Each row captures the child's DTW distance against a reference template,
 * the computed percentage score, and the star rating for gamified feedback.
 */
@Entity(
    tableName = "practice_stats",
    foreignKeys = [ForeignKey(
        entity = ChildProfile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PracticeStats(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,          // Links to ChildProfile
    val word: String,             // The practice word attempted (e.g. "Water")
    val dtwDistance: Float,       // Raw DTW distance from scoring
    val score: Int,               // Computed percentage: 100 / 80 / 60 / 0
    val stars: Int,               // Star rating: 3 / 2 / 1 / 0
    val timestamp: Long = System.currentTimeMillis()
)
