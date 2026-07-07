package com.example.voxa.ui.screens.quests

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close // ADDED: Close Icon Import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.RewardEntity
import com.example.voxa.ui.quests.QuestsViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsStoreScreen(
    viewModel: QuestsViewModel,
    onBack: () -> Unit
) {
    val rewards by viewModel.rewards.collectAsState()
    val childPoints by viewModel.childPoints.collectAsState()
    val isParentAuthenticated by viewModel.isParentAuthenticated.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards Store 🎁", color = Color.White, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (isParentAuthenticated) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    containerColor = QuestOrange,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Reward", modifier = Modifier.size(32.dp))
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Slate900, QuestOrange.copy(alpha = 0.15f), Slate900)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                // Balance Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available Stars", color = Slate300, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$childPoints", color = QuestGold, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⭐", fontSize = 28.sp)
                    }
                }

                if (rewards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛒", fontSize = 80.sp)
                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Store is empty!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Ask your caregiver to add rewards.", color = Slate400, fontSize = 16.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(rewards) { reward ->
                            RewardCard(
                                reward = reward,
                                canAfford = childPoints >= reward.cost,
                                isParent = isParentAuthenticated,
                                onRedeem = { viewModel.redeemReward(reward) },
                                onDelete = { viewModel.deleteReward(reward) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState,
            containerColor = Slate900,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Slate600) }
        ) {
            CreateRewardSheetContent(
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreateSheet = false
                        }
                    }
                },
                onCreate = { title, cost, icon ->
                    viewModel.createReward(title, cost, icon)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreateSheet = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun RewardCard(
    reward: RewardEntity,
    canAfford: Boolean,
    isParent: Boolean,
    onRedeem: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && canAfford && !isParent) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = canAfford && !isParent,
                onClick = onRedeem
            ),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, if (canAfford) QuestOrange else Slate700),
        elevation = CardDefaults.cardElevation(defaultElevation = if (canAfford) 8.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Slate700),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = reward.icon, fontSize = 40.sp, modifier = Modifier.scale(if (!canAfford) 0.8f else 1f))
                }
                Text(
                    text = reward.title,
                    color = if (canAfford) Color.White else Slate500,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Button(
                    onClick = onRedeem,
                    enabled = canAfford && !isParent,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuestOrange,
                        disabledContainerColor = Slate600
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("${reward.cost} ⭐", color = if (canAfford) Color.White else Slate400, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }

            if (isParent) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(ErrorRed.copy(alpha = 0.2f), CircleShape)
                        .size(32.dp)
                ) {
                    // FIXED: Changed to Icons.Default.Close
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRewardSheetContent(
    onDismiss: () -> Unit,
    onCreate: (title: String, cost: Int, icon: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("100") }
    var selectedIcon by remember { mutableStateOf("🍦") }

    val icons = listOf("🍦", "🎮", "🏞️", "🎥", "🧸", "🚲", "🎨", "🍫")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("New Reward 🎁", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Reward Name", color = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestOrange,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = cost,
            onValueChange = { if (it.all { char -> char.isDigit() }) cost = it },
            label = { Text("Cost (Stars)", color = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestOrange,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Select Emoji:", color = Slate300, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))

        // Emoji Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            icons.take(4).forEach { icon ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (selectedIcon == icon) QuestOrange.copy(alpha = 0.3f) else Slate800, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = { selectedIcon = icon }, contentPadding = PaddingValues(0.dp)) {
                        Text(icon, fontSize = 28.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            icons.drop(4).forEach { icon ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (selectedIcon == icon) QuestOrange.copy(alpha = 0.3f) else Slate800, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = { selectedIcon = icon }, contentPadding = PaddingValues(0.dp)) {
                        Text(icon, fontSize = 28.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate600)
            ) {
                Text("Cancel", color = Slate300, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, cost.toIntOrNull() ?: 0, selectedIcon)
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuestOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}