package com.example.voxa.ui.screens.quests

import androidx.compose.ui.res.stringResource
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
                // Full screen Confetti Rain in the background
                ConfettiRain(modifier = Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(com.example.voxa.R.string.quests_child_missions),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Wallet Header with bouncy scale
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(QuestTeal, Sky500)
                                )
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(com.example.voxa.R.string.quests_child_wallet_title), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐", fontSize = 36.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                // Bounce effect using derived scale from points changes
                                val scale = 1f + (animatedPoints - childPoints.toFloat()) * 0.05f
                                Text(
                                    text = childPoints.toString(),
                                    color = Color.White,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.scale(scale.coerceIn(1f, 1.4f))
                                )
                            }
                        }
                    }

                    if (childActiveQuests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(com.example.voxa.R.string.quests_great_job), 
                                    color = QuestGold, 
                                    fontSize = 36.sp, 
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(com.example.voxa.R.string.quests_get_rewards), 
                                    color = Slate300, 
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
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
        // Render Burst State (Confetti Rain)
        ConfettiRain(modifier = Modifier.fillMaxWidth().height(200.dp))
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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Slate700),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = quest.icon, fontSize = 36.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = quest.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = stringResource(com.example.voxa.R.string.quests_reward_label), color = Slate300, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${quest.points} ⭐", color = QuestGold, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onUnable,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(stringResource(com.example.voxa.R.string.quests_need_help), color = Slate300, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
                            .height(52.dp)
                            .scale(pulse)
                    ) {
                        Text(stringResource(com.example.voxa.R.string.quests_i_did_it), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * A smooth, continuous confetti rain effect.
 */
@Composable
fun ConfettiRain(modifier: Modifier = Modifier) {
    var timeMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val startTime = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            androidx.compose.runtime.withFrameNanos { frameTimeNanos ->
                timeMillis = (frameTimeNanos - startTime) / 1_000_000L
            }
        }
    }

    val colors = listOf(Color(0xFFFFD700), Color(0xFF00F2FE), Color(0xFF14B8A6), Color(0xFFA855F7), Color(0xFFF97316)).map { it.copy(alpha = 0.85f) }
    
    // Generate pseudo-random deterministic particles
    val particles = remember {
        List(60) { index ->
            val x = (Math.random() * 2000).toFloat()
            val yOffset = (Math.random() * 2000).toFloat()
            val speed = 80f + (Math.random() * 120f).toFloat() // Slower speed
            val size = 10f + (Math.random() * 15f).toFloat()
            val color = colors[index % colors.size]
            Triple(x, Triple(yOffset, speed, size), color)
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas

        val timeSecs = timeMillis / 1000f

        particles.forEach { (startX, physics, color) ->
            val (yOffset, speed, pSize) = physics
            val normalizedX = (startX % w)
            
            // Endless smooth fall by wrapping around height + buffer
            val totalHeight = h + pSize * 2
            val currentY = ((yOffset + timeSecs * speed) % totalHeight) - pSize
            
            if (currentY > -pSize && currentY < h + pSize) {
                drawCircle(
                    color = color,
                    radius = pSize / 2f,
                    center = androidx.compose.ui.geometry.Offset(normalizedX, currentY)
                )
            }
        }
    }
}