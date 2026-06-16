package com.example.voxa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "acoustic_templates",
    foreignKeys = [ForeignKey(
        entity = EnrolledIntent::class,
        parentColumns = ["id"],
        childColumns = ["intentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AcousticTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intentId: Long, // Links to EnrolledIntent
    val templateFeatures: String // Stored as a serialized string (JSON list of floats)
)
