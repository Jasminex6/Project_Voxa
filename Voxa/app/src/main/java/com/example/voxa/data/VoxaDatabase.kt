package com.example.voxa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 🏛️ VoxaDatabase
 *
 * This class serves as the master database controller. It represents the actual SQLite database
 * configuration file on the Android device's file storage.
 *
 * - @Database: Marks this class as a Room Database holder.
 * - entities: Registers our three table classes (ChildProfile, EnrolledIntent, AcousticTemplate).
 * - version = 1: The schema version. If we add or change tables in the future, we must increment this.
 * - exportSchema = false: Prevents Room from exporting the DB schema design to a JSON file during builds.
 */
@Database(
    entities = [ChildProfile::class, EnrolledIntent::class, AcousticTemplate::class],
    version = 1,
    exportSchema = false
)
abstract class VoxaDatabase : RoomDatabase() {

    /**
     * Exposes our DAO functions. 
     * We leave this abstract because Room will write the code to implement this function.
     */
    abstract fun voxaDao(): VoxaDao

    companion object {
        /**
         * @Volatile: This is a concurrency safety keyword.
         *
         * In an audio app like Voxa, we have two different threads working:
         * 1. The Main thread (drawing the Compose UI screens).
         * 2. The Background thread (recording microphone data and checking database templates).
         *
         * Volatile ensures that any changes made to this INSTANCE variable (like initializing it)
         * are immediately visible to all threads, preventing thread caches from going out of sync.
         */
        @Volatile
        private var INSTANCE: VoxaDatabase? = null

        /**
         * Singleton pattern: returns the active database instance.
         *
         * Opening a database connection is very expensive in CPU and memory. We must ensure 
         * that only ONE connection is open across the entire app.
         */
        fun getDatabase(context: Context): VoxaDatabase {
            // If the database instance already exists, return it immediately to save CPU cycles.
            return INSTANCE ?: synchronized(this) {
                // synchronized(this) locks this block of code. 
                // If two threads try to call getDatabase() at the exact same millisecond,
                // it forces them to wait in line. This prevents creating two duplicate database instances.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoxaDatabase::class.java,
                    "voxa_database"
                )
                /**
                 * fallbackToDestructiveMigration:
                 * During early hackathon development, you might change a column in your tables. 
                 * Normally, Room will crash on startup if your entity classes don't match the 
                 * SQL file schema on the phone. This line prevents crashes by saying: 
                 * "If the schema changes during development, just delete the old database file 
                 * and create a new clean one."
                 */
                .fallbackToDestructiveMigration()
                .build()
                
                // Cache the newly created instance
                INSTANCE = instance
                instance
            }
        }
    }
}
