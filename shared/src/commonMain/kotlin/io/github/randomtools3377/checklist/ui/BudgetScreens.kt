package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.CategorySpend
import io.github.randomtools3377.checklist.core.DayPay
import io.github.randomtools3377.checklist.core.MoneyCategory
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.MoneyMonth
import io.github.randomtools3377.checklist.core.Pay
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.WorkDay
import io.github.randomtools3377.checklist.core.WorkHours
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private fun monthName(first: LocalDate) = "${MONTHS[first.monthNumber - 1]} ${first.year}"

/** The usual week, kept while the screen is rebuilt: start, stop and break for each day, -1 for a day off. */
private val WeekSaver = listSaver<List<WorkHours?>, Int>(
    save = { week -> week.flatMap { if (it == null) listOf(-1, 0, 0) else listOf(it.start, it.stop, it.breakMinutes) } },
    restore = { flat -> flat.chunked(3).map { (start, stop, rest) -> if (start < 0) null else WorkHours(start, stop, rest) } },
)

private fun minuteTime(minute: Int) = LocalTime((minute / 60) % 24, minute % 60)

/** How long [cents] takes to earn: "≈ 45 min of work", "≈ 2 h 15 min of work". */
fun workText(cents: Long, hourly: Double): String {
    val minutes = Pay.workMinutes(cents, hourly)
    val shown = if (minutes < 60) minutes else (minutes + 2) / 5 * 5
    return "≈ ${minutesText(shown)} of work"
}

/** The time now, ticking every second while [live]. */
@Composable
private fun rememberNow(live: Boolean): State<LocalDateTime> = produceState(nowLocal(), live) {
    while (live) {
        value = nowLocal()
        delay(1_000)
    }
}

