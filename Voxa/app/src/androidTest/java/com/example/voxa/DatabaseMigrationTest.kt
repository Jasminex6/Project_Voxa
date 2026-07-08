package com.example.voxa

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.VoxaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private lateinit var db: VoxaDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Build the version 4 database in-memory to verify schema initialization builds correctly without crashes
        db = Room.inMemoryDatabaseBuilder(context, VoxaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testDatabaseSchemaAndCascadingDeletions() = runBlocking {
        val dao = db.voxaDao()

        // 1. Verify Profile creation with caregiverContactsJson column
        val profile = ChildProfile(
            name = "Test Child",
            gender = "Female",
            isActive = true,
            avatarEmoji = "👧",
            caregiverContactsJson = "[{\"name\":\"Mom\",\"phone\":\"123\"}]"
        )
        val profileId = dao.insertProfile(profile)
        
        val loadedProfile = dao.getActiveProfile()
        assertNotNull(loadedProfile)
        assertEquals("Test Child", loadedProfile?.name)
        assertEquals("Female", loadedProfile?.gender)
        assertEquals("[{\"name\":\"Mom\",\"phone\":\"123\"}]", loadedProfile?.caregiverContactsJson)

        // 2. Verify EnrolledIntent creation linked to this Profile
        val intent = EnrolledIntent(
            profileId = profileId,
            intentName = "Water",
            outputPhrase = "ميّه",
            audioAssetPath = "water.mp3"
        )
        val intentId = dao.insertIntent(intent)
        
        val loadedIntents = dao.getIntentsForProfile(profileId)
        assertEquals(1, loadedIntents.size)
        assertEquals("Water", loadedIntents[0].intentName)

        // 3. Verify Cascading Deletions: Delete the ChildProfile and assert Room automatically deletes associated EnrolledIntents
        dao.deleteProfile(loadedProfile!!)
        
        val postDeleteProfile = dao.getActiveProfile()
        assertNull(postDeleteProfile)
        
        val postDeleteIntents = dao.getIntentsForProfile(profileId)
        assertEquals(0, postDeleteIntents.size)
    }
}
