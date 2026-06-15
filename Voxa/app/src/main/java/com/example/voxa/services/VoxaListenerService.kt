package com.example.voxa.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * VoxaListenerService is a background Service.
 *
 * In Android, a Service is a component that runs in the background to perform long-running
 * operations without providing a user interface. 
 *
 * We inherit from the standard Android 'Service' class (indicated by the ':' symbol).
 */
class VoxaListenerService : Service() {

    /**
     * 1. onBind()
     *
     * This method is required by the parent Service class. It is used if another application
     * wants to "bind" (connect directly) to our service to communicate with it.
     *
     * For Voxa, we are a "Started Service" (we launch, do our work, and stop on our own). We do
     * not want other apps binding to us, so we return 'null' to indicate binding is not allowed.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 2. onCreate()
     *
     * This is the "birth" of our service. The Android OS calls this method exactly ONCE when
     * the service is first initialized in memory.
     *
     * We use this to set up things that only need to happen once (like initializing our local
     * audio buffers or loading our TFLite machine learning model into memory).
     */
    override fun onCreate() {
        // super.onCreate() tells Kotlin to run Google's default Service setup code first.
        // If we omit this line, Android will throw an exception and crash our app because
        // the parent class wasn't initialized!
        super.onCreate()

        // Log.d is the developer's printer. It prints messages to the "Logcat" console.
        // - "VoxaService" is the TAG (allows you to search and filter logs in Android Studio).
        // - "Service Created!" is the MESSAGE that gets printed.
        Log.d("VoxaService", "Service Created!")
    }

    /**
     * 3. onStartCommand()
     *
     * This runs every time our UI screen (MainActivity) tells the service to start running its logic
     * (e.g. when the user clicks the "Start Listening" button).
     *
     * This is where the actual action happens (starting our microphone recording thread).
     *
     * @return START_STICKY: Tells Android that if the OS kills this service to free up RAM,
     * it should automatically recreate and restart the service as soon as memory is free.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VoxaService", "Service started and running listening logic!")
        return START_STICKY
    }

    /**
     * 4. onDestroy()
     *
     * This is the "death" of our service. It is called by the OS when the service is stopped
     * (e.g. when the user clicks the "Stop Listening" button).
     *
     * We use this to release resources (like turning off the microphone, stopping recording threads,
     * and clearing memory) so the app doesn't leak battery or RAM.
     */
    override fun onDestroy() {
        // super.onDestroy() cleans up Google's default Service resources.
        super.onDestroy()
        Log.d("VoxaService", "Service Destroyed!")
    }
}
