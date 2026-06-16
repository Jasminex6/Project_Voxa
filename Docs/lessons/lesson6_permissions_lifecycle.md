# 🎓 Voxa Study Log: Lesson 6 — Requesting Runtime Permissions & Service Lifecycle

Welcome to Lesson 6! In this lesson, we will resolve the red Manifest compiler error by exploring the lifecycle of our background service, and write our first Kotlin logic to ask the user for microphone access at runtime.

---

## 💬 Q&A: Answering Your Questions from Lesson 5

### ❓ Question 1: I'm seeing a warning on `android:name="android.permission.FOREGROUND_SERVICE"` and `android:label`. Why is that?
**💡 Mentor Explanation:**
*   **Warning on `FOREGROUND_SERVICE`:** In Android 14+ (API 34+), just declaring the permission `FOREGROUND_SERVICE` is not enough. The OS warns you: *"You requested a foreground service, but you must also request a specific service type permission."* We resolved this warning by declaring `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_TYPE_MICROPHONE" />` right below it. The warning is simply Android Studio reminding you of API 34 strict rules.
*   **Warning on `android:label`:** In your manifest, you hardcoded the app name as a string: `android:label="Voxa"`. Android Studio warns you: *"Hardcoded string 'Voxa' should use a `@string` resource."* 
    *   *Why?* For internationalization. Android prefers that you save UI strings in `res/values/strings.xml` so that if a user switches their phone language to Arabic, the OS automatically swaps "Voxa" with "بسمة" without rewriting the manifest. It is a styling warning, not a build error.

---

### ❓ Question 2: What is a WakeLock?
**💡 Mentor Explanation:** A **WakeLock** is a power-management flag.
*   When a phone screen turns off and sits idle, the Android OS goes into a deep sleep state called **Doze Mode** to conserve battery. In this mode, the CPU is paused.
*   **The Problem:** If the CPU is paused, our microphone listening loop stops running, and Voxa goes deaf!
*   **The Solution:** By requesting the `WAKE_LOCK` permission and acquiring a `PARTIAL_WAKE_LOCK` in Kotlin, we tell the phone: *"Keep the CPU awake in the background even if the user turns off the screen, because we are listening for vocalizations."*

---

### ❓ Question 3: I think you are behind on syntax in the permissions aspect.
**💡 Mentor Explanation:** You might be referring to how we request permissions in modern Jetpack Compose! 
*   If you've seen libraries like **Accompanist Permissions** (which gives you `rememberPermissionState()`), that is a popular helper library. 
*   However, for this project, I chose to use the **Native Android Activity API** (`rememberLauncherForActivityResult` with `RequestPermission()`). 
    *   *Why?* Accompanist is an experimental library that changes deprecations frequently. Using the native platform API has zero dependencies, never goes out of style, and is $100\%$ stable for a real production app. 
*   If you see another syntax style or error in Android Studio, write it down here and we will discuss the differences!

---

### ❓ Question 4: The `android:name=".services.VoxaListenerService"` error in the Manifest.
**💡 Mentor Explanation:** You have a sharp eye, and you are $100\%$ correct! 
*   The Manifest is a registration sheet. We wrote `<service android:name=".services.VoxaListenerService" />`, which tells Android: *"We have a background service file located at `com.example.voxa.services.VoxaListenerService`."*
*   But because **the Kotlin file did not exist yet** inside our source folders, Android Studio highlighted it in red to warn you.
*   **The Fix:** I have created the file **[VoxaListenerService.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/services/VoxaListenerService.kt)** in your package. If you check your Android Studio now, the red error in your Manifest has vanished!

---

### ❓ Question 5: `VoxaListenerService.kt` file isn't quite well explained. I don't know what `onCreate` does, what `super.onCreate()` is, or what `Log.d` means.

**💡 Mentor Explanation:** 
Let's break these down using some intuitive analogies. They are fundamental concepts in Android and Kotlin.

#### 1. What does `onCreate()` do?
Think of a Service like a background robot helper. 
*   `onCreate()` is the **birth** of the robot. It is called by the Android OS **exactly once** when the service is loaded into memory.
*   **Why it exists:** This is where we prepare the robot to work. We use it to initialize heavy tasks that only need to happen once—like loading our TensorFlow Lite AI model from the assets or opening our Room Database connection.

#### 2. What is `super.onCreate()`?
In Kotlin, we use **Inheritance**. Our class declaration looks like this:
```kotlin
class VoxaListenerService : Service()
```
The colon `:` means: *"VoxaListenerService is a child of Google's standard `Service` class."* 

