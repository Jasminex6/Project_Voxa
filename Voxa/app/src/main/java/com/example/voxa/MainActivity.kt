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
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.example.voxa.services.VoxaListenerService
import com.example.voxa.ui.*
import com.example.voxa.ui.screens.DashboardScreen
import com.example.voxa.ui.screens.EnrollmentScreen
import com.example.voxa.ui.screens.LibraryScreen
import com.example.voxa.ui.screens.ProfileScreen
import com.example.voxa.ui.screens.EmergencyScreen
import com.example.voxa.ui.theme.*

class MainActivity : ComponentActivity() {

    // The ViewModel acts as the central brain/storekeeper for the UI. It retrieves flows
    // from the Room database and keeps them updated in-memory for our Compose screens.
    private val viewModel: VoxaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate900
                ) {
                    VoxaAppEntry(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Like opening a window to check the current weather, we sync the UI's listening toggle
    // with the actual background listener service whenever the app gains focus.
    override fun onResume() {
        super.onResume()
        // Sync listening state when resuming app focus
        viewModel.updateListeningState()
    }
}

// Tabs on a binder analogy: The Screen enum specifies the active pages that the bottom
// navigation bar can switch between.
enum class Screen(val title: String, val icon: String) {
    Dashboard("Home", "🏠"),
    Enrollment("Enroll", "➕"),
    Library("Library", "📚"),
    Emergency("Emergency", "🆘"),
    Profile("Profile", "👤")
}

// Security gate (night club analogy)
@Composable
fun VoxaAppEntry(viewModel: IVoxaViewModel, modifier: Modifier = Modifier) {
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
        VoxaAppContent(viewModel = viewModel, modifier = modifier)
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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Slate900 // Sleek slate dark background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎙️ Permissions Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Voxa requires microphone access to translate your child's vocalizations, and notification access to monitor sounds continuously in the background.\n\nAll audio is processed locally and never leaves this device.",
                fontSize = 14.sp,
                color = Slate400,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Sky400)
            ) {
                Text("Grant Permissions", color = Slate900, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// The dance floor (Main container shell)
// This acts as a theater stage: a single persistent frame with a bottom tab switcher
// that dynamically slides different screen contents into focus depending on state.
@Composable
fun VoxaAppContent(viewModel: IVoxaViewModel, modifier: Modifier = Modifier) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            CustomBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = { currentScreen = Screen.Profile }
                )
                Screen.Enrollment -> EnrollmentScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.Library }
                )
                Screen.Library -> LibraryScreen(
                    viewModel = viewModel,
                    onNavigateToEnrollment = { currentScreen = Screen.Enrollment }
                )
                Screen.Emergency -> EmergencyScreen(viewModel = viewModel)
                Screen.Profile -> ProfileScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.Dashboard }
                )
            }
        }
    }
}

// 📱 Custom minimalist Bottom Navigation Bar matching the premium styling
@Composable
fun CustomBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Surface(
        color = Slate800,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Center Home (Dashboard) flanked by Library and Emergency
            val tabScreens = listOf(Screen.Library, Screen.Dashboard, Screen.Emergency)
            
            tabScreens.forEach { screen ->
                val isSelected = currentScreen == screen
                
                val activeBgColor = when (screen) {
                    Screen.Emergency -> ErrorRed.copy(alpha = 0.15f)
                    else -> Sky400.copy(alpha = 0.15f)
                }
                val activeContentColor = when (screen) {
                    Screen.Emergency -> ErrorRed
                    else -> Sky400
                }
                val activeBorderColor = when (screen) {
                    Screen.Emergency -> ErrorRed.copy(alpha = 0.3f)
                    else -> Sky400.copy(alpha = 0.3f)
                }
                val inactiveContentColor = when (screen) {
                    Screen.Emergency -> ErrorRed.copy(alpha = 0.6f)
                    else -> Slate400
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) activeBgColor else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) activeBorderColor else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onScreenSelected(screen) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = screen.icon,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = screen.title,
                        color = if (isSelected) activeContentColor else inactiveContentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Permission Screen")
@Composable
fun PermissionRequiredScreenPreview() {
    VoxaTheme {
        PermissionRequiredScreen(onRequestPermission = {})
    }
}