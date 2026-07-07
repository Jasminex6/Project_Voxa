package com.example.voxa.ui.screens.quests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxa.data.PurchasedRewardEntity
import com.example.voxa.data.QuestEntity
import com.example.voxa.data.QuestStatus
import com.example.voxa.ui.quests.QuestsViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: QuestsViewModel,
    onLogout: () -> Unit
) {
    val actionableQuests by viewModel.parentActionableQuests.collectAsState()
    val approvedQuests by viewModel.approvedQuests.collectAsState()
    val unfulfilledPurchases by viewModel.unfulfilledPurchases.collectAsState()
    val childPoints by viewModel.childPoints.collectAsState()
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showCreateSheet by remember { mutableStateOf(false) }
    var showRewards by remember { mutableStateOf(false) }

    if (showRewards) {
        RewardsStoreScreen(viewModel = viewModel, onBack = { showRewards = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Caregiver Dashboard 🛠️", color = Color.White, fontWeight = FontWeight.ExtraBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900),
                    actions = {
                        IconButton(onClick = { showRewards = true }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Rewards Store", tint = QuestGold, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Slate300)
                        }
                    }
                )
            },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = QuestTeal,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Quest", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = Slate900
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Stats Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Slate600)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 40.sp)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text("Child's Wallet", color = Slate400, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("$childPoints Points", color = QuestGold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            if (unfulfilledPurchases.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pending Rewards to Fulfill 🎁", color = QuestOrange, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Badge(containerColor = QuestOrange) {
                            Text("${unfulfilledPurchases.size}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(unfulfilledPurchases) { purchase ->
                    PurchasedRewardCard(purchase = purchase, viewModel = viewModel)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Actionable Quests", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Badge(containerColor = QuestTeal) {
                        Text("${actionableQuests.size}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (actionableQuests.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate600)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✅", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("All caught up!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(actionableQuests) { quest ->
                    QuestParentCard(quest = quest, viewModel = viewModel, isActionable = true)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Completed History", color = Slate300, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Badge(containerColor = Slate600) {
                        Text("${approvedQuests.size}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (approvedQuests.isEmpty()) {
                item {
                    Text("No history yet.", color = Slate500, modifier = Modifier.padding(bottom = 80.dp))
                }
            } else {
                items(approvedQuests) { quest ->
                    QuestParentCard(quest = quest, viewModel = viewModel, isActionable = false)
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
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
            CreateQuestSheetContent(
                onDismiss = { 
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreateSheet = false
                        }
                    }
                },
                onCreate = { title, desc, icon, pts ->
                    viewModel.createQuest(title, desc, icon, pts)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showCreateSheet = false
                        }
                    }
                }
            )
        }
    }
    } // Closes the `else` block for `if (showRewards)`
}

@Composable
fun QuestParentCard(quest: QuestEntity, viewModel: QuestsViewModel, isActionable: Boolean) {
    val borderColor = when (quest.status) {
        QuestStatus.SUBMITTED -> QuestTeal
        QuestStatus.UNABLE -> QuestOrange
        QuestStatus.PENDING -> Slate600
        else -> Slate700
    }
    
    val badgeColor = when (quest.status) {
        QuestStatus.SUBMITTED -> QuestTeal.copy(alpha = 0.2f)
        QuestStatus.UNABLE -> QuestOrange.copy(alpha = 0.2f)
        QuestStatus.PENDING -> Slate700
        else -> Color.Transparent
    }
    
    val badgeTextColor = when (quest.status) {
        QuestStatus.SUBMITTED -> QuestTeal
        QuestStatus.UNABLE -> QuestOrange
        QuestStatus.PENDING -> Slate300
        else -> Slate500
    }
    
    val badgeText = when (quest.status) {
        QuestStatus.SUBMITTED -> "Needs Verification"
        QuestStatus.UNABLE -> "Child Needs Help"
        QuestStatus.PENDING -> "Awaiting Child"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (isActionable) {
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = badgeText, color = badgeTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = quest.icon, fontSize = 40.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quest.title, 
                        color = if (isActionable) Color.White else Slate400, 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = quest.description, color = Slate500, fontSize = 14.sp)
                }
                Text(
                    text = "+${quest.points}", 
                    color = if (isActionable) QuestGold else Slate500, 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 20.sp
                )
            }
            
            if (isActionable) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    when (quest.status) {
                        QuestStatus.SUBMITTED -> {
                            Button(
                                onClick = { viewModel.rejectQuest(quest, null) },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f), contentColor = ErrorRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Reject")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reject", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = { viewModel.approveQuest(quest, null) },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Approve", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        QuestStatus.UNABLE, QuestStatus.PENDING -> {
                            Button(
                                onClick = { viewModel.deleteQuest(quest) },
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = Slate300)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (quest.status == QuestStatus.UNABLE) "Acknowledge & Delete" else "Cancel Task", color = Slate300, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun PurchasedRewardCard(purchase: PurchasedRewardEntity, viewModel: QuestsViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, QuestOrange.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .background(QuestOrange.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "Unfulfilled Reward", color = QuestOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = purchase.icon, fontSize = 40.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = purchase.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Child spent ${purchase.cost} stars.", color = Slate400, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.fulfillPurchasedReward(purchase) },
                colors = ButtonDefaults.buttonColors(containerColor = QuestOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Mark as Given", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark as Given ✅", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQuestSheetContent(
    onDismiss: () -> Unit,
    onCreate: (title: String, desc: String, icon: String, points: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("50") }
    var selectedIcon by remember { mutableStateOf("🧹") }
    
    val icons = listOf("🧹", "🛏️", "🦷", "🍎", "🐶", "📚", "🪴", "👕")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("New Quest 🎯", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title", color = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description", color = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = points,
            onValueChange = { if (it.all { char -> char.isDigit() }) points = it },
            label = { Text("Points Reward", color = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
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
                EmojiSelector(icon, selectedIcon == icon) { selectedIcon = icon }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            icons.drop(4).forEach { icon ->
                EmojiSelector(icon, selectedIcon == icon) { selectedIcon = icon }
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
                        onCreate(title, description, selectedIcon, points.toIntOrNull() ?: 0)
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuestTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Assign", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmojiSelector(icon: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(if (isSelected) QuestTeal.copy(alpha = 0.3f) else Slate800, RoundedCornerShape(12.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(icon, fontSize = 28.sp)
        }
    }
}
