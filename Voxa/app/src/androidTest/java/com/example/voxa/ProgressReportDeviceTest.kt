package com.example.voxa

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.data.*
import com.example.voxa.data.license.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProgressReportDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun renderBilingualReviewAndExportAllowlistedFixture() {
        val report = ReportSamples.review()
        val json = ProgressReport.gson.toJson(report)
        assertFalse(json.contains("PRIVATE")); assertFalse(json.contains("word")); assertFalse(json.contains("parentNote"))
        assertTrue(json.toByteArray().size < 65536)
        val dir = context.getExternalFilesDir(null)!!
        File(dir, "progress-review-example.json").writeText(json)
        File(dir, "progress-review-highlights.json").writeText(ProgressReport.gson.toJson(mapOf("en" to ReviewHighlights.build(report,false),"ar" to ReviewHighlights.build(report,true))))
        for (language in listOf("en", "ar")) {
            val file = File(dir, "progress-review-$language.pdf")
            ProgressPdf.write(file, report, language == "ar", sample = true)
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertEquals(1, renderer.pageCount)
                repeat(renderer.pageCount) { index -> renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(dir, "progress-review-$language-${index + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                } }
            }
        }
    }

    @Test fun largeAndEmptyReportsStayOnOnePageWithoutClipping() {
        val sample = ReportSamples.review()
        val target = sample.practice.targets.first()
        val large = sample.copy(practice = sample.practice.copy(targets = (1..41).map { target.copy(code = "personal_$it") }))
        for (report in listOf(sample.copy(practice = sample.practice.copy(targets = emptyList())), large)) for (ar in listOf(false, true)) {
            val file = File(context.cacheDir, "report-layout-check.pdf")
            ProgressPdf.write(file, report, ar)
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(1,it.pageCount) }
            file.delete()
        }
    }

    @Test fun taskTransitionsAreDurableAndDuplicateApprovalDoesNotCreateEvidence() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, VoxaDatabase::class.java).build()
        try {
            val profile = db.voxaDao().insertProfile(ChildProfile(name = "Synthetic", gender = "Male"))
            val dao = db.questDao()
            val id = dao.createObservedQuest(QuestEntity(profileId = profile, title = "PRIVATE", description = "PRIVATE", icon = "", points = 5))
            assertTrue(dao.transitionObservedQuest(id, QuestStatus.SUBMITTED, null))
            assertTrue(dao.transitionObservedQuest(id, QuestStatus.APPROVED, "PRIVATE", "prompted"))
            assertFalse(dao.transitionObservedQuest(id, QuestStatus.APPROVED, null, "independent"))
            dao.deleteQuestsByStatus(profile, QuestStatus.APPROVED)
            assertEquals(3, dao.getTaskObservations(profile).size)
            assertEquals("prompted", dao.getTaskObservations(profile).last().support)
            assertTrue(dao.getQuestsForProfile(profile).isEmpty())
        } finally { db.close() }
    }

    @Test fun migrationPreservesExistingProfilesVocabularyHistoryAndTasks() = runBlocking {
        val name = "report-migration-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, VoxaDatabase::class.java, name).build()
        try {
            val id = db.voxaDao().insertProfile(ChildProfile(name = "Keep me", gender = "Female"))
            db.voxaDao().insertIntent(EnrolledIntent(profileId = id, intentName = "Water", outputPhrase = "Water", audioAssetPath = "local.wav"))
            db.voxaDao().insertPracticeStats(PracticeStats(profileId = id, word = "Water", score = 80, stars = 3))
            db.questDao().insertQuest(QuestEntity(profileId = id, title = "Keep task", description = "", icon = "", points = 1))
            db.close()
            // Reconstruct the actual v9 schema: all old tables survive, only the new observation tables are absent.
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { old ->
                old.execSQL("DROP TABLE practice_observations"); old.execSQL("DROP TABLE task_observations"); old.version = 9
            }
            db = Room.databaseBuilder(context, VoxaDatabase::class.java, name).addMigrations(VoxaDatabase.MIGRATION_9_10).build()
            assertEquals("Keep me", db.voxaDao().getProfileById(id)?.name)
            assertEquals(1, db.voxaDao().getIntentsForProfile(id).size)
            assertEquals(1, db.voxaDao().getPracticeStatsForProfile(id).size)
            assertEquals(1, db.questDao().getQuestsForProfile(id).size)
            assertTrue(db.voxaDao().getPracticeObservations(id).isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
