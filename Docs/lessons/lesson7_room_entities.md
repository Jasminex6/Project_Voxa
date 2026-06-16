# 🎓 Voxa Study Log: Lesson 7 — Designing Room DB Tables

Welcome to Lesson 7! Now that our app can run continuously in the background and has permission to listen to the microphone, we need a place to store our child profiles and training vocalizations.

If a caregiver configures a child's name, records 7 training vocalizations for "Water", and closes the app, we cannot let that data vanish from RAM. We must save it to a persistent local SQL database. On Android, Google's official library for this is **Room**.

---

## 🏛️ The Three Pillars of Room

Room is an abstraction layer over raw SQLite. Instead of writing raw database commands by hand, Room uses three components:

1.  **Entities (Tables):** Kotlin data classes marked with `@Entity`. These represent the columns and rows of your database.
2.  **DAOs (Data Access Objects):** Interfaces marked with `@Dao`. This is where we write the functions to load, save, or delete data (e.g. `insertIntent()`, `getAllIntents()`).
3.  **Database:** A class marked with `@Database` that coordinates the tables and connections.

---

## 📐 Designing the Voxa Tables

For a personalized speech translator, we need three tables:
1.  `ChildProfile`: Stores who is using the app and their voice print.
2.  `EnrolledIntent`: Maps a specific sound meaning (like "Water") to its output phrase (like "أنا عايز ميّه").
3.  `AcousticTemplate`: Stores the actual mathematical audio templates (extracted by Developer A) that we will compare incoming audio against.

Here is how their relationships are mapped out:

```
┌─────────────────┐
│  ChildProfile   │
└────────┬────────┘
         │ (1-to-many relationship)
         ▼
┌─────────────────┐
│ EnrolledIntent  │
└────────┬────────┘
         │ (1-to-many relationship)
         ▼
┌─────────────────┐
│AcousticTemplate │
└─────────────────┘
```

---

## 💻 The Room Entity Code

To implement these tables, we need to create a new package directory named `data` inside our main Kotlin package:
*   **Directory Location:** `Voxa/app/src/main/java/com/example/voxa/data/` (or absolute: `d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/data/`)

Inside this folder, we will create three separate Kotlin files for our entities:

| File Name | Absolute File Path | Description |
| :--- | :--- | :--- |
| **`ChildProfile.kt`** | [ChildProfile.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/data/ChildProfile.kt) | Stores active profiles and settings. |
| **`EnrolledIntent.kt`** | [EnrolledIntent.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/data/EnrolledIntent.kt) | Maps vocalization meanings to text and audio paths. |
| **`AcousticTemplate.kt`** | [AcousticTemplate.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/data/AcousticTemplate.kt) | Stores the 7 training voice templates per intent. |

Here is how we define each of these files:

### 1. ChildProfile Entity
This stores child-specific configurations and their enrolled speaker verification embedding.

```kotlin
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
```

### 2. EnrolledIntent Entity
This maps an intent name to the Egyptian Arabic phrase and the corresponding local audio clip file. It is linked to the `ChildProfile` using a **Foreign Key**.

```kotlin
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
        onDelete = ForeignKey.CASCADE // If we delete the child, delete their templates!
    )]
)
data class EnrolledIntent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long, // Links to ChildProfile
    val intentName: String, // e.g. "Water", "Help"
    val outputPhrase: String, // e.g. "أنا عايز مية"
    val audioAssetPath: String // e.g. "audio/masc/water.mp3"
)
```

### 3. AcousticTemplate Entity
This stores the actual training feature templates extracted by Developer A's MFCC feature extractor.

#### 💡 Deep Dive: Why is this a separate table?
An **acoustic template** is a mathematical footprint of a sound—a matrix of floating-point numbers representing vocal characteristics over time. 

We separate it from `EnrolledIntent` because:
*   **One-to-Many Relationship:** A child never vocalizes the exact same sound twice. Sometimes they speak faster or slower. To absorb this inconsistency, we have caregivers record **7 distinct training samples** per intent.
*   Rather than stuffing lists of arrays into a single row, database design rules dictate we create a separate table. A single `EnrolledIntent` (e.g. ID `5` for "Water") has **7 corresponding rows** in `AcousticTemplate` linked by the foreign key `intentId = 5`.

#### 🧬 What is serialization?
SQLite only understands simple types (Strings, Integers, Real numbers, and raw BLOBS). It cannot save a native programming variable like a Kotlin `FloatArray` or custom matrix list directly.

We must **serialize** it:
1.  **Serialization (Save):** Convert the raw floating-point numbers into a structured String (like a JSON array: `"[0.12, -0.45, 0.88]"`) and save it in a database cell.
2.  **Deserialization (Load):** When the background service starts up, it reads that String from SQLite and parses it back into a native memory representation (`FloatArray`), so Developer A's matching engine can execute matrix calculations.

```kotlin
package com.example.voxa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "acoustic_templates",
    foreignKeys = [ForeignKey(
        entity = EnrolledIntent::class,
        parentColumns = ["id"],
        childColumns = ["intentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AcousticTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intentId: Long, // Links to EnrolledIntent
    val templateFeatures: String // Stored as a serialized string (JSON list of floats)
)
```

---

## 🔍 How this integrates with the AI Model (Developer B Bridge)

When Developer A's algorithm completes, it generates a mathematical representation of the child's vocalization (a matrix of floating-point values). 

As **Developer B**, your job is:
1.  Collect 7 training samples in the UI.
2.  Pass them to Developer A's engine to get the feature template arrays.
3.  Serialize those arrays and write them into the `acoustic_templates` database table.
4.  When the background listener starts, query the database, deserialize the templates back into memory, and feed them into the DTW engine for real-time comparison.

---

### Next Step:
We need to register these Room dependencies in our `build.gradle.kts` file before we can compile this code. Let me know when you are ready, and we will update the dependencies and write the database compiler class!
