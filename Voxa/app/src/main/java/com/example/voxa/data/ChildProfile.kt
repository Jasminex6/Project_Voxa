package com.example.voxa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_profiles")

data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String, // "Male" or "Female" (swaps child-like voice packs)
    val isActive: Boolean = false
)