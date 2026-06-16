package com.example.voxa.utils

import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 🧠 TFLiteModelLoader
 * Helper class to map TensorFlow Lite model files directly from the app's assets folder
 * into memory using low-level file channels.
 */
object TFLiteModelLoader {
    /**
     * Loads a .tflite model file from the assets directory and returns its memory-mapped buffer.
     * This avoids allocating JVM heap memory for model weights, letting the OS load pages on demand.
     */
    fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        // 1. Open the file inside the APK's raw assets folder to get a descriptor (metadata & offset info).
        val fileDescriptor = context.assets.openFd(modelName)
        
        // 2. Wrap the file descriptor in a standard FileInputStream to establish a raw read stream.
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        
        // 3. Retrieve the low-level file channel which supports mapping files to virtual memory addresses.
        val fileChannel = inputStream.channel
        
        // 4. map() instructs the operating system to map the file from startOffset to declaredLength in memory-mapped mode.
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}
/*
Find .tflite model in assets
→ open it
→ create a file channel
→ memory-map the correct part of the file
→ return it to TensorFlow Lite
*/