Google's parent class has its own hidden setup code that it needs to run behind the scenes to configure the service properly on the phone.
*   `super` refers to the **parent class** (Google's standard `Service`).
*   `super.onCreate()` tells Kotlin: *"Run Google's standard setup code first, and then run my custom code below it."*
*   *Rule:* If you do not call `super.onCreate()`, Android will crash your service because the parent setup was skipped.

#### 3. What is `Log.d("VoxaService", "...")`?
When your app runs on a phone, you can't use standard print commands like `print()` or `console.log()` because there is no screen monitor attached. Instead, Android has a diagnostic logging system called **Logcat** (Zone 4 at the bottom of Android Studio).

*   `Log` is the Android class we use to print messages to the console.
*   `.d` stands for **Debug** (used for development messages. Others include `.e` for Error, `.w` for Warning, `.i` for Info).
*   **The first argument (`"VoxaService"`) is the TAG:** Think of this as a search tag. Because thousands of system logs run every second, you can type "VoxaService" in the Logcat search bar to filter out the noise and only see your app's logs.
*   **The second argument is the MESSAGE:** The actual text you want to print.

---

## 📚 Lesson 6: Requesting Runtime Permissions in Jetpack Compose

Even though we declared `<uses-permission android:name="android.permission.RECORD_AUDIO" />` in the Manifest, the phone will deny access by default because it is a privacy-sensitive permission.

We must write Kotlin code to check if we have the permission. 
*   If we do: Launch the dashboard.
*   If we don't: Show a screen with a button that launches the Android permission prompt.

### The Code Implementation
Open [MainActivity.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/MainActivity.kt) in Android Studio, and replace its entire content with this code:

```kotlin
package com.example.voxa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.voxa.ui.theme.VoxaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoxaAppEntry(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoxaAppEntry(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 1. Helper function to check if the permission is already granted
    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 2. Track permission state in Compose
    var isPermissionGranted by remember { mutableStateOf(hasMicPermission()) }

    // 3. Register a "launcher" to prompt the system permission dialog box
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            isPermissionGranted = isGranted // Update our state when the user clicks Allow/Deny
        }
    )

    // 4. Decide which screen to show based on our permission state
    if (isPermissionGranted) {
        VoxaDashboard(modifier)
    } else {
        PermissionRequiredScreen(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = modifier
        )
    }
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎙️ Microphone Access Required",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Voxa needs access to the microphone to listen and translate your child's vocalizations. All audio is processed locally and never leaves your device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun VoxaDashboard(modifier: Modifier = Modifier) {
    var isListening by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isListening) "🎙️ Active & Listening..." else "🔇 Microphone Paused",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { isListening = !isListening },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = if (isListening) "Stop Listening" else "Start Listening")
        }
    }
}
```

---

## 🔍 How this works (Building Intuition)

1.  **`ContextCompat.checkSelfPermission(...)`**: Before showing any popup, the app checks the OS security record. If the user previously clicked "Allow," it returns `PackageManager.PERMISSION_GRANTED` and skips the request screen.
2.  **`rememberLauncherForActivityResult(...)`**: This is a callback hook in Compose. It tells the OS: *"Prepare a popup dialog to request a permission, and when the user responds, run this code block to update my `isPermissionGranted` state variable."*
3.  **Recomposition:** When the user clicks "Allow," `isPermissionGranted` turns `true`. Compose instantly redraws the screen, automatically replacing the warning message with the main `VoxaDashboard`!

---

> ❓ **[Your Question]** 
> ```xml
> <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
> <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
> ```
> Why do we include both and not just the 2nd?
> 
> 💡 **[Mentor Answer]** 
> Excellent question! The short answer is: **Backwards Compatibility** and **Granular OS Security**.
> 
> 1. **The Base Permission (Android 9+):** The base permission `FOREGROUND_SERVICE` was introduced in Android 9 (API 28). If you run your app on any phone running Android 9 through Android 13, the phone *only* checks for this base permission. It has no idea what `FOREGROUND_SERVICE_MICROPHONE` is. If you remove the base one, the app will instantly crash on older devices.
> 2. **The Type Permission (Android 14+):** To prevent malicious apps from silently tracking users in the background, Android 14 (API 34) added strict restrictions. If you compile against API 34, you must *also* request the specific hardware type permission (`FOREGROUND_SERVICE_MICROPHONE`).
> 
> **Summary:** The Android OS checks **both**: the base permission to authorize the app to run in the background at all, and the type permission to verify that this background service is allowed to access the microphone hardware specifically.