/** An amount of money typed in: digits and one decimal point, with the currency symbol in front. */
@Composable
private fun MoneyInput(value: String, onValueChange: (String) -> Unit, label: String, tag: String, modifier: Modifier = Modifier, error: String? = null) {
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
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/**
 * The Budget tab: the month's pay filling up as the user works, what's been spent and received,
 * and spending by category against each category's limit.
 */
@Composable
fun BudgetScreen(
    vm: MainViewModel,
    padding: PaddingValues,
    onPay: () -> Unit,
    onEntry: (Long) -> Unit,
    onReceived: () -> Unit,
    onCategories: () -> Unit,
    onChangeDay: (LocalDate) -> Unit,
    onBills: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    LaunchedEffect(Unit) { vm.startBudget() }
    val view by vm.budget.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val savings by vm.savings.collectAsStateWithLifecycle()
    val game by vm.moneyGame.collectAsStateWithLifecycle()
    val paid by vm.moneyPaid.collectAsStateWithLifecycle()
    val workDays by vm.workDays.collectAsStateWithLifecycle()
    paid?.let { payouts ->
        AlertDialog(
            onDismissRequest = vm::moneyPaidSeen,
            title = { Text(if (payouts.any { it.points == io.github.randomtools3377.checklist.core.MoneyGame.SAVED_UP }) "🎉 Saved up!" else "💰 Money game") },
            text = {
                Column {
                    payouts.forEach { Text("+${pointsText(it.points)} · ${it.label.removePrefix("Money: ").replaceFirstChar { c -> c.uppercase() }}") }
                }
            },
            confirmButton = { TextButton(onClick = vm::moneyPaidSeen, modifier = Modifier.testTag("money-paid-ok")) { Text("Nice") } },
        )
    }
    val shown = view ?: return
    val now by rememberNow(live = shown.thisMonth)
    val month = remember(shown, now) { Budget.month(shown.first, shown.plan, shown.changes, shown.entries, shown.categories, now) }
    val plan = shown.plan
    val hourly = plan?.let { Pay.hourly(it) }
    val categories = shown.categories.associateBy { it.id }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("budget-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { vm.showBudgetMonth(-1) }, modifier = Modifier.testTag("budget-earlier")) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Month before")
                }
                Text(monthName(shown.first), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("budget-month"))
                IconButton(onClick = { vm.showBudgetMonth(1) }, enabled = !shown.thisMonth, modifier = Modifier.testTag("budget-later")) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Month after")
                }
                TextButton(onClick = onPay, modifier = Modifier.testTag("budget-pay")) { Text("Pay and hours") }
            }
        }
        if (plan == null || !plan.complete) {
            item {
                Card(Modifier.fillMaxWidth().testTag("budget-setup")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("💼 Set up your pay", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Put in what you're paid and your work hours, and a bar fills up with money as you work through the day and the month.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = onPay, modifier = Modifier.testTag("budget-setup-pay")) { Text("Set up pay") }
                    }
                }
            }
        }
        if (plan != null && plan.complete && shown.thisMonth) {
            // A night shift from yesterday still going shows until it ends.
            val todayPay = Pay.current(plan, now, workDays ?: shown.changes)
            item { TodayPayCard(todayPay, now, onChange = { onChangeDay(todayPay.day) }) }
        }
        game?.let { today -> item { MoneyGameCard(today) } }
        month.pay?.let { pay ->
            item {
                Card(Modifier.fillMaxWidth().testTag("month-pay")) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (shown.thisMonth) "This month's pay" else "Pay", style = MaterialTheme.typography.titleSmall)
                        Text(
                            moneyText(pay.earned, alwaysCents = true),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("month-earned"),
                        )
                        Text("of ${moneyText(pay.total, alwaysCents = true)}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (pay.total > 0) (pay.earned / pay.total).toFloat().coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                        TextButton(onClick = { onChangeDay(if (shown.thisMonth) now.date else shown.first) }, modifier = Modifier.testTag("change-a-day")) {
                            Text("Change a day…")
                        }
                    }
                }
            }
        }
        item { MonthSums(month, shown.thisMonth, onReceived) }
        if (shown.thisMonth) {
            item { BillsCard(bills, onBills) }
            item { SavingsCard(savings, hourly, vm) }
        }
        item {
            TextButton(onClick = onReport, modifier = Modifier.testTag("open-report")) { Text("Report for ${monthName(shown.first)}") }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Categories", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = onCategories, modifier = Modifier.testTag("edit-categories")) { Text("Edit") }
            }
        }
        items(month.categories.filter { it.cents > 0 || it.category.limitCents != null || !it.category.archived }, key = { "category-${it.category.id}" }) { spend ->
            CategoryRow(spend)
        }
        item {
            Text(
                "Spending and money in",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (shown.entries.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (shown.thisMonth) "Nothing spent yet this month. Tap Spent to add what you spend." else "Nothing that month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("budget-empty"),
                    )
                    if (shown.thisMonth) FilledTonalButton(onClick = { onEntry(0) }, modifier = Modifier.testTag("budget-empty-spent")) { Text("Spent") }
                }
            }
        }
        val byDay = shown.entries.groupBy { it.day }
        byDay.forEach { (day, list) ->
            item(key = "day-$day") {
                Text(longDate(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            items(list, key = { "money-${it.id}" }) { entry ->
                EntryRow(entry, entry.categoryId?.let { categories[it] }, hourly, onClick = { onEntry(entry.id) })
            }
        }
    }
}

/** Today's pay: what's earned so far, filling up live from start to stop. */
@Composable
private fun TodayPayCard(pay: DayPay, now: LocalDateTime, onChange: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag("today-pay"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💼 Today", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onChange, modifier = Modifier.testTag("change-today")) { Text(if (pay.day == now.date) "Change today" else "Change this shift") }
            }
            val hours = pay.hours
            when {
                pay.paidOff -> {
                    Text("Day off, paid", style = MaterialTheme.typography.titleMedium)
                    Text(moneyText(pay.total, alwaysCents = true), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("today-earned"))
                }
                hours == null -> Text(
                    if (pay.changed) "Day off, unpaid. Enjoy it." else "A day off. Enjoy it.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("today-off"),
                )
                else -> {
                    val done = pay.done(now)
                    Text(
                        moneyText(pay.earned(now), alwaysCents = true),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("today-earned"),
                    )
                    Text("of ${moneyText(pay.total, alwaysCents = true)} today", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { done.toFloat() }, modifier = Modifier.fillMaxWidth().height(12.dp).testTag("today-bar"))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${timeText(minuteTime(hours.start))} – ${timeText(minuteTime(hours.stop))}" + when {
                            done <= 0.0 -> " · starts at ${timeText(minuteTime(hours.start))}"
                            done >= 1.0 -> " · done for the day"
                            else -> " · ${minutesText(pay.minutesLeft(now))} to go"
                        } + if (!pay.changed) "" else if (pay.day == now.date) " · changed today" else " · changed",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** The month added up: pay so far, other money in, spending and what's left. */
@Composable
private fun MonthSums(month: MoneyMonth, thisMonth: Boolean, onReceived: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("month-sums")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            month.pay?.let { SumRow(if (thisMonth) "Pay so far" else "Pay", moneyText(it.earned, alwaysCents = true), "sum:pay") }
            SumRow("Other money in", "+" + centsText(month.receivedCents), "sum:received")
            SumRow("Spent", "−" + centsText(month.spentCents), "sum:spent")
            SumRow(if (thisMonth) "Left now" else "Left", moneyText(month.leftNow, alwaysCents = true), "sum:left", bold = true)
            val pay = month.pay
            if (thisMonth && pay != null && pay.total > pay.earned) {
                SumRow("Left at the end of the month", moneyText(month.leftAtEnd, alwaysCents = true), "sum:left-at-end")
                Text(
                    "If nothing else is spent, with the rest of this month's pay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReceived, modifier = Modifier.testTag("add-received")) { Text("Add money in") }
        }
    }
}

@Composable
private fun SumRow(label: String, amount: String, tag: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else null, modifier = Modifier.testTag(tag))
    }
}

/** A category's spending this month, with a bar against its limit if it has one. */
@Composable
private fun CategoryRow(spend: CategorySpend) {
    val limit = spend.category.limitCents
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("category:${spend.category.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spend.category.icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
            Text(spend.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                centsText(spend.cents) + (limit?.let { " of ${centsText(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = if (spend.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (limit != null && limit > 0) {
            LinearProgressIndicator(
                progress = { (spend.cents.toFloat() / limit).coerceIn(0f, 1f) },
                color = if (spend.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 2.dp),
            )
            Text(
                if (spend.over) "${centsText(spend.cents - limit)} over" else "${centsText(limit - spend.cents)} left",
                style = MaterialTheme.typography.bodySmall,
                color = if (spend.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp),
            )
        }
    }
}

@Composable
private fun EntryRow(entry: MoneyEntry, category: MoneyCategory?, hourly: Double?, onClick: () -> Unit) {
    val received = entry.kind == MoneyKind.RECEIVED
    if (entry.savingId != null) {
        val out = entry.kind == MoneyKind.TAKEN_OUT
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp).testTag("money:saving:${entry.note}"),
        ) {
            Text("🐷", fontSize = 22.sp, modifier = Modifier.width(36.dp))
            Text((if (out) "Taken out of " else "Put into ") + entry.note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text((if (out) "+" else "−") + centsText(entry.cents), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp).testTag("money:${entry.note.ifBlank { category?.name ?: "Money in" }}"),
    ) {
        Text(if (received) "💵" else category?.icon ?: "📦", fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.note.ifBlank { if (received) "Money in" else category?.name ?: "Spent" }, style = MaterialTheme.typography.bodyLarge)
            val line = buildList {
                if (!received && entry.note.isNotBlank()) category?.let { add(it.name) }
                if (!received && hourly != null) add(workText(entry.cents, hourly))
                if (entry.purchaseId != null) add("bought in the shop")
                if (entry.billId != null) add("bill")
            }
            if (line.isNotEmpty()) {
                Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            (if (received) "+" else "−") + centsText(entry.cents),
            style = MaterialTheme.typography.bodyLarge,
            color = if (received) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A time to tap and change, for the week's start and stop times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChip(minute: Int, onChange: (Int) -> Unit, tag: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) { Text(timeText(minuteTime(minute))) }
    if (open) {
        val state = rememberTimePickerState(initialHour = minute / 60 % 24, initialMinute = minute % 60, is24Hour = is24HourClock())
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }, modifier = Modifier.testTag("$tag:ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

/** Start, stop and break for one day. */
@Composable
private fun HoursRow(hours: WorkHours, onChange: (WorkHours) -> Unit, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TimeChip(hours.start, { onChange(hours.copy(start = it)) }, "$tag:start")
        Text("to")
        TimeChip(hours.stop, { onChange(hours.copy(stop = it)) }, "$tag:stop")
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = if (hours.breakMinutes == 0) "" else hours.breakMinutes.toString(),
            onValueChange = { text -> onChange(hours.copy(breakMinutes = text.filter { it.isDigit() }.take(3).toIntOrNull()?.coerceAtMost(hours.span) ?: 0)) },
            label = { Text("Break, min") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp).testTag("$tag:break"),
        )
    }
}

/** Pay and the usual week: how much, per what, how much is kept after tax, and each day's hours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayEditor(vm: MainViewModel, onClose: () -> Unit) {
    val current by vm.payPlan.collectAsStateWithLifecycle()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(PayUnit.HOUR) }
    var takeHome by rememberSaveable { mutableStateOf("100") }
    var week by rememberSaveable(stateSaver = WeekSaver) { mutableStateOf(List<WorkHours?>(7) { if (it < 5) WorkHours(9 * 60, 17 * 60, 30) else null }) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(current) {
        val plan = current
        if (!loaded && plan != null) {
            amount = Amounts.format(plan.amount)
            unit = plan.unit
            takeHome = plan.takeHome.toString()
            week = plan.week
        }
        loaded = true
    }
    val amountValue = Amounts.parse(amount)?.takeIf { it > 0 }
    val keep = takeHome.toIntOrNull()?.takeIf { it in 1..100 }
    EditorScaffold(
        title = "Pay and hours",
        onBack = onClose,
        onSave = {
            if (amountValue == null || keep == null || week.none { it != null }) {
                showErrors = true
            } else {
                vm.savePay(PayPlan(amountValue, unit, week, keep), onClose)
            }
        },
    ) {
        if (!loaded) return@EditorScaffold
        MoneyInput(amount, { amount = it }, "Pay", "field:pay", error = if (showErrors && amountValue == null) "How much you're paid" else null)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayUnit.entries.forEach { choice ->
                FilterChip(
                    selected = unit == choice,
                    onClick = { unit = choice },
                    label = { Text(choice.each) },
                    modifier = Modifier.testTag("pay-unit:${choice.name}"),
                )
            }
        }
        OutlinedTextField(
            value = takeHome,
            onValueChange = { text -> takeHome = text.filter { it.isDigit() }.take(3) },
            label = { Text("Kept after tax, in %") },
            singleLine = true,
            isError = showErrors && keep == null,
            supportingText = { Text("100 if the pay above is already what you take home.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("field:take-home"),
        )
        Text("Your usual week", style = MaterialTheme.typography.titleSmall)
        Text(
            when (unit) {
                PayUnit.HOUR -> "Each paid minute earns its share of the hourly pay; breaks aren't paid."
                PayUnit.DAY -> "Each day you work earns the day's pay."
                PayUnit.WEEK -> "The week's pay is spread over the hours below."
                PayUnit.MONTH -> "The month's pay is spread over the hours below in each month, so a usual month pays exactly that."
                PayUnit.YEAR -> "A twelfth of the year's pay is spread over the hours below in each month."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showErrors && week.none { it != null }) {
            Text("Turn on at least one work day.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        WEEKDAYS.forEachIndexed { i, name ->
            val hours = week[i]
            Column(Modifier.fillMaxWidth()) {
                // The whole row turns the day on or off, read out as "Monday, on".
                SwitchRow(name, hours != null, "workday:$i") { on ->
                    week = week.toMutableList().also { it[i] = if (on) week.firstOrNull { h -> h != null } ?: WorkHours(9 * 60, 17 * 60, 30) else null }
                }
                if (hours != null) HoursRow(hours, { changed -> week = week.toMutableList().also { it[i] = changed } }, "hours:$i")
            }
        }
        val monday = week[0]
        if (monday != null) {
            TextButton(
                onClick = { week = week.mapIndexed { i, h -> if (i > 0 && h != null) monday else h } },
                modifier = Modifier.testTag("copy-monday"),
            ) { Text("Same hours as Monday on every work day") }
        }
        Text(
            "A night shift that stops before it starts ends the next morning. To change just one day, use \"Change today\" or \"Change a day\" in the Budget tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One day that isn't the usual one: other hours, or a day off with or without pay. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkDayEditor(start: LocalDate, vm: MainViewModel, onClose: () -> Unit) {
    val plan by vm.payPlan.collectAsStateWithLifecycle()
    // Every changed day, so a day in another month shows as it is, not "As usual".
    val changes by vm.workDays.collectAsStateWithLifecycle()
    var day by rememberSaveable { mutableStateOf(start.toEpochDay()) }
    val date = day.toDay()
    val loaded = changes != null
    val current = changes?.get(date)
    val usual = plan?.usual(date)
    // 0 usual, 1 other hours, 2 off with pay, 3 off without.
    var choice by rememberSaveable(day, loaded, current) {
        mutableStateOf(
            when {
                current == null -> 0
                current.hours != null -> 1
                current.paid -> 2
                else -> 3
            },
        )
    }
    var hours by remember(day, loaded, current) { mutableStateOf(current?.hours ?: usual ?: WorkHours(9 * 60, 17 * 60, 30)) }
    EditorScaffold(
        title = "Change a day",
        onBack = onClose,
        onSave = save@{
            // Not before the day's change is known, or saving would wipe it.
            if (!loaded) return@save
            when (choice) {
                0 -> vm.usualWorkDay(date)
                1 -> vm.setWorkDay(WorkDay(date, hours))
                2 -> vm.setWorkDay(WorkDay(date, paid = true))
                else -> vm.setWorkDay(WorkDay(date, paid = false))
            }
            onClose()
        },
    ) {
        if (!loaded) return@EditorScaffold
        DateSetting("Day", date, { it?.let { picked -> day = picked.toEpochDay() } }, tag = "field:work-day", canClear = false)
        Text(
            "Usually: " + (usual?.let { "${timeText(minuteTime(it.start))} – ${timeText(minuteTime(it.stop))}" } ?: "a day off"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("As usual", "Other hours", "Day off, paid", "Day off, unpaid").forEachIndexed { i, label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { choice = i }.testTag("work-day:$i"),
            ) {
                RadioButton(selected = choice == i, onClick = { choice = i })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            if (i == 1 && choice == 1) HoursRow(hours, { hours = it }, "day-hours")
        }
        if (choice == 2 && usual == null) {
            Text(
                "It's usually a day off, so a paid day off adds nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Money spent or received: how much, what for, a note and the day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyEditor(id: Long, received: Boolean, vm: MainViewModel, onClose: () -> Unit) {
    val categories by vm.moneyCategories.collectAsStateWithLifecycle()
    val plan by vm.payPlan.collectAsStateWithLifecycle()
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var kind by rememberSaveable { mutableStateOf(if (received) MoneyKind.RECEIVED else MoneyKind.SPENT) }
    var amount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(nowLocal().date.toEpochDay()) }
    var purchaseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var billId by rememberSaveable { mutableStateOf<Long?>(null) }
    var savingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var at by rememberSaveable { mutableStateOf(0L) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadMoney(id)?.let {
                kind = it.kind
                amount = Amounts.format(it.cents / 100.0)
                categoryId = it.categoryId
                note = it.note
                day = it.day.toEpochDay()
                purchaseId = it.purchaseId
                billId = it.billId
                savingId = it.savingId
                at = it.at
            }
            loaded = true
        }
    }
    val value = Amounts.parse(amount)?.takeIf { it > 0 }
    val amountError = if (value == null) "How much" else null
    val categoryError = if (kind == MoneyKind.SPENT && categoryId == null) "Pick what it was for" else null
    EditorScaffold(
        title = when {
            savingId != null -> "Savings"
            id != 0L -> "Edit"
            kind == MoneyKind.RECEIVED -> "Money in"
            else -> "Spent"
        },
        onBack = onClose,
        onSave = {
            if (amountError != null || categoryError != null) {
                showErrors = true
            } else {
                vm.saveMoney(
                    MoneyEntry(
                        id = id, kind = kind, cents = Budget.cents(value!!),
                        categoryId = if (kind == MoneyKind.SPENT) categoryId else null,
                        note = note, day = day.toDay(), at = at, purchaseId = purchaseId, billId = billId, savingId = savingId,
                    ),
                    onClose,
                )
            }
        },
    ) {
        if (!loaded) return@EditorScaffold
        if (savingId != null) {
            Text(if (kind == MoneyKind.TAKEN_OUT) "Taken out of savings" else "Put into savings", style = MaterialTheme.typography.titleSmall)
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == MoneyKind.SPENT, onClick = { kind = MoneyKind.SPENT }, label = { Text("Spent") }, modifier = Modifier.testTag("money-kind:SPENT"))
            FilterChip(selected = kind == MoneyKind.RECEIVED, onClick = { kind = MoneyKind.RECEIVED }, label = { Text("Money in") }, modifier = Modifier.testTag("money-kind:RECEIVED"))
        }
        MoneyInput(amount, { amount = it }, "How much", "field:amount", error = if (showErrors) amountError else null)
        val hourly = plan?.let { Pay.hourly(it) }
        if (kind == MoneyKind.SPENT && value != null && hourly != null) {
            Text(workText(Budget.cents(value), hourly), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("work-time"))
        }
        if (kind == MoneyKind.SPENT) {
            Text("What for", style = MaterialTheme.typography.titleSmall)
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
            if (showErrors && categoryError != null) Text(categoryError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(80) },
            label = { Text(if (kind == MoneyKind.RECEIVED) "From (optional)" else "Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field:note"),
        )
        DateSetting("Day", day.toDay(), { it?.let { picked -> day = picked.toEpochDay() } }, tag = "field:day", canClear = false)
        if (purchaseId != null) {
            Text(
                "Bought in the shop. Returning the reward there takes this off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (id != 0L) {
            var asking by remember { mutableStateOf(false) }
            TextButton(onClick = { asking = true }, modifier = Modifier.testTag("remove")) {
                Text("Remove this", color = MaterialTheme.colorScheme.error)
            }
            if (asking) {
                AlertDialog(
                    onDismissRequest = { asking = false },
                    title = { Text("Remove it?") },
                    text = { Text("It comes off the budget.") },
                    confirmButton = {
                        TextButton(onClick = { asking = false; vm.removeMoney(id, onClose) }, modifier = Modifier.testTag("confirm-remove")) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

/** The budget's categories: names, icons, monthly limits and order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val categories by vm.moneyCategories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MoneyCategory?>(null) }
    val shown = categories.filter { !it.archived }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction()

                    TextButton(onClick = { editing = MoneyCategory(name = "", icon = "📦") }, modifier = Modifier.testTag("add-category")) { Text("Add") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("categories-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Text(
                    "Tap one to rename it or give it a monthly limit. Rewards is where rewards bought in the shop go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(shown, key = { it.id }) { category ->
                val index = shown.indexOf(category)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { editing = category }.padding(vertical = 4.dp).testTag("edit-category:${category.name}"),
                ) {
                    Text(category.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            category.limitCents?.let { "Limit ${centsText(it)} a month" } ?: "No limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.moveMoneyCategory(category.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${category.name} up")
                    }
                    IconButton(onClick = { vm.moveMoneyCategory(category.id, 1) }, enabled = index < shown.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${category.name} down")
                    }
                }
            }
        }
    }
    editing?.let { category ->
        var name by remember(category) { mutableStateOf(category.name) }
        var icon by remember(category) { mutableStateOf(category.icon) }
        var limit by remember(category) { mutableStateOf(category.limitCents?.let { Amounts.format(it / 100.0) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (category.id == 0L) "New category" else "Edit category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconPicker(icon) { icon = it }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("field:category-name"),
                    )
                    MoneyInput(limit, { limit = it }, "Monthly limit (optional)", "field:category-limit")
                    if (category.id != 0L && category.role == null) {
                        TextButton(
                            onClick = { vm.removeMoneyCategory(category.id); editing = null },
                            modifier = Modifier.testTag("remove-category"),
                        ) { Text("Remove this category", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveMoneyCategory(category.copy(name = name, icon = icon, limitCents = Amounts.parse(limit)?.takeIf { it > 0 }?.let { Budget.cents(it) }))
                        editing = null
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("save-category"),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

/** A work-time line for a reward with a money value in the shop, while Budget is on and pay is set. */
fun rewardWorkText(value: Double?, plan: PayPlan?): String? {
    val hourly = plan?.let { Pay.hourly(it) } ?: return null
    return value?.takeIf { it > 0 }?.let { workText(Budget.cents(it), hourly) }
}

/** The Budget tab's icon: a banknote, drawn here as the icon set has none. */
internal val MoneyIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Money", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        // A note with a coin in the middle, cut out so it reads at small sizes.
        pathData = androidx.compose.ui.graphics.vector.addPathNodes(
            "M2,6h20v12h-20z M4,8v8h16v-8z M12,9.5a2.5,2.5 0,1 1,0 5a2.5,2.5 0,1 1,0 -5z M5.5,11h1.5v2h-1.5z M17,11h1.5v2h-1.5z",
        ),
        pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
        fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
    ).build()
}

/** Today's pay on Today: what's earned so far, filling up live. Hidden until pay is set. */
@Composable
fun TodayMoneyCard(vm: MainViewModel, onOpen: () -> Unit) {
    val plan by vm.payPlan.collectAsStateWithLifecycle()
    // Every changed day, whatever month the Budget tab was left on.
    val changes by vm.workDays.collectAsStateWithLifecycle()
    val shown = plan?.takeIf { it.complete } ?: return
    val now by rememberNow(live = true)
    val pay = Pay.current(shown, now, changes.orEmpty())
    val hours = pay.hours
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onOpen).testTag("today-money")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💼", fontSize = 22.sp, modifier = Modifier.width(36.dp))
                Text(
                    when {
                        pay.paidOff -> "Day off, paid: ${moneyText(pay.total, alwaysCents = true)}"
                        hours == null -> "A day off"
                        else -> "${moneyText(pay.earned(now), alwaysCents = true)} of ${moneyText(pay.total, alwaysCents = true)} today"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hours != null) {
                val done = pay.done(now)
                LinearProgressIndicator(progress = { done.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp))
                Text(
                    when {
                        done <= 0.0 -> "Starts at ${timeText(minuteTime(hours.start))}"
                        done >= 1.0 -> "Done for the day"
                        else -> "${minutesText(pay.minutesLeft(now))} to go"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The budget game today: the day's share, what's spent against it, and the money streak. */
@Composable
private fun MoneyGameCard(game: MoneyGameView) {
    Card(Modifier.fillMaxWidth().testTag("money-game")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎮 Money game", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GuideButton(HelpTopic.BUDGET_GAME)
            }
            val share = game.share
            if (share == null) {
                Text(
                    "Set up your pay, or add money in, and each day gets its share of the month to stay within.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val shareCents = Budget.cents(share)
            val over = game.spentCents > shareCents
            Text(
                "Spent today ${centsText(game.spentCents)} of ${centsText(shareCents)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("money-game-today"),
            )
            LinearProgressIndicator(
                progress = { if (shareCents > 0) (game.spentCents.toFloat() / shareCents).coerceIn(0f, 1f) else 1f },
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    over -> "Over today's share: no points for today, and the streak starts again tomorrow."
                    game.spentCents == 0L -> "A no-spend day so far: +${io.github.randomtools3377.checklist.core.MoneyGame.NO_SPEND_DAY} if it stays that way, +${io.github.randomtools3377.checklist.core.MoneyGame.GOOD_DAY} within the share."
                    else -> "${centsText(shareCents - game.spentCents)} left today: +${io.github.randomtools3377.checklist.core.MoneyGame.GOOD_DAY} if it stays within."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (game.streak > 0) {
                Text("🔥 ${game.streak} good money day${if (game.streak == 1) "" else "s"} in a row", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("money-streak"))
            }
            Text(
                "Points come the next day. A month within every limit pays +${io.github.randomtools3377.checklist.core.MoneyGame.MONTH_BONUS}, and reaching a saving goal +${io.github.randomtools3377.checklist.core.MoneyGame.SAVED_UP}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
