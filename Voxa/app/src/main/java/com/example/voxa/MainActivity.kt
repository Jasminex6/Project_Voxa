package com.example.voxa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.example.voxa.services.VoxaListenerService
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

// Security gate (night club analogy)
@Composable
fun VoxaAppEntry(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Helper functions to check if the permissions are already granted
    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Track permission states in Compose
    var isMicGranted by remember { mutableStateOf(hasMicPermission()) }
    var isNotificationGranted by remember { mutableStateOf(hasNotificationPermission()) }

    // Register a launcher for multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            isMicGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: isMicGranted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isNotificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: isNotificationGranted
            }
        }
    )

    // Decide which screen to show based on our permission states
    if (isMicGranted && isNotificationGranted) {
        VoxaDashboard(modifier)
    } else {
        PermissionRequiredScreen(
            onRequestPermission = {
                val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(list.toTypedArray())
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
            text = "🎙️ Microphone & Notification Access Required",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Voxa needs access to the microphone to listen and translate your child's vocalizations, and notification access to run continuously in the background. All audio is processed locally and never leaves your device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permissions")
        }
    }
}

// The dance floor
@Composable
fun VoxaDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(VoxaListenerService.isRunning) }

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
            onClick = {
                val nextListeningState = !isListening
                val intent = Intent(context, VoxaListenerService::class.java)
                if (nextListeningState) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.stopService(intent)
                }
                isListening = nextListeningState
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = if (isListening) "Stop Listening" else "Start Listening")
        }
    }
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──

@Preview(showBackground = true, showSystemUi = true, name = "Permission Screen")
@Composable
fun PermissionRequiredScreenPreview() {
    VoxaTheme {
        PermissionRequiredScreen(onRequestPermission = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard")
@Composable
fun VoxaDashboardPreview() {
    VoxaTheme {
        VoxaDashboard()
    }
}