package com.example.voxa.data

import androidx.room.*

/**
 * 🏛️ VoxaDao (Data Access Object)
 *
 * An interface marked with @Dao is the "Menu" of database operations. 
 * We do not write the database query code ourselves; we only declare the function signatures 
 * and write SQL queries. At build time, Room generates the concrete code to run these queries.
 */
@Dao
interface VoxaDao {

    // ==========================================
    // 👤 PROFILE QUERIES
    // ==========================================

    /**
     * Inserts a new child profile into the database.
     *
     * - @Insert: Tells Room this function writes a new row.
     * - onConflict = OnConflictStrategy.REPLACE: If a profile with the same primary key ID
     *   already exists, overwrite it.
     * - suspend: Tells Kotlin this is a coroutine function. It will run in the background 
     *   without freezing the main UI thread.
     * - @return Long: Returns the auto-generated row ID of the newly inserted profile.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ChildProfile): Long

    /**
     * Retrieves the currently active child profile.
     *
     * - @Query: Runs custom raw SQL.
     * - isActive = 1 LIMIT 1: Finds the profile marked active. LIMIT 1 ensures we get at most one.
     * - @return ChildProfile?: Can return null if no profile has been created yet.
     */
    @Query("SELECT * FROM child_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ChildProfile?

    // ==========================================
    // 🎙️ INTENT QUERIES
    // ==========================================

    /**
     * Inserts a vocal intent mapping (e.g., "Water" -> "أنا عايز مية").
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntent(intent: EnrolledIntent): Long

    /**
     * Retrieves all intents mapped to a specific child profile.
     *
     * - @Transaction: Runs the query as a single atomic operation. This is critical because 
     *   we will link this query to templates. If the database updates mid-query, this prevents
     *   loading partial or corrupt entries.
     * - :profileId: The colon syntax inserts the function's parameter directly into the SQL query.
     */
    @Transaction
    @Query("SELECT * FROM enrolled_intents WHERE profileId = :profileId")
    suspend fun getIntentsForProfile(profileId: Long): List<EnrolledIntent>

    // ==========================================
    // 📈 ACOUSTIC TEMPLATE QUERIES
    // ==========================================

    /**
     * Saves an acoustic training template (the 40-D MFCC float vectors serialized as a string).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: AcousticTemplate)

    /**
     * Retrieves the training template sound prints for a specific intent.
     */
    @Query("SELECT * FROM acoustic_templates WHERE intentId = :intentId")
    suspend fun getTemplatesForIntent(intentId: Long): List<AcousticTemplate>

    // ==========================================
    // ➕ ADDITIONAL ACTIONS FOR COMPOSE UI SHELL
    // ==========================================

    /**
     * Retrieves all child profiles to display in the profile selector.
     * Returns a Flow so that the UI updates automatically when profiles are added or modified.
     */
    @Query("SELECT * FROM child_profiles")
    fun getAllProfilesFlow(): kotlinx.coroutines.flow.Flow<List<ChildProfile>>

    /**
     * Retrieves all intents for a child as a Flow for real-time UI updates.
     */
    @Query("SELECT * FROM enrolled_intents WHERE profileId = :profileId")
    fun getIntentsForProfileFlow(profileId: Long): kotlinx.coroutines.flow.Flow<List<EnrolledIntent>>

    /**
     * Deletes an enrolled intent and cascades to its training templates.
     */
    @Delete
    suspend fun deleteIntent(intent: EnrolledIntent)

    /**
     * Clears the active flag from all profiles.
     */
    @Query("UPDATE child_profiles SET isActive = 0")
    suspend fun clearActiveProfiles()

    /**
     * Sets a specific profile as active.
     */
    @Query("UPDATE child_profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun setActiveProfile(profileId: Long)

    /**
     * Switches the active child profile atomically.
     */
    @Transaction
    suspend fun selectActiveProfile(profileId: Long) {
        clearActiveProfiles()
        setActiveProfile(profileId)
    }

    @Update
    suspend fun updateProfile(profile: ChildProfile)

    @Delete
    suspend fun deleteProfile(profile: ChildProfile)
}

