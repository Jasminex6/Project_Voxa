package com.example.voxa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "enrolled_intents",
    foreignKeys = [ForeignKey(
        entity = ChildProfile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class EnrolledIntent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long, // Links to ChildProfile
    val intentName: String, // e.g. "Water", "Help"
    val outputPhrase: String, // e.g. "أنا عايز مية"
    val audioAssetPath: String // e.g. "water.mp3" (gender-neutral filename, resolved dynamically at playback)
)