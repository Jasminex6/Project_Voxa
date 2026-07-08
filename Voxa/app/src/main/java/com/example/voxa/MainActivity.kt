package com.example.voxa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
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
import com.example.voxa.ui.theme.*
import com.example.voxa.ui.quests.QuestsViewModel
import com.example.voxa.ui.screens.quests.QuestsMainScreen
import com.example.voxa.utils.LocaleHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.wrap(newBase, savedLang))
    }

    private val viewModel: VoxaViewModel by viewModels()
    private val questsViewModel: QuestsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appLanguage by viewModel.appLanguage.collectAsState()
            val layoutDirection = if (appLanguage == "ar") {
                androidx.compose.ui.unit.LayoutDirection.Rtl
            } else {
                androidx.compose.ui.unit.LayoutDirection.Ltr
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
            ) {
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

    override fun onResume() {
        super.onResume()
        viewModel.updateListeningState()
    }
}

enum class Screen(val titleRes: Int, val icon: String) {
    Dashboard(R.string.screen_home, "🏠"),
    Enrollment(R.string.screen_enroll, "➕"),
    Library(R.string.screen_library, "📚"),
    Practice(R.string.screen_practice, "🎮"),
    Emergency(R.string.screen_emergency, "🆘"),
    Profile(R.string.screen_profile, "👤"),
    Quests(R.string.screen_quests, "🎯")
}

@Composable
fun VoxaAppEntry(viewModel: IVoxaViewModel, questsViewModel: QuestsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE) }
    var isOnboardingCompleted by remember { mutableStateOf(sharedPrefs.getBoolean("is_first_boot_completed", false)) }

    if (!isOnboardingCompleted) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = {
                isOnboardingCompleted = true
            },
            modifier = modifier
        )
    } else {
        VoxaAppContent(viewModel = viewModel, questsViewModel = questsViewModel, modifier = modifier)
    }
}

@Composable
fun VoxaAppContent(viewModel: IVoxaViewModel, questsViewModel: QuestsViewModel, modifier: Modifier = Modifier) {
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
                Screen.Practice -> SpeechPracticeScreen(viewModel = viewModel)
                Screen.Profile -> ProfileScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.Dashboard }
                )
                Screen.Quests -> QuestsMainScreen(
                    viewModel = questsViewModel,
                    onNavigateBack = { currentScreen = Screen.Dashboard }
                )
            }
        }
    }
}

@Composable
fun CustomBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
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
            val tabScreens = listOf(Screen.Library, Screen.Dashboard, Screen.Practice, Screen.Emergency, Screen.Quests)
            
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
                        text = androidx.compose.ui.res.stringResource(id = screen.titleRes),
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