package com.example.voxa.ui.screens.quests

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person // ADDED: Person Icon Import
import androidx.compose.material.icons.filled.PlayArrow
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
        Text(
            text = "Who is playing? 🤔",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))

        // Father Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clickable { onSelectFather() },
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(80.dp).background(QuestTeal.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    // FIXED: Using Icons.Default.Person
                    Icon(Icons.Default.Person, contentDescription = "Parent", tint = QuestTeal, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text("I'm the Parent", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Manage Quests & Rewards", color = Slate400, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Child Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clickable { onSelectChild() },
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(80.dp).background(QuestPurple.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(activeProfileEmoji, fontSize = 48.sp)
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text("I'm $activeProfileName", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Play Quests & Get Stars!", color = Slate400, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        TextButton(onClick = onNavigateBack) {
            Text("Exit Quests", color = Slate500, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
            text = "Caregiver Gateway 🛡️",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Create a 4-digit PIN to secure the parent dashboard.",
            color = Slate300,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
            label = { Text("Enter 4-digit PIN", color = Slate400) },
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
            label = { Text("Confirm PIN", color = Slate400) },
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
            Text("Set PIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("Back to Role Selection", color = Slate400, fontSize = 16.sp)
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
            text = "Caregiver Login",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
            label = { Text("Enter PIN", color = Slate400) },
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
            Text("Unlock Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = { viewModel.resetPin() }) {
            Text("Forgot PIN? Reset it", color = ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = onBack) {
            Text("Back to Role Selection", color = Slate400, fontSize = 16.sp)
        }
    }
}