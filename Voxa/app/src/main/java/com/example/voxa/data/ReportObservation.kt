package com.example.voxa.data

import androidx.room.*

enum class PracticeOutcome { SCORED, UNRECOGNIZED, NO_SPEECH, TECHNICAL }

@Entity(tableName = "practice_observations", foreignKeys = [ForeignKey(
    entity = ChildProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["profileId", "timestamp"])])
data class PracticeObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val targetCode: String,
    val language: String,
    val outcome: PracticeOutcome,
    val matched: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_observations", foreignKeys = [ForeignKey(
    entity = ChildProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["profileId", "timestamp"])])
data class TaskObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val taskId: Long,
    val fromStatus: String?,
    val toStatus: String,
    val support: String = "notRecorded",
    val timestamp: Long = System.currentTimeMillis()
)
