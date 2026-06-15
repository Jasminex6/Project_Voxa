package com.example.voxa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.voxa.ui.theme.VoxaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoxaDashboard(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoxaDashboard(modifier: Modifier = Modifier) {
    // 1. State variable. When this flips, the screen redraws.
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun VoxaDashboardPreview() {
    VoxaTheme {
        VoxaDashboard()
    }
}