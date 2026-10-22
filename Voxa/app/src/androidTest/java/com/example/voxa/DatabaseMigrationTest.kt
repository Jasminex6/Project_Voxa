package com.example.voxa

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.voxa.data.VoxaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun localEnrollmentsSurviveVersionEightUpgrade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // An in-memory fixture: never opens or changes the family's database or preferences.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE enrolled_intents (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, profileId INTEGER NOT NULL, intentName TEXT NOT NULL, outputPhrase TEXT NOT NULL, audioAssetPath TEXT NOT NULL, oodThreshold REAL NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO enrolled_intents VALUES (7, 2, 'Water', 'Water please', 'local.wav', 0.85)")
            VoxaDatabase.MIGRATION_8_9.migrate(db)
            db.query("SELECT id, profileId, audioAssetPath, isImported FROM enrolled_intents").use {
                assertTrue(it.moveToFirst())
                assertEquals(7L, it.getLong(0))
                assertEquals(2L, it.getLong(1))
                assertEquals("local.wav", it.getString(2))
                assertEquals(0, it.getInt(3))
                assertEquals(1, it.count)
            }
        } finally {
            helper.close()
        }
    }
}
