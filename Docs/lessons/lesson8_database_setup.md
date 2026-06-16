# 🎓 Voxa Study Log: Lesson 8 — Room DAOs, Database Class & Gradle Setup

Welcome to Lesson 8! Now that we have defined our three database entities (`ChildProfile`, `EnrolledIntent`, and `AcousticTemplate`) in our `data` package, we need to implement the database compiler infrastructure and write the queries.

In this lesson, we will:
1.  **Configure Room Dependencies:** Register the Room library in Android Studio's version catalog and Gradle files.
2.  **Create the DAOs (Data Access Objects):** Write the interface queries to read and write profiles and templates.
3.  **Create the Database Class (`VoxaDatabase.kt`):** Setup the abstract database class that manages database initialization.

---

## 💬 Q&A: Understanding Dependency Management

### ❓ Question 1: What is a `.toml` file?
**💡 Mentor Explanation:** **TOML** stands for **T**om's **O**bvious **M**inimal **L**anguage. It is a configuration file format designed to be simple for humans to read and write.
*   **Why Android uses it:** In modern Gradle, we use `libs.versions.toml` as a **Version Catalog**.
*   Instead of hardcoding library version strings inside multiple separate module files (like `app/build.gradle.kts` and `dsp-engine/build.gradle.kts`), we list them once in this central `.toml` file.
*   Your Gradle modules then reference this catalog (e.g., `libs.androidx.room.runtime`). If you need to upgrade Room later, you change the version number **in just one place** in the `.toml` file, preventing version mismatch issues across your team.

---

## 🛠️ Step 1: Configure Gradle Dependencies

Modern Android apps use **Version Catalogs** (`libs.versions.toml`) to store library versions in a central place. 

### A. Update the version catalog
Open [libs.versions.toml](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/gradle/libs.versions.toml) and add the following lines to their respective sections:

```toml
[versions]
# Add the Room library version (using latest stable 2.6.x)
room = "2.6.1"

[libraries]
# Declare the Room runtime, KTX (Kotlin extensions), and compiler libraries
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

### B. Update the App's build file
Open [build.gradle.kts](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/build.gradle.kts) and add the dependencies to the `dependencies` block:

```kotlin
dependencies {
    // ... other existing implementations

    // Room Database Libraries
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // KSP/annotation processor (generates the actual SQLite database helper code during build)
    annotationProcessor(libs.androidx.room.compiler)
}
```

---

## 📋 Step 2: Creating the DAOs (Data Access Objects)

We need interfaces to read and insert data. Create a new Kotlin interface file named `VoxaDao.kt` at:
*   **File Location:** `Voxa/app/src/main/java/com/example/voxa/data/VoxaDao.kt` (or absolute: [VoxaDao.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/data/VoxaDao.kt))

```kotlin
package com.example.voxa.data

import androidx.room.*

@Dao
interface VoxaDao {

    // ── Profile Queries ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ChildProfile): Long

    @Query("SELECT * FROM child_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ChildProfile?

    // ── Intent Queries ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntent(intent: EnrolledIntent): Long

    @Transaction // Combines multiple operations. Loads intents + all templates cleanly
    @Query("SELECT * FROM enrolled_intents WHERE profileId = :profileId")
    suspend fun getIntentsForProfile(profileId: Long): List<EnrolledIntent>

    // ── Template Queries ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: AcousticTemplate)

    @Query("SELECT * FROM acoustic_templates WHERE intentId = :intentId")
    suspend fun getTemplatesForIntent(intentId: Long): List<AcousticTemplate>
}
```

---

## 🏛️ Step 3: Creating the Database Class

The database class acts as the bridge connecting your tables (Entities) to your DAOs. Create a file named `VoxaDatabase.kt` at:
*   **File Location:** `Voxa/app/src/main/java/com/example/voxa/data/VoxaDatabase.kt` (or absolute: [VoxaDatabase.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/data/VoxaDatabase.kt))

```kotlin
package com.example.voxa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChildProfile::class, EnrolledIntent::class, AcousticTemplate::class],
    version = 1,
    exportSchema = false
)
abstract class VoxaDatabase : RoomDatabase() {

    // Expose our DAO functions to the application
    abstract fun voxaDao(): VoxaDao

    companion object {
        @Volatile
        private var INSTANCE: VoxaDatabase? = null

        /**
         * Singleton pattern: returns the active database instance.
         * Ensures only one database connection is open across the entire app.
         */
        fun getDatabase(context: Context): VoxaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoxaDatabase::class.java,
                    "voxa_database"
                )
                .fallbackToDestructiveMigration() // Destroys database and recreates if version changes during development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

---

## 🔍 Understanding the Code Annotations

*   **`@Transaction`:** Tells Room to run multiple database reads as a single unit. Because `EnrolledIntent` has a child relationship with `AcousticTemplate`, this prevents partial reads if the data changes mid-query.
*   **`@Volatile` & `synchronized(this)`:** This is **thread safety** boilerplate. In an audio app, the background audio recording thread will be reading templates from the database at the exact same time your Compose UI thread is saving them. This prevents race conditions.
*   **`fallbackToDestructiveMigration()`:** Vital for early hackathon prototyping. If you change a database column in your entities during development, the app will normally crash on startup due to a "schema mismatch". This line tells Room: *"If the schema changes, just wipe the database file and start fresh. No crash."*
