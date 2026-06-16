# 🎓 Voxa Study Log: Lesson 3 — The Android Manifest & Permissions

This is the archived version of Lesson 3, including our Q&A history.

---

## 💬 Q&A: Your Questions & My Answers

### ❓ Question 6: What is the difference between a Foreground Service and a Background Service?
**💡 Mentor Explanation:** 
*   **Background Service (Invisible):** A task running silently in the background that does not notify the user (e.g., checking if you have new emails once an hour). Because it's invisible, the Android Operating System will aggressively kill this service to save battery and RAM if you open a heavy app (like a game).
*   **Foreground Service (Visible & Persistent):** A service that must display a **permanent, non-dismissible notification** in the phone's status bar (e.g., Spotify playing music, Google Maps navigation, or Voxa listening for the child). Because the user is actively aware of it, Android promises **never to kill this service** unless the phone is in an absolute out-of-memory emergency. 
*   **Why we choose Foreground for Voxa:** Continuous audio recording requires high reliability. If we used a background service, Android would terminate our listener after 5 minutes of screen-off time.

---

### ❓ Question 7: What is the real difference between runtime and manifest-declared permissions?
**💡 Mentor Explanation:**
*   **Manifest-Declared (Normal) Permissions:** These are low-risk privacy features (like accessing the Internet or Bluetooth). You just write them in your `AndroidManifest.xml` file. The OS grants them automatically at install time. The user is never asked.
*   **Runtime (Dangerous) Permissions:** These are high-risk privacy features (like access to the Camera, GPS, or Microphone). Declaring them in the Manifest is **not enough**. The first time your app runs and needs the microphone, your Kotlin code must dynamically request it by popping up the system dialog box: *"Allow Voxa to record audio?"* The user must explicitly click "Allow".

---

### ❓ Question 8: What is XML itself?
**💡 Mentor Explanation:** **XML** stands for **eXtensible Markup Language**. It is a text-based format used to structure data using nested tag boundaries (very similar to HTML).
*   **Analogy:** Think of XML like a fill-in-the-blank registration form. It doesn't contain executable logic (no mathematical loops, no calculations); it just has labels (tags) and values.
*   **Example:**
    ```xml
    <label>Voxa</label>
    <minSdkVersion>26</minSdkVersion>
    ```
    Android reads this XML "form" to know the basic parameters of your app before compiling the Java/Kotlin code.

---

### ❓ Question 9: Can we start creating actual files and coding along the way? When do we start?
**💡 Mentor Explanation:** **Right now!** You are completely right. Learning theory is boring without seeing it compile.

We are establishing the base Android files directly in this workspace. In **Lesson 4**, we will write the exact files to your disk, open them in Android Studio, and compile our first running application!

---

## 📚 Lesson 3: The Android Manifest & Permissions

Below is the code we reviewed for `AndroidManifest.xml`. It declares our app screens, background service, and microphone permission requests.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.voxa.app">

    <!-- Permissions required by our app -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_TYPE_MICROPHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Voxa"
        android:supportsRtl="true">

        <!-- MainActivity (UI Launcher Screen) -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Listening Service -->
        <service
            android:name=".services.VoxaListenerService"
            android:foregroundServiceType="microphone"
            android:exported="false" />

    </application>
</manifest>
```
