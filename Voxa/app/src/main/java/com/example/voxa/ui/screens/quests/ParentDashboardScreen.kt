package com.example.voxa.ui.screens.quests

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    var historyExpanded by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val layoutDir = if (java.util.Locale.getDefault().language == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDir) {
        if (showRewards) {
            RewardsStoreScreen(viewModel = viewModel, onBack = { showRewards = false })
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(
                                stringResource(com.example.voxa.R.string.quests_parent_dashboard_title), 
                                color = Color.White, 
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ) 
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900),
                        actions = {
                            IconButton(onClick = { showRewards = true }) {
                                Icon(
                                    Icons.Default.ShoppingCart, 
                                    contentDescription = "Rewards Store", 
                                    tint = QuestGold, 
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onLogout) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp, 
                                    contentDescription = "Logout", 
                                    tint = Slate300,
                                    modifier = Modifier.size(26.dp)
                                )
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
                        Icon(Icons.Default.Add, contentDescription = "New Quest", modifier = Modifier.size(30.dp))
                    }
                },
                containerColor = Slate900
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Stats Card ("محفظة الطفل")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate800),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Slate700)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(com.example.voxa.R.string.quests_parent_child_wallet), 
                                    color = Slate400, 
                                    fontSize = 15.sp, 
                                    fontWeight = FontWeight.Medium, 
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(com.example.voxa.R.string.quests_parent_points, childPoints), 
                                        color = QuestGold, 
                                        fontSize = 30.sp, 
                                        fontWeight = FontWeight.ExtraBold, 
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("⭐", fontSize = 38.sp)
                                }
                            }
                        }
                    }

                    if (unfulfilledPurchases.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(com.example.voxa.R.string.quests_parent_pending_rewards), 
                                    color = QuestOrange, 
                                    fontSize = 20.sp, 
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(QuestOrange, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${unfulfilledPurchases.size}", 
                                        color = Color.White, 
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(unfulfilledPurchases) { purchase ->
                            PurchasedRewardCard(purchase = purchase, viewModel = viewModel)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(com.example.voxa.R.string.quests_parent_actionable_quests), 
                                color = Color.White, 
                                fontSize = 20.sp, 
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(QuestTeal, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${actionableQuests.size}", 
                                    color = Color.White, 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (actionableQuests.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Slate800),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Slate700)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(24.dp), 
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("✅", fontSize = 44.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            stringResource(com.example.voxa.R.string.quests_parent_all_caught_up), 
                                            color = Color.White, 
                                            fontSize = 17.sp, 
                                            fontWeight = FontWeight.Bold
                                        )
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { historyExpanded = !historyExpanded }
                        ) {
                            Text(
                                stringResource(com.example.voxa.R.string.quests_parent_completed_history), 
                                color = Color.White, 
                                fontSize = 20.sp, 
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Slate600, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${approvedQuests.size}", 
                                    color = Color.White, 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (historyExpanded && approvedQuests.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearHistoryDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Clear History",
                                        tint = ErrorRed.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Icon(
                                imageVector = if (historyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (historyExpanded) "Collapse" else "Expand",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (historyExpanded) {
                        if (approvedQuests.isEmpty()) {
                            item {
                                Text(
                                    stringResource(com.example.voxa.R.string.quests_parent_no_history), 
                                    color = Slate500, 
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 80.dp)
                                )
                            }
                        } else {
                            items(approvedQuests) { quest ->
                                QuestParentCard(quest = quest, viewModel = viewModel, isActionable = false)
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    } else {
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

            // Clear History Confirmation Dialog
            if (showClearHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showClearHistoryDialog = false },
                    title = { 
                        Text(
                            stringResource(com.example.voxa.R.string.quests_parent_clear_history_confirm_title), 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    text = { 
                        Text(
                            stringResource(com.example.voxa.R.string.quests_parent_clear_history_confirm_msg), 
                            color = Slate300
                        ) 
                    },
                    containerColor = Slate800,
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearCompletedHistory()
                                showClearHistoryDialog = false
                                historyExpanded = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Text(
                                stringResource(com.example.voxa.R.string.quests_parent_clear_confirm), 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearHistoryDialog = false }) {
                            Text(
                                stringResource(com.example.voxa.R.string.quests_parent_clear_cancel), 
                                color = Slate400
                            )
                        }
                    }
                )
            }
        } // Closes the `else` block for `if (showRewards)`
    } // Closes CompositionLocalProvider
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestParentCard(quest: QuestEntity, viewModel: QuestsViewModel, isActionable: Boolean) {
    val borderColor = when (quest.status) {
        QuestStatus.SUBMITTED -> QuestTeal
        QuestStatus.UNABLE -> QuestOrange
        QuestStatus.PENDING -> Slate700
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
        QuestStatus.SUBMITTED -> stringResource(com.example.voxa.R.string.quests_parent_badge_needs_verification)
        QuestStatus.UNABLE -> stringResource(com.example.voxa.R.string.quests_parent_badge_needs_help)
        QuestStatus.PENDING -> stringResource(com.example.voxa.R.string.quests_parent_badge_awaiting)
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isActionable && badgeText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText, 
                        color = badgeTextColor, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title, icon & description on Right (in RTL, first child of Row is rightmost)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quest.title, 
                            color = if (isActionable) Color.White else Slate400, 
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (quest.icon.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = quest.icon, fontSize = 22.sp)
                        }
                    }
                    if (quest.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = quest.description, 
                            color = Slate500, 
                            fontSize = 13.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Action Buttons in middle if actionable
                if (isActionable) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (quest.status) {
                            QuestStatus.SUBMITTED -> {
                                Button(
                                    onClick = { viewModel.rejectQuest(quest, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f), contentColor = ErrorRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(com.example.voxa.R.string.quests_parent_reject), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { viewModel.approveQuest(quest, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(com.example.voxa.R.string.quests_parent_approve), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            QuestStatus.UNABLE, QuestStatus.PENDING -> {
                                Button(
                                    onClick = { viewModel.deleteQuest(quest) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = Slate300, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (quest.status == QuestStatus.UNABLE) stringResource(com.example.voxa.R.string.quests_parent_btn_ack_delete) else stringResource(com.example.voxa.R.string.quests_parent_btn_cancel), 
                                        color = Slate300, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                // Points on Left (in RTL, last child of Row is leftmost)
                Text(
                    text = "+${quest.points}", 
                    color = if (isActionable) QuestGold else Slate500, 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 22.sp
                )
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
        border = BorderStroke(1.dp, QuestOrange.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!purchase.isFulfilled) {
                Box(
                    modifier = Modifier
                        .background(QuestOrange.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = stringResource(com.example.voxa.R.string.quests_unfulfilled_reward), color = QuestOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = purchase.icon, fontSize = 36.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = purchase.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = stringResource(com.example.voxa.R.string.quests_child_spent, purchase.cost), color = Slate400, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.fulfillPurchasedReward(purchase) },
                colors = ButtonDefaults.buttonColors(containerColor = QuestOrange),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Mark as Given", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(com.example.voxa.R.string.quests_parent_mark_given), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        Text(stringResource(com.example.voxa.R.string.quests_parent_new_quest), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(com.example.voxa.R.string.quests_parent_task_title), color = Slate400) },
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
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(com.example.voxa.R.string.quests_parent_task_desc), color = Slate400) },
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
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = points,
            onValueChange = { if (it.all { char -> char.isDigit() }) points = it },
            label = { Text(stringResource(com.example.voxa.R.string.quests_parent_points_reward), color = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuestTeal,
                unfocusedBorderColor = Slate600,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(com.example.voxa.R.string.quests_parent_select_emoji), color = Slate300, fontWeight = FontWeight.Medium)
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

        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate600)
            ) {
                Text(stringResource(com.example.voxa.R.string.quests_parent_cancel), color = Slate300, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { 
                    if (title.isNotBlank()) {
                        onCreate(title.trim(), description.trim(), selectedIcon, points.toIntOrNull() ?: 0)
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuestTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(com.example.voxa.R.string.quests_parent_assign), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmojiSelector(icon: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(if (isSelected) QuestTeal.copy(alpha = 0.3f) else Slate800, RoundedCornerShape(12.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(icon, fontSize = 26.sp)
        }
    }
}

