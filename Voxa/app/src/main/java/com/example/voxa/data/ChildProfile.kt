package com.example.voxa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_profiles")

data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String, // "Male" or "Female" (swaps child-like voice profiles)
    val isActive: Boolean = false,
    val avatarEmoji: String = "👦",
    val speakerEmbedding: String? = null, // JSON-serialized FloatArray(192) — ECAPA-TDNN voice fingerprint
    val caregiverPhone1: String = "",
    val caregiverPhone2: String = "",
    val caregiverPhone3: String = ""
)