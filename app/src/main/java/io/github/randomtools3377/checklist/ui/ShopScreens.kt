package io.github.randomtools3377.checklist.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import io.github.randomtools3377.checklist.core.Mystery
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.Purchase
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.toLocalDateTime

@Composable
fun ShopScreen(
    view: ShopView?,
    vm: MainViewModel,
    celebrations: Celebrations,
    showBought: Boolean,
    onShowBought: (Boolean) -> Unit,
    reordering: Boolean,
    onEdit: (Reward) -> Unit,
    padding: PaddingValues,
) {
    if (view == null) return
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var buying by remember { mutableStateOf<Reward?>(null) }
    var using by remember { mutableStateOf<Purchase?>(null) }
    var returning by remember { mutableStateOf<Purchase?>(null) }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        BalanceCard(
            view.balance,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            note = if (view.balance < 0) "The shop opens again once you are back above zero." else null,
        )
        TabRow(selectedTabIndex = if (showBought) 1 else 0) {
            Tab(selected = !showBought, onClick = { onShowBought(false) }, text = { Text("Rewards") }, modifier = Modifier.testTag("tab:rewards"))
            Tab(
                selected = showBought,
                onClick = { onShowBought(true) },
                text = { Text(if (view.bought.isEmpty()) "Bought" else "Bought (${view.bought.size})") },
                modifier = Modifier.testTag("tab:bought"),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            if (!showBought) {
                if (view.rewards.isEmpty()) {
                    item {
                        Text(
                            "The shop is empty. Add the real-life rewards you want to earn, at the prices you choose.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                itemsIndexed(view.rewards, key = { _, row -> row.reward.id }) { index, row ->
                    RewardCard(
                        row,
                        view.balance,
                        reordering = reordering,
                        canMoveUp = index > 0,
                        canMoveDown = index < view.rewards.lastIndex,
                        onMove = { vm.moveReward(row.reward.id, it) },
                        onBuy = { buying = row.reward },
                        onEdit = { onEdit(row.reward) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                if (view.bought.isEmpty()) {
                    item {
                        Text(
                            "Nothing waiting. Rewards you buy wait here until you use them.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                items(view.bought, key = { it.id }) { purchase ->
                    BoughtCard(purchase, onUse = { using = purchase }, onReturn = { returning = purchase })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    buying?.let { reward ->
        AlertDialog(
            onDismissRequest = { buying = null },
            title = { Text("Buy ${reward.name}?") },
            text = { Text("It costs ${pointsText(reward.price)}. Your balance will be ${pointsText(view.balance - reward.price)}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        buying = null
                        vm.buy(reward.id) { result ->
                            when (result) {
                                is BuyResult.Bought -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    celebrations.show(BigCelebration.Bought(reward.icon, result.prize ?: reward.name, mystery = result.prize != null))
                                }
                                is BuyResult.TooExpensive ->
                                    Toast.makeText(context, "Not enough points for ${reward.name} yet.", Toast.LENGTH_SHORT).show()
                                BuyResult.NotFound -> Unit
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm-buy"),
                ) { Text("Buy") }
            },
            dismissButton = { TextButton(onClick = { buying = null }) { Text("Cancel") } },
        )
    }

    returning?.let { purchase ->
        AlertDialog(
            onDismissRequest = { returning = null },
            title = { Text("Return ${purchase.name}?") },
            text = { Text("You get ${pointsText(purchase.price)} back, and it leaves your Bought list.") },
            confirmButton = {
                TextButton(onClick = { vm.returnPurchase(purchase.id); returning = null }, modifier = Modifier.testTag("confirm-return")) {
                    Text("Return it")
                }
            },
            dismissButton = { TextButton(onClick = { returning = null }) { Text("Keep it") } },
        )
    }

    using?.let { purchase ->
        AlertDialog(
            onDismissRequest = { using = null },
            title = { Text("Used ${purchase.name}?") },
            text = { Text("It leaves your Bought list.") },
            confirmButton = {
                TextButton(onClick = { vm.use(purchase.id); using = null }, modifier = Modifier.testTag("confirm-use")) { Text("Yes, used it") }
            },
            dismissButton = { TextButton(onClick = { using = null }) { Text("Not yet") } },
        )
    }
}

@Composable
private fun RewardCard(
    row: RewardRow,
    balance: Int,
    reordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onBuy: () -> Unit,
    onEdit: () -> Unit,
) {
    val reward = row.reward
    val affordable = reward.price <= balance
    Card(Modifier.fillMaxWidth().clickable(enabled = !reordering, onClick = onEdit).testTag("reward:${reward.name}")) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reward.icon, fontSize = 30.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(reward.name, style = MaterialTheme.typography.titleMedium)
                    Text(pointsText(reward.price), style = MaterialTheme.typography.bodyMedium)
                    if (reward.isMystery) {
                        Text(
                            "A mystery: one of ${reward.prizes.size}, drawn when you buy it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("mystery:${reward.name}"),
                        )
                    }
                    row.usual?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (reordering) {
                    IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.testTag("up:${reward.name}")) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${reward.name} up")
                    }
                    IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.testTag("down:${reward.name}")) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${reward.name} down")
                    }
                } else {
                    Button(onClick = onBuy, enabled = affordable, modifier = Modifier.testTag("buy:${reward.name}")) { Text("Buy") }
                }
            }
            if (!affordable) {
                // Saving for it: how far the balance has got toward the price.
                val have = balance.coerceAtLeast(0)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (have.toFloat() / reward.price).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Saving for it: $have of ${reward.price} · ${pointsText(reward.price - balance)} to go",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BoughtCard(purchase: Purchase, onUse: () -> Unit, onReturn: () -> Unit) {
    val day = purchase.at.toLocalDateTime().date
    Card(Modifier.fillMaxWidth().testTag("bought:${purchase.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(purchase.icon, fontSize = 30.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(purchase.name, style = MaterialTheme.typography.titleMedium)
                Text("Bought ${shortDate(day)} · ${pointsText(purchase.price)}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(onClick = onUse, modifier = Modifier.testTag("use:${purchase.name}")) { Text("Use it") }
                TextButton(onClick = onReturn, modifier = Modifier.testTag("return:${purchase.name}")) { Text("Return") }
            }
        }
    }
}

@Composable
fun RewardEditor(id: Long, vm: MainViewModel, onClose: () -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(REWARD_ICON) }
    var price by rememberSaveable { mutableStateOf("") }
    var mystery by rememberSaveable { mutableStateOf(false) }
    var prizes by rememberSaveable { mutableStateOf(listOf("", "")) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadReward(id)?.let {
                name = it.name
                icon = it.icon
                price = it.price.toString()
                if (it.isMystery) {
                    mystery = true
                    prizes = it.prizes
                }
            }
            loaded = true
        }
    }

    val nameError = if (name.isBlank()) "Give it a name" else null
    val priceValue = price.toIntOrNull()
    val priceError = if (priceValue == null || priceValue < 1) "A price of at least 1 point" else null
    val prizesError = if (mystery && Mystery.tidy(prizes).size < 2) "Write at least two things it could be" else null

    EditorScaffold(
        title = if (id == 0L) "New reward" else "Edit reward",
        onBack = onClose,
        onSave = {
            if (nameError != null || priceError != null || prizesError != null) {
                showErrors = true
            } else {
                vm.saveReward(
                    RewardDraft(id = id, name = name.trim(), icon = icon, price = priceValue!!, prizes = if (mystery) Mystery.tidy(prizes) else emptyList()),
                    onDone = onClose,
                )
            }
        },
    ) {
        if (!loaded) return@EditorScaffold
        IconPicker(icon) { icon = it }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Name") },
            singleLine = true,
            isError = showErrors && nameError != null,
            supportingText = if (showErrors && nameError != null) ({ Text(nameError) }) else null,
            modifier = Modifier.fillMaxWidth().testTag("field:name"),
        )
        NumberField(
            label = "Price in points",
            value = price,
            onValueChange = { price = it },
            tag = "field:price",
            error = if (showErrors) priceError else null,
        )
        Text(
            "There is no limit on how often a reward can be bought, as long as the balance covers the price.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Mystery reward", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Write what could come out of it. Each time it's bought, one is drawn at random.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = mystery, onCheckedChange = { mystery = it }, modifier = Modifier.testTag("field:mystery"))
        }
        if (mystery) {
            prizes.forEachIndexed { index, prize ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = prize,
                        onValueChange = { text -> prizes = prizes.toMutableList().also { it[index] = text.take(Mystery.PRIZE_LENGTH) } },
                        label = { Text("Could be ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field:prize-$index"),
                    )
                    IconButton(onClick = { prizes = prizes.filterIndexed { i, _ -> i != index } }, enabled = prizes.size > 2) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${index + 1}")
                    }
                }
            }
            if (showErrors && prizesError != null) {
                Text(prizesError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (prizes.size < Mystery.MAX_PRIZES) {
                TextButton(onClick = { prizes = prizes + "" }, modifier = Modifier.testTag("add-prize")) { Text("Add another") }
            }
        }
        if (id != 0L) {
            var asking by remember { mutableStateOf(false) }
            TextButton(onClick = { asking = true }, modifier = Modifier.testTag("remove")) {
                Text("Remove this reward", color = MaterialTheme.colorScheme.error)
            }
            if (asking) {
                AlertDialog(
                    onDismissRequest = { asking = false },
                    title = { Text("Remove $name?") },
                    text = { Text("It leaves the shop. Anything already bought stays under Bought.") },
                    confirmButton = {
                        TextButton(onClick = { asking = false; vm.removeReward(id, onClose) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
                )
            }
        }
    }
}
