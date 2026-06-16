# 🎓 Voxa Study Log: Lesson 1 — Starting from Zero

This is the archived version of Lesson 1. 

---

## 📱 What is an Android App?

At its simplest, an Android app is a compressed package of files called an **APK** (Android Package) or **AAB** (Android App Bundle). You can think of an APK like a `.zip` file. When a user downloads your app from the Play Store, their phone unzips this file.

Inside that package, there are two main types of files:
1.  **Compiled Code:** The logic you write (in our case, **Kotlin**) compiled into a format that the phone's CPU can read.
2.  **Resources:** The non-code visual and auditory parts of your app. This includes layouts, images, translation text files, and audio clips.

---

## 🛠️ The Tools of the Trade

To create this package, we use three tools:

### 1. Kotlin (The Programming Language)
*   **Why we use it:** Kotlin is Google's preferred language for Android. It is modern, clean, and helps prevent common coding mistakes.
*   **Intuition:** If the app is a robot, Kotlin is the set of rules that controls the robot's brain.

### 2. Android Studio (The Workshop)
*   **Why we use it:** It is the official development environment (IDE) for Android. It contains a code editor, visual design tools, and a device emulator.
*   **Intuition:** This is your digital workbench. It gathers your tools, highlights your typos, and lets you test your robot.

### 3. Gradle (The Builder)
*   **Why we use it:** Gradle is a background program that takes all your Kotlin files, images, and third-party libraries, and builds them into that final `.apk` package.
*   **Intuition:** This is your assembly line manager. You give Gradle instructions in configuration files, and it does the heavy lifting of building the app.

---

## 📝 Q&A Archive

> ❓ **[User Question]** what will be the app stack? i wanted to learn PERN but this seems diffirent so clarify what is this and what's its use
>
> 💡 **[Mentor Answer]** Answered in detail in Lesson 2! It is a Native Mobile Stack (Compose, SQLite/Room, Kotlin, TFLite) which operates offline.
