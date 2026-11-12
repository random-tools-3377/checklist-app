package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.BillEvery
import io.github.randomtools3377.checklist.core.Bills
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.Pay
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.SavingProgress
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay

/** How many days ahead the Budget tab lists bills coming up. */
private const val BILLS_AHEAD_DAYS = 30

/** Bills falling due in the next [BILLS_AHEAD_DAYS] days, soonest first, for the Budget tab. */
fun billsComingUp(bills: List<Bill>, today: kotlinx.datetime.LocalDate): List<Pair<Bill, kotlinx.datetime.LocalDate>> =
    bills.filter { !it.archived }
        .flatMap { bill -> Bills.due(bill, today, today.plusDays(BILLS_AHEAD_DAYS)).map { bill to it } }
        .sortedBy { it.second }

/** The Budget tab's bills card: what's coming up, and the way to all of them. */
@Composable
fun BillsCard(bills: List<Bill>, onBills: () -> Unit) {
    val today = nowLocal().date
    val coming = billsComingUp(bills, today).take(5)
    Card(Modifier.fillMaxWidth().testTag("bills-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧾 Bills coming up", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBills, modifier = Modifier.testTag("open-bills")) { Text(if (bills.none { !it.archived }) "Add bills" else "All bills") }
            }
            if (coming.isEmpty()) {
                Text(
                    if (bills.none { !it.archived }) "Rent, phone, subscriptions: add them once and they're counted as spent on their day, by themselves."
                    else "Nothing due in the next $BILLS_AHEAD_DAYS days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            coming.forEach { (bill, day) ->
                Row(Modifier.fillMaxWidth().testTag("coming:${bill.name}")) {
                    Text(bill.icon, modifier = Modifier.width(32.dp))
                    Text(bill.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        (if (day == today) "today" else shortDate(day)) + " · " + centsText(bill.cents),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** The Budget tab's saving goals: each with a bar, and buttons to put money in or take it out. */
@Composable
fun SavingsCard(savings: List<SavingProgress>, hourly: Double?, vm: MainViewModel) {
    var editing by remember { mutableStateOf<SavingGoal?>(null) }
    var moving by remember { mutableStateOf<Pair<SavingGoal, Boolean>?>(null) }
    var removing by remember { mutableStateOf<SavingGoal?>(null) }
    // What is in a goal now, in cents.
    fun savedIn(goal: SavingGoal): Long = savings.firstOrNull { it.goal.id == goal.id }?.savedCents ?: 0
    Card(Modifier.fillMaxWidth().testTag("savings-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🐷 Saving for", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = SavingGoal(name = "", icon = "🎯", targetCents = 0) }, modifier = Modifier.testTag("add-saving")) { Text("Add") }
            }
            if (savings.isEmpty()) {
                Text(
                    "Saving up for something? Add it with how much it costs, then put money towards it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            savings.forEach { saving ->
                Column(Modifier.fillMaxWidth().testTag("saving:${saving.goal.name}")) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { editing = saving.goal }) {
                        Text(saving.goal.icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
                        Text(saving.goal.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            // A plain savings account has no target: just what is in it.
                            if (saving.goal.targetCents > 0) "${centsText(saving.savedCents)} of ${centsText(saving.goal.targetCents)}" else "${centsText(saving.savedCents)} saved",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("saving-amounts:${saving.goal.name}"),
                        )
                    }
                    LinearProgressIndicator(progress = { saving.share }, modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 2.dp))
                    Text(
                        if (saving.reached) "Reached! 🎉" else "${centsText(saving.leftCents)} to go" + (hourly?.let { " · ${workText(saving.leftCents, it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 36.dp),
                    )
                    Row(Modifier.padding(start = 28.dp)) {
                        TextButton(onClick = { moving = saving.goal to false }, modifier = Modifier.testTag("put-in:${saving.goal.name}")) { Text("Put in") }
                        if (saving.savedCents > 0) {
                            TextButton(onClick = { moving = saving.goal to true }, modifier = Modifier.testTag("take-out:${saving.goal.name}")) { Text("Take out") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { goal ->
        var name by remember(goal) { mutableStateOf(goal.name) }
        var icon by remember(goal) { mutableStateOf(goal.icon) }
        var target by remember(goal) { mutableStateOf(if (goal.targetCents > 0) Amounts.format(goal.targetCents / 100.0) else "") }
        val targetValue = Amounts.parse(target)?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (goal.id == 0L) "Saving for something" else "Edit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconPicker(icon) { icon = it }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("What for") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("field:saving-name"),
                    )
                    MoneyAmountField(target, { target = it }, "How much it costs", "field:saving-target")
                    if (goal.id != 0L) {
                        TextButton(onClick = { removing = goal; editing = null }, modifier = Modifier.testTag("remove-saving")) {
                            Text("Remove it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveSavingGoal(goal.copy(name = name, icon = icon, targetCents = Budget.cents(targetValue!!)))
                        editing = null
                    },
                    enabled = name.isNotBlank() && targetValue != null,
                    modifier = Modifier.testTag("save-saving"),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    // Removing asks first, and says what is still in it.
    removing?.let { goal ->
        val left = savedIn(goal)
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${goal.name}?") },
            text = {
                Text(
                    if (left > 0) "It still has ${centsText(left)} in it. Take it out to put it back in this month's money, or remove it and keep it counted as saved."
                    else "What went in and out stays in the history.",
                )
            },
            confirmButton = {
                if (left > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { vm.removeSavingGoal(goal.id, takeOut = true); removing = null }, modifier = Modifier.testTag("remove-saving-take-out")) {
                            Text("Take it out and remove")
                        }
                        TextButton(onClick = { vm.removeSavingGoal(goal.id); removing = null }, modifier = Modifier.testTag("confirm-remove-saving")) {
                            Text("Remove, keep it saved", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { removing = null }) { Text("Cancel") }
                    }
                } else {
                    TextButton(onClick = { vm.removeSavingGoal(goal.id); removing = null }, modifier = Modifier.testTag("confirm-remove-saving")) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = if (left > 0) null else ({ TextButton(onClick = { removing = null }) { Text("Cancel") } }),
        )
    }
    moving?.let { (goal, out) ->
        var amount by remember(goal, out) { mutableStateOf("") }
        val value = Amounts.parse(amount)?.takeIf { it > 0 }
        // Never more out than is in it.
        val inIt = savedIn(goal)
        val tooMuch = out && value != null && Budget.cents(value) > inIt
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(if (out) "Take out of ${goal.name}" else "Put into ${goal.name}") },
            text = { MoneyAmountField(amount, { amount = it }, "How much", "field:saving-amount", error = if (tooMuch) "Only ${centsText(inIt)} in it" else null) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.moveSaving(goal, Budget.cents(value!!), out)
                        moving = null
                    },
                    enabled = value != null && !tooMuch,
                    modifier = Modifier.testTag("confirm-saving"),
                ) { Text(if (out) "Take out" else "Put in") }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
}

/** An amount of money typed in, with the currency symbol in front. */
@Composable
fun MoneyAmountField(value: String, onValueChange: (String) -> Unit, label: String, tag: String, error: String? = null) {
    val symbol = remember { moneySymbol() }
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' || it == ',' }.take(10)) },
        label = { Text(label) },
        prefix = if (symbol.isNotEmpty()) ({ Text(symbol) }) else null,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Every bill, with how often it comes, when it's next due, and what they come to in a month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: MainViewModel, onBill: (Long) -> Unit, onBack: () -> Unit) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val shown = bills.filter { !it.archived }
    val today = nowLocal().date
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction()
 TextButton(onClick = { onBill(0) }, modifier = Modifier.testTag("add-bill")) { Text("Add") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("bills-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Text(
                    if (shown.isEmpty()) "No bills yet. Add rent, phone, subscriptions and the like: each counts as spent on its day, by itself."
                    else "About ${moneyText(shown.sumOf { Bills.monthly(it) }, alwaysCents = true)} a month in all. Each counts as spent on its day, by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("bills-total"),
                )
            }
            items(shown, key = { it.id }) { bill ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onBill(bill.id) }.padding(vertical = 6.dp).testTag("bill:${bill.name}"),
                ) {
                    Text(bill.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(bill.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            bill.howOften.replaceFirstChar { it.uppercase() } + (Bills.next(bill, today)?.let { " · next ${shortDate(it)}" } ?: " · completed"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(centsText(bill.cents), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** A bill: what it's for, how much, how often, from when and until when. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillEditor(id: Long, vm: MainViewModel, onClose: () -> Unit) {
    val categories by vm.moneyCategories.collectAsStateWithLifecycle()
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("🧾") }
    var amount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var every by rememberSaveable { mutableStateOf(BillEvery.MONTHS) }
    var count by rememberSaveable { mutableStateOf("1") }
    var first by rememberSaveable { mutableStateOf(nowLocal().date.toEpochDay()) }
    var until by rememberSaveable { mutableStateOf<Long?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var stored by remember { mutableStateOf<Bill?>(null) }
    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadBill(id)?.let {
                stored = it
                name = it.name
                icon = it.icon
                amount = Amounts.format(it.cents / 100.0)
                categoryId = it.categoryId
                every = it.every
                count = it.count.toString()
                first = it.first.toEpochDay()
                until = it.until?.toEpochDay()
            }
            loaded = true
        }
    }
    // Bills go under Bills unless another category is picked, found by its icon once it's renamed.
    LaunchedEffect(categories) {
        if (categoryId == null && id == 0L) categoryId = Budget.billsCategory(categories)?.id
    }
    val value = Amounts.parse(amount)?.takeIf { it > 0 }
    val times = count.toIntOrNull()?.takeIf { it in 1..99 }
    EditorScaffold(
        title = if (id == 0L) "New bill" else "Edit bill",
        onBack = onClose,
        onSave = {
            if (name.isBlank() || value == null || times == null) {
                showErrors = true
            } else {
                val bill = (stored ?: Bill(name = name, icon = icon, cents = 0, first = first.toDay())).copy(
                    id = id, name = name, icon = icon, cents = Budget.cents(value), categoryId = categoryId,
                    every = every, count = times, first = first.toDay(), until = until?.toDay(),
                )
                vm.saveBill(bill, onClose)
            }
        },
    ) {
        if (!loaded) return@EditorScaffold
        IconPicker(icon) { icon = it }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("What it's for, like Rent") },
            singleLine = true,
            isError = showErrors && name.isBlank(),
            modifier = Modifier.fillMaxWidth().testTag("field:name"),
        )
        MoneyAmountField(amount, { amount = it }, "How much", "field:amount", error = if (showErrors && value == null) "How much it is" else null)
        Text("How often", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Every")
            OutlinedTextField(
                value = count,
                onValueChange = { count = it.filter { c -> c.isDigit() }.take(2) },
                singleLine = true,
                isError = showErrors && times == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp).testTag("field:every"),
            )
            BillEvery.entries.forEach { choice ->
                FilterChip(
                    selected = every == choice,
                    onClick = { every = choice },
                    label = { Text(if ((times ?: 1) == 1) choice.one else choice.many) },
                    modifier = Modifier.testTag("every:${choice.name}"),
                )
            }
        }
        DateSetting(if (id == 0L) "First (or next) one" else "First one", first.toDay(), { it?.let { d -> first = d.toEpochDay() } }, tag = "field:first", canClear = false)
        DateSetting("Ends", until?.toDay(), { until = it?.toEpochDay() }, tag = "field:until", emptyText = "Never")
        Text("Category", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.filter { !it.archived || it.id == categoryId }.forEach { category ->
                FilterChip(
                    selected = categoryId == category.id,
                    onClick = { categoryId = category.id },
                    label = { Text("${category.icon} ${category.name}") },
                    modifier = Modifier.testTag("money-category:${category.name}"),
                )
            }
        }
        Text(
            if (id == 0L) "It's counted from today on. Anything already paid before today, add as spending if you want it in this month."
            else "Changes apply from now on; what was already counted stays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (id != 0L) {
            var asking by remember { mutableStateOf(false) }
            TextButton(onClick = { asking = true }, modifier = Modifier.testTag("remove")) { Text("Stop this bill", color = MaterialTheme.colorScheme.error) }
            if (asking) {
                AlertDialog(
                    onDismissRequest = { asking = false },
                    title = { Text("Stop $name?") },
                    text = { Text("It won't be counted again. What it already counted stays in the budget.") },
                    confirmButton = {
                        TextButton(onClick = { asking = false; vm.removeBill(id, onClose) }, modifier = Modifier.testTag("confirm-remove")) {
                            Text("Stop it", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

/**
 * The month's report: pay, money in, spending and saving; where the spending went, by category;
 * and a chart of each day's spending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetReportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val view by vm.budget.collectAsStateWithLifecycle()
    val shown = view
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction()

                    TextButton(onClick = { vm.showBudgetMonth(-1) }, modifier = Modifier.testTag("report-earlier")) { Text("◀", modifier = Modifier.spoken("Month before")) }
                    TextButton(onClick = { vm.showBudgetMonth(1) }, enabled = shown?.thisMonth == false, modifier = Modifier.testTag("report-later")) { Text("▶", modifier = Modifier.spoken("Month after")) }
                },
            )
        },
    ) { padding ->
        if (shown == null) return@Scaffold
        val month = remember(shown) { Budget.month(shown.first, shown.plan, shown.changes, shown.entries, shown.categories, nowLocal()) }
        val days = Pay.monthDays(shown.first)
        // Bills aside, as in the budget game, so the day a big bill falls doesn't always come out top.
        val spentByDay = days.map { day -> shown.entries.filter { it.day == day && it.kind == MoneyKind.SPENT && it.billId == null }.sumOf { it.cents } }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("report-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "${MONTH_NAMES[shown.first.monthNumber - 1]} ${shown.first.year}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("report-month"),
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        month.pay?.let { ReportRow(if (shown.thisMonth) "Pay so far" else "Pay", moneyText(it.earned, alwaysCents = true)) }
                        ReportRow("Other money in", centsText(month.receivedCents))
                        ReportRow("Spent", centsText(month.spentCents), "report:spent")
                        ReportRow("Put into savings", centsText(month.savedCents))
                        ReportRow("Left", moneyText(month.leftNow, alwaysCents = true), "report:left", bold = true)
                        month.pay?.takeIf { it.earned > 0 }?.let { pay ->
                            val share = (month.spentCents / 100.0 / pay.earned * 100).toInt()
                            Text(
                                "Spending was $share% of your pay" + if (shown.thisMonth) " so far." else ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Text("Each day's spending, bills aside", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item { DayBars(spentByDay, Modifier.fillMaxWidth().height(120.dp).testTag("report-chart")) }
            item {
                val most = spentByDay.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
                Text(
                    most?.let { "Most on ${shortDate(days[it.index])}: ${centsText(it.value)}." } ?: "Nothing spent yet, bills aside.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The month's spending, money in, bills and savings, for a spreadsheet app.
            item(key = "report-export") { BudgetMonthExportButton(shown.first, vm) }
            item { Text("Where it went", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            val spent = month.categories.filter { it.cents > 0 }.sortedByDescending { it.cents }
            if (spent.isEmpty()) item { Text("No spending this month.", style = MaterialTheme.typography.bodyMedium) }
            items(spent, key = { it.category.id }) { spend ->
                val share = if (month.spentCents > 0) spend.cents.toFloat() / month.spentCents else 0f
                Column(Modifier.fillMaxWidth().testTag("report-category:${spend.category.name}")) {
                    Row {
                        Text(spend.category.icon, modifier = Modifier.width(32.dp))
                        Text(spend.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${centsText(spend.cents)} · ${(share * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 2.dp))
                }
            }
        }
    }
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@Composable
private fun ReportRow(label: String, amount: String, tag: String? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else null,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }
}

/** A bar for each day, as tall as that day's share of the biggest day. */
@Composable
private fun DayBars(values: List<Long>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.outlineVariant
    val biggest = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Canvas(modifier) {
        val slot = size.width / values.size.coerceAtLeast(1)
        val bar = slot * 0.7f
        values.forEachIndexed { i, value ->
            val h = size.height * (value.toFloat() / biggest)
            drawRect(color, topLeft = Offset(i * slot + (slot - bar) / 2, size.height - h), size = Size(bar, h))
        }
        drawRect(base, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
    }
}

/**
 * Last month's money left over, with where to put it: a saving goal, or a plain Savings account
 * when there is none to pick. Nothing carries over into this month by itself.
 */
@Composable
fun LeftoverCard(savings: List<SavingProgress>, vm: MainViewModel) {
    val leftover by vm.leftover.collectAsStateWithLifecycle()
    val (first, cents) = leftover ?: return
    var choosing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("leftover-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎉 ${centsText(cents)} left from ${MONTH_NAMES[first.monthNumber - 1]}", style = MaterialTheme.typography.titleSmall)
            Text(
                "You kept it. Move it into savings and watch it grow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(
                    onClick = { if (savings.isEmpty()) vm.saveLeftover(null) else choosing = true },
                    modifier = Modifier.testTag("leftover-save"),
                ) { Text("Move to savings") }
                TextButton(onClick = vm::dismissLeftover, modifier = Modifier.testTag("leftover-dismiss")) { Text("Not now") }
            }
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Move ${centsText(cents)} into") },
            text = {
                Column {
                    savings.forEach { saving ->
                        TextButton(
                            onClick = { choosing = false; vm.saveLeftover(saving.goal.id) },
                            modifier = Modifier.fillMaxWidth().testTag("leftover-into:${saving.goal.name}"),
                        ) { Text("${saving.goal.icon} ${saving.goal.name}") }
                    }
                    if (savings.none { it.goal.name == io.github.randomtools3377.checklist.data.SAVINGS_ACCOUNT }) {
                        TextButton(
                            onClick = { choosing = false; vm.saveLeftover(null) },
                            modifier = Modifier.fillMaxWidth().testTag("leftover-into:account"),
                        ) { Text("💰 Savings") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}
