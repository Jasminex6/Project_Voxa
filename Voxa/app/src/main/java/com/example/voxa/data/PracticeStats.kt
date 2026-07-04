package com.example.voxa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "practice_stats",
    foreignKeys = [
        ForeignKey(
            entity = ChildProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PracticeStats(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val word: String,
    val score: Int, // 0 to 100
    val stars: Int, // 1 to 3
    val timestamp: Long = System.currentTimeMillis()
)
