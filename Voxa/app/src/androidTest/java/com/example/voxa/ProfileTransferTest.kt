package com.example.voxa

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.data.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic in-memory databases only; does not touch the family's profiles or license. */
@RunWith(AndroidJUnit4::class)
class ProfileTransferTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, VoxaDatabase::class.java
    ).build()

    @Test fun selectedInactiveChildTransfersWithItsOwnRecognitionTemplates() = runBlocking {
        val sender = database()
        val receiver = database()
        try {
            val dao = sender.voxaDao()
            val active = dao.insertProfile(ChildProfile(name = "Active", gender = "Male", isActive = true))
            val selected = dao.insertProfile(ChildProfile(name = "Selected", gender = "Female", avatarEmoji = "👧"))
            val otherIntent = dao.insertIntent(EnrolledIntent(profileId = active,
                intentName = "Other word", outputPhrase = "Wrong library", audioAssetPath = "other.wav"))
            val selectedIntent = dao.insertIntent(EnrolledIntent(profileId = selected,
                intentName = "Water", outputPhrase = "مياه", audioAssetPath = "water.wav"))
            dao.insertTemplate(AcousticTemplate(intentId = otherIntent, templateFeatures = "[9,9]"))
            val mfcc = (1..80).joinToString(prefix = "[", postfix = "]") { (it / 100f).toString() }
            dao.insertTemplate(AcousticTemplate(intentId = selectedIntent, templateFeatures = mfcc))
            val exported = dao.exportP2PProfile(selected, "Test caregiver", "", "Parent")
            // Exercise the exact DTO serialization used by the existing Nearby protocol.
            val json = Gson().toJson(exported)
            val imported = Gson().fromJson(json, P2PSharingDto::class.java)
            val id = receiver.voxaDao().insertP2PProfile(imported)
            assertEquals("Selected (Shared)", receiver.voxaDao().getProfileById(id)?.name)
            val intents = receiver.voxaDao().getIntentsForProfile(id)
            assertEquals(1, intents.size)
            assertEquals("Water", intents.single().intentName)
            assertTrue(intents.single().isImported)
            assertEquals(mfcc, receiver.voxaDao().getTemplatesForIntent(intents.single().id).single().templateFeatures)
            assertEquals(0, receiver.voxaDao().customIntentCount(id))
            assertFalse(json.contains("Wrong library"))
            assertEquals(active, dao.getActiveProfile()?.id)
        } finally { sender.close(); receiver.close() }
    }

    @Test fun failedImportRollsBackProfileAndAlreadyInsertedWords() = runBlocking {
        val db = database()
        try {
            val dao = db.voxaDao()
            val existing = dao.insertProfile(ChildProfile(name = "Keep me", gender = "Male", isActive = true))
            val dto = P2PSharingDto("Incoming", "Female", "👧", null, "", "", "",
                listOf(IntentWithTemplates("Water", "Water", "water.wav", .85f, listOf("[1,2]")),
                    IntentWithTemplates("", "Invalid", "bad.wav", .85f, emptyList())))
            try {
                dao.insertP2PProfile(dto)
                fail("Incomplete transfer must fail")
            } catch (_: IllegalArgumentException) { }
            assertEquals(1, dao.profileCount())
            assertEquals(existing, dao.getActiveProfile()?.id)
            assertTrue(dao.getIntentsForProfile(existing).isEmpty())
        } finally { db.close() }
    }

    @Test fun deletedProfileCannotExportAnotherChildsData() = runBlocking {
        val db = database()
        try {
            db.voxaDao().insertProfile(ChildProfile(name = "Keep me", gender = "Male", isActive = true))
            try {
                db.voxaDao().exportP2PProfile(999, "", "", "")
                fail("Missing selected profile must fail")
            } catch (_: IllegalStateException) { }
        } finally { db.close() }
    }
}
