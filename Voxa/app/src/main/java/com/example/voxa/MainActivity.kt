package com.example.voxa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.content.Context
import androidx.core.content.ContextCompat
import com.example.voxa.services.VoxaListenerService
import com.example.voxa.ui.*
import com.example.voxa.ui.screens.DashboardScreen
import com.example.voxa.ui.screens.EnrollmentScreen
import com.example.voxa.ui.screens.LibraryScreen
import com.example.voxa.ui.screens.ProfileScreen
import com.example.voxa.ui.screens.EmergencyScreen
import com.example.voxa.ui.screens.SpeechPracticeScreen
import com.example.voxa.ui.screens.OnboardingScreen
import com.example.voxa.ui.screens.PracticeViolet
import com.example.voxa.data.EmergencyContactPrefs
import com.example.voxa.ui.theme.*
import com.example.voxa.ui.quests.QuestsViewModel
import com.example.voxa.ui.screens.quests.QuestsMainScreen

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("voxa_settings", MODE_PRIVATE)
        val lang = prefs.getString("app_language", "en") ?: "en"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        // Layout direction is left to Compose to handle text natively
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    // The ViewModel acts as the central brain/storekeeper for the UI. It retrieves flows
    // from the Room database and keeps them updated in-memory for our Compose screens.
    private val viewModel: VoxaViewModel by viewModels()
    private val questsViewModel: QuestsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                VoxaTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Slate900
                    ) {
                        VoxaAppEntry(
                            viewModel = viewModel,
                            questsViewModel = questsViewModel
                        )
                    }
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
enum class Screen(val titleResId: Int, val icon: String) {
    Dashboard(R.string.title_home, "🏠"),
    Enrollment(R.string.title_enroll, "➕"),
    Library(R.string.title_library, "📚"),
    Practice(R.string.title_practice, "🎮"),
    Emergency(R.string.title_emergency, "🚨"),
    Profile(R.string.title_profile, "👤"),
    Quests(R.string.title_quests, "🎯")
}

// Security gate (night club analogy)
@Composable
fun VoxaAppEntry(viewModel: IVoxaViewModel, questsViewModel: QuestsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Track whether first-time setup has been completed
    var isSetupDone by remember { mutableStateOf(EmergencyContactPrefs.isSetupComplete(context)) }

    // Decide which screen to show based on our setup completion
    if (!isSetupDone) {
        OnboardingScreen(
            viewModel = viewModel,
            onOnboardingCompleted = { isSetupDone = true }
        )
    } else {
        VoxaAppContent(viewModel = viewModel, questsViewModel = questsViewModel, modifier = modifier)
    }
}

// The dance floor (Main container shell)
// This acts as a theater stage: a single persistent frame with a bottom tab switcher
// that dynamically slides different screen contents into focus depending on state.
@Composable
fun VoxaAppContent(viewModel: IVoxaViewModel, questsViewModel: QuestsViewModel, modifier: Modifier = Modifier) {
    val navStack = remember { mutableStateListOf(Screen.Dashboard) }
    val currentScreen = navStack.last()

    BackHandler(enabled = navStack.size > 1) {
        navStack.removeLast()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            CustomBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { screen -> 
                    if (navStack.last() != screen) {
                        navStack.add(screen)
                    }
                }
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
                    onNavigateToProfile = { navStack.add(Screen.Profile) }
                )
                Screen.Enrollment -> EnrollmentScreen(
                    viewModel = viewModel,
                    onBack = { if (navStack.size > 1) navStack.removeLast() }
                )
                Screen.Library -> LibraryScreen(
                    viewModel = viewModel,
                    onNavigateToEnrollment = { navStack.add(Screen.Enrollment) }
                )
                Screen.Emergency -> EmergencyScreen(viewModel = viewModel)
                Screen.Practice -> SpeechPracticeScreen(viewModel = viewModel)
                Screen.Profile -> ProfileScreen(
                    viewModel = viewModel,
                    onBack = { if (navStack.size > 1) navStack.removeLast() }
                )
                Screen.Quests -> QuestsMainScreen(
                    viewModel = questsViewModel,
                    onNavigateBack = { if (navStack.size > 1) navStack.removeLast() }
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
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Center Home (Dashboard) flanked by Library, Practice, Quests, and Emergency
            val tabScreens = listOf(Screen.Library, Screen.Practice, Screen.Dashboard, Screen.Quests, Screen.Emergency)
            
            tabScreens.forEach { screen ->
                val isSelected = currentScreen == screen
                
                val activeBgColor = when (screen) {
                    Screen.Emergency -> ErrorRed.copy(alpha = 0.15f)
                    Screen.Practice -> PracticeViolet.copy(alpha = 0.15f)
                    Screen.Library -> WarningAmber.copy(alpha = 0.15f)
                    Screen.Quests -> Sky400.copy(alpha = 0.15f)
                    else -> Sky400.copy(alpha = 0.15f)
                }
                val activeContentColor = when (screen) {
                    Screen.Emergency -> ErrorRed
                    Screen.Practice -> PracticeViolet
                    Screen.Library -> WarningAmber
                    Screen.Quests -> Sky400
                    else -> Sky400
                }
                val activeBorderColor = when (screen) {
                    Screen.Emergency -> ErrorRed.copy(alpha = 0.3f)
                    Screen.Practice -> PracticeViolet.copy(alpha = 0.3f)
                    Screen.Library -> WarningAmber.copy(alpha = 0.3f)
                    Screen.Quests -> Sky400.copy(alpha = 0.3f)
                    else -> Sky400.copy(alpha = 0.3f)
                }
                val inactiveContentColor = when (screen) {
                    Screen.Emergency -> ErrorRed.copy(alpha = 0.6f)
                    Screen.Practice -> PracticeViolet.copy(alpha = 0.6f)
                    Screen.Library -> WarningAmber.copy(alpha = 0.6f)
                    Screen.Quests -> Sky400.copy(alpha = 0.6f)
                    else -> Sky400
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
                        text = stringResource(id = screen.titleResId),
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
}

// ── PREVIEWS FOR ANDROID STUDIO DESIGN PANEL ──