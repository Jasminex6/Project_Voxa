package com.example.voxa.ui.screens.quests

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.ui.quests.QuestsViewModel
import com.example.voxa.ui.theme.*

enum class RoleSelection { NONE, FATHER, CHILD }

@Composable
fun QuestsMainScreen(
    viewModel: QuestsViewModel,
    onNavigateBack: () -> Unit
) {
    val isPinSetup by viewModel.isPinSetup.collectAsState()
    val isParentAuthenticated by viewModel.isParentAuthenticated.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    // Automatically skip role selection if parent is already authenticated
    var selectedRole by remember { mutableStateOf(if (isParentAuthenticated) RoleSelection.FATHER else RoleSelection.NONE) }

    val layoutDir = if (java.util.Locale.getDefault().language == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDir) {
        when (selectedRole) {
        RoleSelection.NONE -> {
            RoleSelectionScreen(
                activeProfileName = activeProfile?.name ?: "Child",
                activeProfileEmoji = activeProfile?.avatarEmoji ?: "👦",
                onSelectFather = { selectedRole = RoleSelection.FATHER },
                onSelectChild = { selectedRole = RoleSelection.CHILD },
                onNavigateBack = onNavigateBack
            )
        }
        RoleSelection.CHILD -> {
            ChildPlaygroundScreen(
                viewModel = viewModel,
                onBackToParent = { selectedRole = RoleSelection.NONE }
            )
        }
        RoleSelection.FATHER -> {
            if (!isPinSetup) {
                PinSetupScreen(viewModel = viewModel, onBack = { selectedRole = RoleSelection.NONE })
            } else if (!isParentAuthenticated) {
                PinEntryScreen(
                    viewModel = viewModel,
                    onBack = { selectedRole = RoleSelection.NONE }
                )
            } else {
                BackHandler {
                    viewModel.logoutParent()
                    selectedRole = RoleSelection.NONE 
                }
                ParentDashboardScreen(
                    viewModel = viewModel,
                    onLogout = { 
                        viewModel.logoutParent()
                        selectedRole = RoleSelection.NONE 
                    }
                )
            }
        }
        }
    }
}

@Composable
fun RoleSelectionScreen(
    activeProfileName: String,
    activeProfileEmoji: String,
    onSelectFather: () -> Unit,
    onSelectChild: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val prefs = context.getSharedPreferences("voxa_settings", android.content.Context.MODE_PRIVATE)
        val caregiverName = prefs.getString("caregiver_name", stringResource(com.example.voxa.R.string.dashboard_caregiver_default_name)) ?: stringResource(com.example.voxa.R.string.dashboard_caregiver_default_name)

        // Catchy Title
        Text(
            text = stringResource(com.example.voxa.R.string.quests_main_title),
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            color = QuestTeal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        // Father Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFather() }
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(QuestTeal.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                // Circle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(QuestTeal.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = "Parent", tint = QuestTeal, modifier = Modifier.size(56.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.example.voxa.R.string.quests_role_parent_named, caregiverName), 
                color = QuestTeal, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    shadow = Shadow(
                        color = QuestTeal.copy(alpha = 0.6f),
                        blurRadius = 12f
                    )
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Child Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectChild() }
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(QuestPurple.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                // Circle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(QuestPurple.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(activeProfileEmoji, fontSize = 56.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.example.voxa.R.string.quests_role_child), 
                color = QuestPurple, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    shadow = Shadow(
                        color = QuestPurple.copy(alpha = 0.6f),
                        blurRadius = 12f
                    )
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(viewModel: QuestsViewModel, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Security",
            tint = QuestTeal.copy(alpha = alpha),
            modifier = Modifier.size(80.dp).scale(scale)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.quests_caregiver_gateway),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.quests_pin_prompt),
            color = Slate300,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
            label = { Text(stringResource(com.example.voxa.R.string.quests_set_pin_label), color = Slate400) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) confirmPin = it },
            label = { Text(stringResource(com.example.voxa.R.string.quests_confirm_pin_label), color = Slate400) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = error, color = ErrorRed, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = {
                if (pin.length != 4) {
                    error = "PIN must be 4 digits"
                } else if (pin != confirmPin) {
                    error = "PINs do not match"
                } else {
                    viewModel.setupPin(pin)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = QuestTeal),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(stringResource(com.example.voxa.R.string.quests_set_pin_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(com.example.voxa.R.string.quests_back_to_role), color = Slate400, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinEntryScreen(
    viewModel: QuestsViewModel,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Security",
            tint = Slate400,
            modifier = Modifier.size(80.dp).scale(scale)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(com.example.voxa.R.string.quests_caregiver_login),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
            label = { Text(stringResource(com.example.voxa.R.string.quests_enter_pin_label), color = Slate400) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = error, color = ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (viewModel.authenticateParent(pin)) {
                    error = ""
                } else {
                    error = "Incorrect PIN"
                    pin = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = QuestTeal),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(stringResource(com.example.voxa.R.string.quests_unlock_dashboard), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = { viewModel.resetPin() }) {
            Text(stringResource(com.example.voxa.R.string.quests_forgot_pin), color = ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = onBack) {
            Text(stringResource(com.example.voxa.R.string.quests_back_to_role), color = Slate400, fontSize = 16.sp)
        }
    }
}