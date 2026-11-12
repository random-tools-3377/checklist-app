@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Suggest
import io.github.randomtools3377.checklist.core.MoneyPrices
import io.github.randomtools3377.checklist.core.RewardSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
    onBalanceCheck: () -> Unit = {},
    /** Opens the editor for a new reward. */
    onNew: () -> Unit = {},
) {
    if (view == null) return
    val haptics = LocalHapticFeedback.current
    val phone = LocalPhone.current
    var buying by remember { mutableStateOf<Reward?>(null) }
    var using by remember { mutableStateOf<Purchase?>(null) }
    var returning by remember { mutableStateOf<Purchase?>(null) }
    val used by vm.usedRewards.collectAsStateWithLifecycle()
    val limitLeft by vm.rewardsLimitLeft.collectAsStateWithLifecycle()
    val shopFeatures by vm.features.collectAsStateWithLifecycle()
    val savingGoals by vm.savings.collectAsStateWithLifecycle()
    val starred by vm.starredReward.collectAsStateWithLifecycle()
    // The saving goal a reward is paid from, picked in the Buy box; null for this month's budget.
    var payFrom by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        BalanceCard(
            view.balance,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            note = if (view.balance < 0) "The shop opens again once you are back above zero." else null,
        )
        val features by vm.features.collectAsStateWithLifecycle()
        val payPlan by vm.payPlan.collectAsStateWithLifecycle()
        val budgetOn = io.github.randomtools3377.checklist.core.Feature.BUDGET in features
        // The time a reward takes to earn comes from the pay, so it stays hidden while the budget is locked.
        val lockSettings by vm.settings.collectAsStateWithLifecycle()
        val isUnlocked by vm.unlocked.collectAsStateWithLifecycle()
        val budgetShown = budgetOn && !(lockSettings.locks.budget && !isUnlocked)
        if (io.github.randomtools3377.checklist.core.Feature.BALANCE_CHECK in features) {
            TextButton(onClick = onBalanceCheck, modifier = Modifier.padding(start = 8.dp).testTag("balance-check")) {
                Text("Balance check: are prices and ${pointsWord()} right?")
            }
        }
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
                        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("The shop is empty. Add a treat to earn, at a price you choose.", textAlign = TextAlign.Center, modifier = Modifier.testTag("shop-empty"))
                            Button(onClick = onNew, modifier = Modifier.padding(top = 12.dp).testTag("shop-empty-add")) { Text("New reward") }
                        }
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
                        work = if (budgetShown) rewardWorkText(row.reward.value, payPlan) else null,
                        starred = starred == row.reward.id,
                        onStar = { vm.setSavingFor(row.reward.id, starred != row.reward.id) },
                        perDay = view.perDay,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                if (view.bought.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nothing waiting. Rewards you buy wait here until you use them.", textAlign = TextAlign.Center)
                            TextButton(onClick = { onShowBought(false) }, modifier = Modifier.padding(top = 8.dp).testTag("bought-empty-shop")) { Text("See the rewards") }
                        }
                    }
                }
                items(view.bought, key = { it.id }) { purchase ->
                    BoughtCard(purchase, onUse = { using = purchase }, onReturn = { returning = purchase })
                    Spacer(Modifier.height(10.dp))
                }
                // Rewards already enjoyed, latest first.
                if (used.isNotEmpty()) {
                    item(key = "used-title") {
                        Text("Used", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp).testTag("used-title"))
                    }
                    items(used, key = { "used:${it.id}" }) { purchase ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("used:${purchase.name}")) {
                            Text(purchase.icon, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                            Text(purchase.name, modifier = Modifier.weight(1f))
                            Text(
                                "Enjoyed ${shortDate((purchase.usedAt ?: purchase.at).toLocalDateTime().date)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    buying?.let { reward ->
        AlertDialog(
            onDismissRequest = { buying = null },
            title = { Text("Buy ${reward.name}?") },
            text = {
                Column {
                    Text("It costs ${pointsText(reward.price)}. Your balance will be ${pointsText(view.balance - reward.price)}.")
                    // With the Budget on, what it costs in money, and what that leaves of the Rewards limit.
                    val cost = reward.value?.takeIf { io.github.randomtools3377.checklist.core.Feature.BUDGET in shopFeatures && it > 0 }
                    if (cost != null) {
                        val left = limitLeft
                        // Paid from savings, it takes nothing from the month's rewards limit.
                        val after = if (payFrom != null) null else left?.minus(io.github.randomtools3377.checklist.core.Budget.cents(cost))
                        Text(
                            when {
                                payFrom != null -> "In money: ${moneyText(cost)}."
                                after == null -> "In money: ${moneyText(cost)}. It counts as spent in your budget."
                                after >= 0 -> "In money: ${moneyText(cost)}. That leaves ${centsText(after)} for rewards this month."
                                else -> "In money: ${moneyText(cost)}. That is ${centsText(-after)} over your rewards limit this month."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (after != null && after < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp).testTag("buy-money"),
                        )
                        // Saving goals that hold enough can pay for it instead.
                        val able = savingGoals.filter { it.savedCents >= io.github.randomtools3377.checklist.core.Budget.cents(cost) }
                        if (able.isNotEmpty()) {
                            Text("Pay from", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                            ChoiceRow("This month's budget", payFrom == null, "pay-from:budget") { payFrom = null }
                            able.forEach { saving ->
                                ChoiceRow(
                                    "${saving.goal.icon} ${saving.goal.name} (${centsText(saving.savedCents)} saved)",
                                    payFrom == saving.goal.id,
                                    "pay-from:${saving.goal.name}",
                                ) { payFrom = saving.goal.id }
                            }
                            if (payFrom != null) {
                                Text(
                                    "It comes out of your savings, so it doesn't count as this month's spending.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        buying = null
                        val from = payFrom
                        payFrom = null
                        vm.buy(reward.id, from) { result ->
                            when (result) {
                                is BuyResult.Bought -> {
                                    celebrations.buzz(haptics)
                                    celebrations.show(BigCelebration.Bought(reward.icon, result.prize ?: reward.name, mystery = result.prize != null))
                                }
                                is BuyResult.TooExpensive ->
                                    phone.toast("Not enough ${pointsWord()} for ${reward.name} yet.")
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
            text = { Text("It moves to Used, under your Bought list.") },
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
    work: String? = null,
    /** Whether this is the one reward being saved for, shown on Today. */
    starred: Boolean = false,
    onStar: () -> Unit = {},
    /** What the user usually earns a day, for how long what is still missing takes. */
    perDay: Double? = null,
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
                    Text(
                        pointsText(reward.price) + (reward.value?.takeIf { reward.followsMoney }?.let { " · ${moneyText(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("price:${reward.name}"),
                    )
                    if (reward.price != row.usualPrice) {
                        Text(
                            "Usually ${pointsText(row.usualPrice)}: bought ${if (row.boughtThisWeek == 1) "once" else "${row.boughtThisWeek} times"} this week. Back to normal on Monday.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("repeat-price:${reward.name}"),
                        )
                    }
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
                    work?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("work:${reward.name}"))
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
                    // The one reward being saved for: Today's balance card shows it. A filled star and an empty one, not colour alone.
                    IconButton(onClick = onStar, modifier = Modifier.testTag("star:${reward.name}")) {
                        Text(
                            if (starred) "★" else "☆",
                            fontSize = 24.sp,
                            color = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = if (starred) "Saving for ${reward.name}. Tap to take the star off." else "Save for ${reward.name}: show it on Today"
                            },
                        )
                    }
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
                    "Saving for it: $have of ${pointsText(reward.price)} · ${pointsText(reward.price - balance)} to go",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).testTag("saving:${reward.name}"),
                )
                // What is still missing, from the same figure as "≈ N days of your usual" above.
                io.github.randomtools3377.checklist.core.Usual.more(reward.price - balance, perDay)?.let {
                    Text(
                        "$it at your usual pace",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("saving-days:${reward.name}"),
                    )
                }
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

/** [value] in the phone's own currency, like "$25", "$3.50" or "−$1,240.50". */
fun moneyText(value: Double, alwaysCents: Boolean = false): String {
    val cents = kotlin.math.round(kotlin.math.abs(value) * 100).toLong()
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val rest = cents % 100
    val sign = if (value < 0 && cents > 0) "−" else ""
    return sign + moneySymbol() + if (rest == 0L && !alwaysCents) whole else "$whole." + rest.toString().padStart(2, '0')
}

/** [cents] in the phone's own currency, with cents always shown. */
fun centsText(cents: Long): String = moneyText(cents / 100.0, alwaysCents = true)

/**
 * What a reward costs in money, and whether its price follows it. Following, the price in points
 * works itself out from the weekly treat budget and what the goals pay, and keeps in step with
 * them (see MoneyPrices). The budget is asked for here the first time.
 */
@Composable
private fun MoneyField(
    money: String,
    onMoney: (String) -> Unit,
    follows: Boolean,
    onFollows: (Boolean) -> Unit,
    vm: MainViewModel,
) {
    val budget by vm.treatBudget.collectAsStateWithLifecycle()
    var budgetText by rememberSaveable { mutableStateOf("") }
    val symbol = remember { moneySymbol() }
    OutlinedTextField(
        value = money,
        onValueChange = { text -> onMoney(text.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) },
        label = { Text("What it costs in money") },
        prefix = if (symbol.isNotEmpty()) ({ Text(symbol) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("field:money"),
    )
    if (Amounts.parse(money)?.takeIf { it > 0 } == null) return
    SwitchRow(
        "Price follows what it costs",
        follows,
        "field:follows-money",
        hint = "Turn this on to link them: the price in ${pointsWord()} then works itself out from this, and keeps in step as your goals change.",
        onChange = onFollows,
    )
    if (budget == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { text -> budgetText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                label = { Text("What you'd spend on treats in a week") },
                prefix = if (symbol.isNotEmpty()) ({ Text(symbol) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("field:treat-budget"),
            )
            TextButton(
                onClick = { vm.setTreatBudget(Amounts.parse(budgetText)) },
                enabled = Amounts.parse(budgetText) != null,
                modifier = Modifier.testTag("save-treat-budget"),
            ) { Text("Set") }
        }
        Text(
            "Money and ${pointsWord()} are linked by this: doing everything due in a week earns this much in rewards. Change it in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "How big is it?": a size suggests a price from the user's usual earnings at Normal, and so does
 * what it costs in money when the price doesn't follow it.
 */
@Composable
private fun SizeField(
    size: RewardSize?,
    onSize: (RewardSize?) -> Unit,
    moneyValue: Double?,
    price: String,
    onPrice: (String) -> Unit,
    vm: MainViewModel,
) {
    val perDay by vm.usualAtNormal.collectAsStateWithLifecycle()
    val rate by vm.moneyRate.collectAsStateWithLifecycle()
    Text(if (moneyValue != null) "Or roughly how big is it?" else "How big is it?", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        RewardSize.entries.forEach { choice ->
            FilterChip(
                selected = size == choice,
                onClick = { onSize(if (size == choice) null else choice) },
                label = { Text(choice.label) },
                modifier = Modifier.testTag("size:${choice.name}"),
            )
        }
    }
    val fromMoney = moneyValue?.let { value -> rate?.let { MoneyPrices.price(value, it) } }
    val suggested = if (size != null) Suggest.price(size, perDay) else fromMoney
    when {
        suggested != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Suggested: $suggested",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag("suggested-price"),
            )
            if (price != suggested.toString()) {
                TextButton(onClick = { onPrice(suggested.toString()) }, modifier = Modifier.testTag("use-suggested-price")) { Text("Use it") }
            }
        }
        size != null && perDay == null -> Text(
            "Add a goal or two first: the suggestion comes from what your goals pay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The balance check: what the user usually earns, how many days each reward takes, which are too
 * cheap or out of reach, and goals that could pay more or less, each with a suggestion to use.
 */
@Composable
fun BalanceCheckScreen(view: BalanceCheckView?, vm: MainViewModel, onBack: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Balance check") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        if (view == null) return@Scaffold
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("balance-check-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Text(
                    view.perDay?.let { perDay ->
                        "You usually earn about ${Suggest.nice(perDay)} ${pointsWord()} a day, ${Suggest.nice(perDay * 7)} a week" +
                            if (view.rate != io.github.randomtools3377.checklist.core.Difficulty.NORMAL) ", at ${view.rate.label}." else "."
                    } ?: "Tick a few goals first: the check works from what you usually earn.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp).testTag("usual-earnings"),
                )
            }
            if (view.rewards.isNotEmpty()) {
                item { SectionTitle("Rewards") }
                items(view.rewards.size) { i ->
                    val check = view.rewards[i]
                    CheckRow(
                        icon = check.reward.icon,
                        name = check.reward.name,
                        line = buildList {
                            add("${check.reward.price} ${pointsWord(check.reward.price)}")
                            // The same figure as on the shop's cards (see Usual.days).
                            io.github.randomtools3377.checklist.core.Usual.short(check.reward.price, view.perDay)?.let { add(it) }
                            when (check.verdict) {
                                io.github.randomtools3377.checklist.core.PriceVerdict.TOO_CHEAP -> add("maybe too cheap")
                                io.github.randomtools3377.checklist.core.PriceVerdict.OUT_OF_REACH -> add("maybe out of reach")
                                else -> {}
                            }
                        }.joinToString(" · "),
                        flagged = check.verdict != null && check.verdict != io.github.randomtools3377.checklist.core.PriceVerdict.FAIR,
                        suggestion = check.suggested?.let { "Make it $it" },
                        tag = "check-reward:${check.reward.name}",
                        onUse = check.suggested?.let { price -> { vm.setRewardPrice(check.reward.id, price) } },
                    )
                }
            }
            item { SectionTitle("Goals") }
            if (view.goals.none { it.hint != null }) {
                item {
                    Text(
                        io.github.randomtools3377.checklist.core.RuleWords.inBalance(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val hinted = view.goals.filter { it.hint != null }
            items(hinted.size) { i ->
                val check = hinted[i]
                CheckRow(
                    icon = check.goal.icon,
                    name = check.goal.name,
                    line = "done ${check.done} of ${check.due} times · " + when (check.hint) {
                        io.github.randomtools3377.checklist.core.GoalHint.PAY_LESS -> "nearly always done: it could pay less, or be made harder"
                        else -> "often missed: more ${pointsWord()} might help"
                    },
                    flagged = true,
                    suggestion = check.suggested?.let { "Pay $it instead of ${check.goal.points}" },
                    tag = "check-goal:${check.goal.name}",
                    onUse = check.suggested?.let { points -> { vm.setGoalPoints(check.goal.id, points) } },
                )
            }
        }
    }
}

@Composable
private fun CheckRow(icon: String, name: String, line: String, flagged: Boolean, suggestion: String?, tag: String, onUse: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag)) {
        Text(icon, fontSize = 26.sp, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (suggestion != null && onUse != null) {
            TextButton(onClick = onUse, modifier = Modifier.testTag("use:$tag")) { Text(suggestion) }
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
    // What it is worth: what it costs in money, which the price can follow, or a size, which
    // suggests a price.
    var size by rememberSaveable { mutableStateOf<String?>(null) }
    var money by rememberSaveable { mutableStateOf("") }
    var follows by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val rate by vm.moneyRate.collectAsStateWithLifecycle()
    // What the user actually earns a day: the shop's cards show the same days for the same price.
    val perDay by vm.usualPerDay.collectAsStateWithLifecycle()
    val budget by vm.treatBudget.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    // The one reward being saved for, shown on Today.
    var saving by rememberSaveable { mutableStateOf(false) }
    // What the editor held when it opened, to ask before throwing changes away.
    var baseline by rememberSaveable { mutableStateOf<String?>(null) }
    fun snapshot(): String = editorSnapshot(name, icon, price, mystery, if (mystery) prizes.filter { it.isNotBlank() } else null, size, money, follows, saving)

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadReward(id)?.let {
                name = it.name
                icon = it.icon
                price = it.price.toString()
                size = it.size?.name
                money = it.value?.let { v -> Amounts.format(v) }.orEmpty()
                follows = it.followsMoney
                if (it.isMystery) {
                    mystery = true
                    prizes = it.prizes
                }
                saving = vm.isSavingFor(id)
            }
            loaded = true
        }
        if (baseline == null) baseline = snapshot()
    }
    val changed = baseline != null && baseline != snapshot()

    val nameError = if (name.isBlank()) "Give it a name" else null
    val moneyValue = Amounts.parse(money)?.takeIf { it > 0 }
    // Following money, the price works itself out; until it can, it is typed in as usual.
    val autoPrice = if (follows && moneyValue != null) rate?.let { MoneyPrices.price(moneyValue, it) } else null
    val priceValue = autoPrice ?: price.toIntOrNull()
    val priceError = if (priceValue == null || priceValue < 1) "A price of at least ${pointsText(1)}" else null
    val prizesError = if (mystery && Mystery.tidy(prizes).size < 2) "Write at least two things it could be" else null

    EditorScaffold(
        title = if (id == 0L) "New reward" else "Edit reward",
        onBack = onClose,
        onSave = {
            if (nameError != null || priceError != null || prizesError != null) {
                showErrors = true
            } else {
                vm.saveReward(
                    RewardDraft(
                        id = id, name = name.trim(), icon = icon, price = priceValue!!, prizes = if (mystery) Mystery.tidy(prizes) else emptyList(),
                        size = size?.let { RewardSize.valueOf(it) }, value = moneyValue,
                        followsMoney = follows && moneyValue != null,
                        savingFor = saving,
                    ),
                    onDone = onClose,
                )
            }
        },
        changed = changed,
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
        // An extra: shown with suggested prices or the Budget on, or when this reward already has a value.
        val suggesting = io.github.randomtools3377.checklist.core.Feature.SUGGESTIONS in features
        val budgeting = io.github.randomtools3377.checklist.core.Feature.BUDGET in features
        val worth = suggesting || budgeting || size != null || money.isNotBlank()
        if (worth) {
            MoneyField(
                money = money,
                onMoney = { text ->
                    // With suggested prices on, typing a money value for the first time makes the
                    // price follow it. With only the Budget on, the value just counts as spending
                    // when the reward is bought, and the price follows only if the user links them.
                    if (suggesting && money.isBlank() && text.isNotBlank()) follows = true
                    money = text
                },
                follows = follows,
                onFollows = { follows = it },
                vm = vm,
            )
        }
        if (autoPrice != null) {
            Column(Modifier.fillMaxWidth()) {
                Text("Price: ${pointsText(autoPrice)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("auto-price"))
                Text(
                    buildList {
                        io.github.randomtools3377.checklist.core.Usual.describe(autoPrice, perDay)?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
                        add((if (isEmpty()) "Worked" else "worked") + " out from ${moneyText(moneyValue!!)}")
                    }.joinToString(", ") + ".",
                    modifier = Modifier.testTag("price-days"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            NumberField(
                label = "Price in ${pointsWord()}",
                value = price,
                onValueChange = { price = it },
                tag = "field:price",
                error = if (showErrors) priceError else null,
                // How long the typed price takes, as the shop will show it.
                hint = price.toIntOrNull()?.let { io.github.randomtools3377.checklist.core.Usual.describe(it, perDay) }?.replaceFirstChar { c -> c.uppercase() },
            )
            if (follows && moneyValue != null && budget != null) {
                Text(
                    "Add a goal that repeats first: the price comes from what your goals pay. Until then, set it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("auto-price-waiting"),
                )
            }
        }
        Text(
            "There is no limit on how often a reward can be bought, as long as the balance covers the price.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if ((suggesting || size != null) && autoPrice == null && !(follows && moneyValue != null)) {
            SizeField(
                size = size?.let { RewardSize.valueOf(it) },
                onSize = { size = it?.name },
                moneyValue = moneyValue,
                price = price,
                onPrice = { price = it },
                vm = vm,
            )
        }
        SwitchRow(
            "Saving for this",
            saving,
            "field:saving-for",
            hint = "Puts a star on it and shows it on Today, with how far along you are. One reward at a time: starring another moves the star.",
        ) { saving = it }
        SwitchRow(
            "Mystery reward",
            mystery,
            "field:mystery",
            hint = "Write what could come out of it. Each time it's bought, one is drawn at random.",
        ) { mystery = it }
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


/** One choice of a few, with a radio button; the whole row picks it. */
@Composable
private fun ChoiceRow(label: String, picked: Boolean, tag: String, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = picked, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onPick)
            .padding(vertical = 4.dp)
            .testTag(tag),
    ) {
        androidx.compose.material3.RadioButton(selected = picked, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
