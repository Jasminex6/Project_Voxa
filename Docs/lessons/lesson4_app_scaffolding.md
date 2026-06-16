# 🎓 Voxa Study Log: Lesson 4 — Navigating Android Studio (Your Studio Tour)

Welcome! We will walk through setting up your Android Studio installation, opening our project files, and getting our first screen to compile.

This is your active study log for Lesson 4.

---

## 🗺️ The Tour of Android Studio: Finding Your Way Around

When you open our Voxa project, the window is split into four main zones. Let's build your intuition for what each zone does:

```
┌────────────────────────────────────────────────────────────────────────┐
│  [Zone 1: Top Toolbar] (Run buttons, Device selection, Gradle Sync)    │
├──────────────────────┬─────────────────────────────────────────────────┤
│                      │                                                 │
│  [Zone 2: Left]      │  [Zone 3: Main Editor]                          │
│  Project File Tree   │  Your Kotlin code files (e.g. MainActivity.kt)  │
│                      │                                                 │
│                      │                                                 │
├──────────────────────┴─────────────────────────────────────────────────┤
│  [Zone 4: Bottom Panel] (Logcat logs, Terminal, Build outputs)         │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 📂 Zone 2: The Project File Tree (Finding Your Files)
On the left side of your screen is the file tree. 
*   **Android View vs. Project View:** At the very top of the tree, you will see a dropdown that defaults to **Android**. 
    *   *Why?* The "Android" view is a simplified view that groups files by category (e.g., all Kotlin files in one place, all Gradle scripts in another).
    *   *Tip:* Keep it on **Android** for now. It hides empty system folders and makes navigation easier.
*   **How to find our UI code:** Expand `app` -> `java` -> `com.voxa.app` -> double-click **`MainActivity.kt`**. (Note: even though Compose is UI, the files are always normal Kotlin `.kt` files).
*   **How to find the build configuration:** Expand **`Gradle Scripts`** at the bottom of the tree. There you will see `build.gradle.kts (Module: app)`.

---

### 💻 Zone 3: The Main Editor (Coding & Auto-Imports)
This is where you write your code. As a beginner, here are two superpowers you must know:
*   **Red Underlines (Missing Imports):** In Kotlin, if you use a component (like `Button` or `Text`), the file needs an "import" statement at the top to know where that component comes from. If you copy code and see red underlines, it usually means the import is missing.
    *   *The Fix:* Hover your cursor over the red underlined word and press **`Alt + Enter`** (or `Option + Return` on Mac) -> select **Import**. Android Studio will automatically add the import line to the top of the file!
*   **Auto-Complete:** As you start typing (e.g. `Col...`), a menu will pop up showing `Column`. Press `Tab` or `Enter` to auto-complete. This saves typing and prevents spelling errors.

---

### 🚀 Zone 1: The Top Toolbar (Running the App)
Look at the top right of the toolbar. You only need to know three items here:
1.  **Device Dropdown:** Displays connected devices. Make sure your phone or emulator is selected here.
2.  **The Green Play Button (Run):** Compiles your code, packages it, pushes it to your phone, and launches it.
3.  **The Gradle Sync Elephant (Sync Project with Gradle Files):** If you make edits to `build.gradle.kts` (like adding a library), a bar will appear at the top saying *"Gradle files have changed. Sync Now."* Click it to tell the builder to fetch the new libraries.

---

### 🩺 Zone 4: The Debug Panel (Logcat)
At the very bottom, you will see tabs. The most important one is **Logcat**.
*   **What it is:** Logcat is the developer's console. It streams diagnostic logs from your phone in real-time.
*   **Why we use it:** If your app crashes, Logcat is where you find out why. The screen will display red text (a "stack trace") showing the exact file and line of code that caused the crash. 

---

## 🎨 Compose Concept: Column, Row, and Spacer

Now that you know the workspace, let's look at how Compose designs layouts. 

In HTML/CSS, you use `div` elements and styling. In Compose, you call layouts:
*   **`Column`**: Stacks elements vertically.
*   **`Row`**: Stacks elements horizontally.
*   **`Spacer`**: Creates space between elements. We specify spacing using `dp` (Density-independent Pixels), which auto-scales across different screen sizes.

---

## 🧠 Exercise: Test State in your MainActivity

Open `app/src/main/java/com/example/voxa/MainActivity.kt` in Android Studio, replace the contents with the code below, and run the app. Click the button and watch the text change:

```kotlin
package com.example.voxa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoxaDashboard()
                }
            }
        }
    }
}

@Composable
fun VoxaDashboard(modifier: Modifier = Modifier) {
    // 1. Declare the "State" variable. When this flips, the screen redraws.
    var isListening by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 2. Read state to decide what text to show
        Text(
            text = if (isListening) "🎙️ Active & Listening..." else "🔇 Microphone Paused",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Button click changes state
        Button(
            onClick = { isListening = !isListening },
            colors = ButtonDefaults.buttonColors( containerColor = if (isListening)
                MaterialTheme.colorScheme.error else
                MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = if (isListening) "Stop Listening" else "Start Listening")
        }
    }
}
```

---

## 📝 Student Notes & Questions

Write your comments, thoughts, or questions anywhere in the document, or use the slots below!

> ❓ **[Your Question]** *Type here...*

---

### Next Step:
Once you run the project, click the button to see "recomposition" in action, and save this file, let me know!
In **Lesson 5**, we will connect the real microphone and implement the runtime permission request dialog box!
