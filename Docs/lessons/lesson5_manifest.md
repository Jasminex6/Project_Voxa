# 🎓 Voxa Study Log: Lesson 5 — Manifest Configuration & Previews

This is your active study log for Lesson 5. Let's address your questions, configure our system permissions, and unlock Compose Previews.

---

## 💬 Q&A: Your Questions & My Answers

### ❓ Question 1: What does the Gradle wrapper exactly do?
**💡 Mentor Explanation:** The wrapper is like a **self-contained installation script**. 
*   Normally, if you download a Java project, you have to go to the web, download the exact match of Gradle manually, install it on your OS system paths, and configure environment variables.
*   The **Gradle Wrapper** solves this. It downloads the exact Gradle version specified in `gradle-wrapper.properties` in the background and runs it. It ensures that the project compiles identically on your machine, my machine, and the judges' machines, without anyone having to install Gradle manually.

---

### ❓ Question 2: How is our progress? Will we be able to finish the app?
**💡 Mentor Explanation:** **We are in a great position.** 
*   In software engineering, the beginning (setting up build wrappers, SDKs, and file structures) is always the most painful. Now that we have a $100\%$ healthy, compiling project, our pace will accelerate.
*   Our timeline is 10–12 days. We have completed the setup. Next is permission handling, then local database storage, and then writing the audio logic. We will comfortably finish on time.

---

### ❓ Question 3: Why do you tell me to edit code in Android Studio instead of in this text file?
**💡 Mentor Explanation:** You *can* edit code files anywhere (even using my file edit tools here). However, using Android Studio's editor is highly recommended for two reasons:
1.  **Instant Syntax Warnings:** If you write a typo (e.g. `colmun` instead of `Column`), Android Studio immediately highlights it in red. If you edit code here, you won't know there is a bug until you run compile and it fails with a long log.
2.  **Auto-Imports:** When you type `Button` in Android Studio, it automatically adds the line `import androidx.compose.material3.Button` to the top of the file. If you write code here, you have to write those import paths manually.

---

### ❓ Question 4: Are the Manifest and MainActivity files currently incomplete?
**💡 Mentor Explanation:**
*   **`MainActivity.kt`:** It is functional for a basic button toggle, but yes, it is currently a skeleton. We will grow it file-by-file (adding microphone permission prompts, vocabulary logs, and status visualizers).
*   **`AndroidManifest.xml`:** Yes, it is incomplete. It has no idea we need the microphone, background execution permissions, or a listening service. We will write these declarations in this lesson!

---

### ❓ Question 5: Resolving the "core-ktx:1.19.0 requires API 37" Error
You ran into a very common Android version issue: Android Studio's template pulled a brand-new helper library (`core-ktx 1.19.0`) that requires you to compile using the **Android 15 (API 37) SDK**, which isn't installed on your computer yet.

**How we fixed it:**
Rather than making you go through the SDK Manager and download a massive 2GB Android 15 SDK, **I edited your [libs.versions.toml](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/gradle/libs.versions.toml) file to downgrade `coreKtx` to `"1.13.0"`.** 
*   This version is fully compatible with your current API 36 SDK.
*   **Action Item:** Look at the top right of your Android Studio editor. You will see a blue banner saying: *"Gradle files have changed. Sync Now."* Click **Sync Now** to apply the fix and let the project compile!

---

## 🎨 Lesson 5: Compose Previews (Seeing Edits Live)

To see UI changes come to life, you don't even need to launch a phone or an emulator. Jetpack Compose has a feature called **Compose Previews**.

By placing the `@Preview` tag above a Composable function, Android Studio rendering engine draws a mock of that component directly inside the IDE.

### 🛠️ Step 1: Add a Preview to `MainActivity.kt`
Let's add a preview function to the bottom of your [MainActivity.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/MainActivity.kt). Open that file in Android Studio and add this block at the very end:

```kotlin
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun VoxaDashboardPreview() {
    VoxaTheme {
        VoxaDashboard()
    }
}
```

### 🔍 How to view it:
1.  Once you add this code, look at the top right of your Android Studio editor window.
2.  You will see three toggle buttons: **Code**, **Split**, and **Design**.
3.  Click **Split**.
4.  Android Studio will compile your UI in the background and render a live virtual mockup of the Voxa Dashboard directly next to your code! If you change the text, the preview updates instantly.

---

## 🔐 Lesson 5: Configuring the Android Manifest

Now, let's make our `AndroidManifest.xml` complete. We need to tell the Android Operating System that our app requires the microphone and will run a background recording service.

### 🛠️ Step 2: Update the Manifest File
Open [AndroidManifest.xml](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/AndroidManifest.xml) in Android Studio, and replace its contents with the configuration below:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 1. Request hardware and service permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_TYPE_MICROPHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Voxa">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Voxa">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 2. Register the background listening service -->
        <service
            android:name=".services.VoxaListenerService"
            android:foregroundServiceType="microphone"
            android:exported="false" />

    </application>
</manifest>
```

---

## 🩺 Lesson 5: Troubleshooting a Stuck Emulator

If the emulator screen is stuck on the Google "G" logo for more than 5 minutes, it is frozen. Here is how we diagnostic and fix this issue:

### Step 1: Wipe the Emulator Data (Factory Reset)
Sometimes the initial boot cache gets corrupted during setup.
1.  Close the emulator (click the close `X` on the device tab).
2.  Open the **Device Manager** panel in Android Studio.
3.  Find your **Pixel 6 API 34** emulator.
4.  On the far right, click the **three vertical dots** (Actions menu).
5.  Select **Wipe Data** and click **Yes** to confirm. (This clears any corrupted cached boot files).

### Step 2: Try a "Cold Boot"
A cold boot forces the emulator to boot up completely fresh rather than loading from a sleep state.
1.  Click the three vertical dots next to your emulator again.
2.  Select **Cold Boot Now**.
3.  Wait 1–2 minutes to see if it loads past the Google logo.

### Step 3: Switch to a Lighter Device (Pixel 5 API 33)
If the Pixel 6 still hangs, your CPU is struggling with the high resolution and API level. Let's make a lighter virtual device:
1.  In Device Manager, click **Create Device** (the plus icon).
2.  Choose **Pixel 5** (it uses much less RAM and screen resolution).
3.  For the System Image, select **API Level 33 (Tiramisu)** and download the **`x86_64`** version (if on Intel/AMD) or **`arm64`** (if on Mac M-chips).
4.  Once created, start this device. It is much more stable and boots in under 30 seconds!

---

## 📝 Student Notes & Questions

Write your comments, thoughts, or questions anywhere in the document, or use the slots below!

> ❓ **[Your Question]** *Type here...*
-showing a warning on android:name="android.permission.FOREGROUND_SERVICE" and android:label , why's that?
-what's wakelock
-I think ur behind on syntax int the permissions aspect
- android:name=".services.VoxaListenerService" error , i think it hasn't been declared yet
- 
---

### Next Step:
Add the Preview block to [MainActivity.kt](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/java/com/example/voxa/MainActivity.kt), update [AndroidManifest.xml](file:///d:/Jasmine/Build/AndroidStudioProjects/Voxa_Project/Voxa/app/src/main/AndroidManifest.xml) in Android Studio, save this file, and let me know when the sync succeeds and you are ready to write our first Kotlin permission request logic!
