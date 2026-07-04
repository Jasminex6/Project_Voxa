package com.example.voxa.utils

/**
 * 📝 SafeLog — JVM-safe logging wrapper
 *
 * Prevents "Method ... not mocked" RuntimeExceptions when running local JUnit tests
 * by catching the exception and falling back to System.out/System.err printing.
 */
object SafeLog {

    fun d(tag: String, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] D: $message")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        } catch (e: RuntimeException) {
            System.err.println("[$tag] E: $message")
            throwable?.printStackTrace()
        }
    }

    fun w(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] W: $message")
        }
    }

    fun i(tag: String, message: String) {
        try {
            android.util.Log.i(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] I: $message")
        }
    }
}
