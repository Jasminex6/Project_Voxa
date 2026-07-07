package com.example.voxa.ui.screens.quests

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.QuestEntity
import com.example.voxa.ui.quests.QuestsViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildPlaygroundScreen(
    viewModel: QuestsViewModel,
    onBackToParent: () -> Unit
) {
    val childActiveQuests by viewModel.childActiveQuests.collectAsState()
    val childPoints by viewModel.childPoints.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var showRewards by remember { mutableStateOf(false) }

    // Spring animation for the wallet whenever points change
    val animatedPoints by animateFloatAsState(
        targetValue = childPoints.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pointsBounce"
    )

    // Bouncing Avatar animation
    val infiniteTransition = rememberInfiniteTransition(label = "avatarBounce")
    val avatarOffsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarOffsetY"
    )

    if (showRewards) {
        RewardsStoreScreen(viewModel = viewModel, onBack = { showRewards = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBackToParent) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showRewards = true }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Rewards", tint = QuestGold, modifier = Modifier.size(36.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Slate900,
                                QuestPurple.copy(alpha = 0.4f),
                                Sky600.copy(alpha = 0.5f),
                                QuestTeal.copy(alpha = 0.3f),
                                Slate900
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Dynamic Bouncing Avatar
                    Box(modifier = Modifier.offset(y = avatarOffsetY.dp)) {
                        Text(
                            text = activeProfile?.avatarEmoji ?: "👦",
                            fontSize = 80.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${activeProfile?.name ?: "My"} Missions! 🚀",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Wallet Header with bouncy scale
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(QuestTeal, Sky500)
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("My Star Wallet 🌟", color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐", fontSize = 64.sp)
                                Spacer(modifier = Modifier.width(16.dp))
                                // Bounce effect using derived scale from points changes
                                val scale = 1f + (animatedPoints - childPoints.toFloat()) * 0.05f
                                Text(
                                    text = childPoints.toString(),
                                    color = Color.White,
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.scale(scale.coerceIn(1f, 1.4f))
                                )
                            }
                        }
                    }

                    if (childActiveQuests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val bounce by infiniteTransition.animateFloat(
                                    initialValue = 0.9f,
                                    targetValue = 1.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "bounce"
                                )
                                Text("🎉", fontSize = 120.sp, modifier = Modifier.scale(bounce))
                                Spacer(modifier = Modifier.height(32.dp))
                                Text("All done for today!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Check back later for more fun.", color = Slate300, fontSize = 20.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(childActiveQuests, key = { it.id }) { quest ->
                                QuestChildCard(
                                    quest = quest,
                                    onCompleted = { viewModel.markQuestSubmitted(quest) },
                                    onUnable = { viewModel.markQuestUnable(quest) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestChildCard(
    quest: QuestEntity,
    onCompleted: () -> Unit,
    onUnable: () -> Unit
) {
    var isSubmitting by remember { mutableStateOf(false) }

    // Floating Animation
    val infiniteTransition = rememberInfiniteTransition(label = "floatingCard")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardRotation"
    )

    // FIXED: Renamed to offsetY to avoid scope shadowing inside graphicsLayer
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardTranslation"
    )

    // Submit Animation
    val submitScale by animateFloatAsState(
        targetValue = if (isSubmitting) 3f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "submitScale"
    )
    val submitAlpha by animateFloatAsState(
        targetValue = if (isSubmitting) 0f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "submitAlpha"
    )

    LaunchedEffect(isSubmitting) {
        if (isSubmitting) {
            delay(600) // Wait for burst animation
            onCompleted()
        }
    }

    if (isSubmitting) {
        // Render Burst State
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🌟",
                fontSize = 80.sp,
                modifier = Modifier
                    .scale(submitScale)
                    .graphicsLayer(alpha = submitAlpha)
            )
        }
    } else {
        // Normal Floating Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationZ = rotation
                    // FIXED: Using the renamed variable here
                    translationY = offsetY.dp.toPx()
                },
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(4.dp, QuestPurple.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Slate700),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = quest.icon, fontSize = 56.sp)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = quest.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Reward: ", color = Slate300, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${quest.points} ⭐", color = QuestGold, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onUnable,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) {
                        Text("I Need Help 🙋", color = Slate300, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }

                    // Pulsing Submit Button
                    val pulse by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "submitPulse"
                    )

                    Button(
                        onClick = { isSubmitting = true },
                        colors = ButtonDefaults.buttonColors(containerColor = QuestPurple),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .scale(pulse)
                    ) {
                        Text("I Did It! ✨", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}