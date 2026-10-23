package com.example.voxa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Keep the press alive across listening-state recompositions, but use current callbacks. */
@Composable
fun Modifier.microphoneGesture(
    holdMode: Boolean,
    gestureKey: Any? = Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggle: () -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    return if (holdMode) {
        pointerInput(holdMode, gestureKey) {
            detectTapGestures(onPress = {
                try {
                    start()
                    tryAwaitRelease()
                } finally {
                    // Also stop when a drag, mode change or navigation cancels the press.
                    stop()
                }
            })
        }
    } else {
        clickable(onClick = onToggle)
    }
}
