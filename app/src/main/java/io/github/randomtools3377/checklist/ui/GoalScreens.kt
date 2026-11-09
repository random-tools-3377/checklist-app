@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import io.github.randomtools3377.checklist.core.Colors
import io.github.randomtools3377.checklist.core.Amounts
import androidx.compose.runtime.produceState
import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.describe
import io.github.randomtools3377.checklist.core.lengthOfMonth
import io.github.randomtools3377.checklist.core.monthShort
import io.github.randomtools3377.checklist.core.ordinal
import io.github.randomtools3377.checklist.core.weekdayShort
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.reminders.Reminders
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.TodoDraft
import io.github.randomtools3377.checklist.core.Todos
import io.github.randomtools3377.checklist.core.Difficulty
import io.github.randomtools3377.checklist.core.Suggest
import io.github.randomtools3377.checklist.core.Effort
import io.github.randomtools3377.checklist.core.GoalSearch
import androidx.compose.material.icons.filled.Search
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toMinuteOfDay
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import io.github.randomtools3377.checklist.core.Pace
import kotlinx.datetime.LocalTime

// The list of goals

@Composable
fun GoalsScreen(
    view: GoalsView?,
    reordering: Boolean,
    onEdit: (Goal) -> Unit,
    onMove: (Goal, Int) -> Unit,
    onUnlock: () -> Unit,
    padding: PaddingValues,
    removed: Int = 0,
    onRemoved: () -> Unit = {},
    doneCount: Int = 0,
    onDoneList: () -> Unit = {},
) {
    if (view == null) return
    // Search: every word typed has to be in a goal's name or category. Not while reordering.
    var query by rememberSaveable { mutableStateOf("") }
    val found = if (reordering) view.goals else GoalSearch.filter(view.goals, query)
    val habits = found.filter { it.kind == GoalKind.HABIT }
    val projects = found.filter { it.kind == GoalKind.PROJECT }
    // Open to-dos: ones without a date, and ones due today or later. Past ones stay in the history.
    val todos = found.filter { goal ->
        goal.kind == GoalKind.TODO && Todos.dateOf(goal).let { it == null || view.today == null || it >= view.today }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
    ) {
        if (view.goals.isEmpty()) {
            item {
                Text(
                    "No goals yet. Add a habit that repeats, like drinking water every day, a project made of steps, or a quick to-do.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
        if (view.hiddenPrivate > 0) {
            item {
                OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (view.hiddenPrivate == 1) "🔒 1 private goal · Unlock" else "🔒 ${view.hiddenPrivate} private goals · Unlock")
                }
            }
        }
        if (!reordering && view.goals.size > 1) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    placeholder = { Text("Search goals") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear the search") } }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("goal-search"),
                )
            }
            if (habits.isEmpty() && projects.isEmpty() && todos.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        "No goals match \"${query.trim()}\".",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("no-match"),
                    )
                }
            }
        }
        if (reordering && view.goals.isNotEmpty()) {
            item {
                Text(
                    "Use the arrows to change the order. \"My order\" on Today follows it.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        goalSection("Habits", habits, view, reordering, onEdit, onMove)
        goalSection("Projects", projects, view, reordering, onEdit, onMove)
        goalSection("To-dos", todos, view, reordering, onEdit, onMove)
        if (doneCount > 0 && !reordering) {
            item {
                TextButton(onClick = onDoneList, modifier = Modifier.padding(top = 8.dp).testTag("done-list")) {
                    Text("Done list · $doneCount")
                }
            }
        }
        if (removed > 0 && !reordering) {
            item {
                TextButton(onClick = onRemoved, modifier = Modifier.padding(top = 8.dp).testTag("removed-goals")) {
                    Text(if (removed == 1) "1 removed goal" else "$removed removed goals")
                }
            }
        }
    }
}

/** Finished projects and done to-dos, newest first. Tap one to open it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DoneListScreen(items: List<io.github.randomtools3377.checklist.core.DoneItem>, onOpen: (Goal) -> Unit, onBack: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Done") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("done-items"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            if (items.isEmpty()) item { Text("Nothing finished yet.", modifier = Modifier.padding(top = 24.dp)) }
            items(items, key = { it.goal.id }) { done ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(done.goal) }.padding(vertical = 8.dp).testTag("done:${done.goal.name}"),
                ) {
                    Text(done.goal.icon, fontSize = 26.sp, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(done.goal.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            (if (done.goal.kind == GoalKind.PROJECT) "Project" else "To-do") + " · done " + shortDate(done.day),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Goals the user removed, each with a button to bring it back. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RemovedGoalsScreen(goals: List<Goal>, onRestore: (Goal) -> Unit, onBack: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Removed goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            if (goals.isEmpty()) item { Text("Nothing removed.", modifier = Modifier.padding(top = 24.dp)) }
            items(goals, key = { it.id }) { goal ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(goal.icon, fontSize = 26.sp, modifier = Modifier.width(40.dp))
                    Text(goal.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRestore(goal) }, modifier = Modifier.testTag("restore:${goal.name}")) { Text("Bring back") }
                }
            }
        }
    }
}

private fun LazyListScope.goalSection(
    title: String,
    goals: List<Goal>,
    view: GoalsView,
    reordering: Boolean,
    onEdit: (Goal) -> Unit,
    onMove: (Goal, Int) -> Unit,
) {
    if (goals.isEmpty()) return
    item { SectionTitle(title) }
    itemsIndexed(goals, key = { _, goal -> goal.id }) { index, goal ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clickable(enabled = !reordering) { onEdit(goal) }
                .testTag("goal:${goal.name}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.goalStrip(goal.color).padding(12.dp)) {
                Text(goal.icon, fontSize = 28.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        describe(goal, view.steps[goal.id].orEmpty().size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reordering) {
                    IconButton(onClick = { onMove(goal, -1) }, enabled = index > 0, modifier = Modifier.testTag("up:${goal.name}")) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${goal.name} up")
                    }
                    IconButton(onClick = { onMove(goal, 1) }, enabled = index < goals.lastIndex, modifier = Modifier.testTag("down:${goal.name}")) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${goal.name} down")
                    }
                }
            }
        }
    }
}

private fun describe(goal: Goal, stepCount: Int): String = when (goal.kind) {
    GoalKind.HABIT -> buildList {
        add("+${goal.points}")
        goal.repeat?.let { add(it.describe(goal.target)) }
        if (goal.repeat?.kind != RepeatKind.TIMES_PER && goal.target > 1) add("${goal.target} times")
        goal.amount?.let { add(Amounts.format(it) + (goal.unit?.let { u -> " $u" } ?: "")) }
        goal.at?.let { add("at ${timeText(it)}") }
        if (goal.times.isNotEmpty()) add("at " + goal.times.sorted().joinToString(", ") { timeText(it) })
        goal.minutes?.let { add(minutesText(it)) }
    }.joinToString(" · ")
    GoalKind.PROJECT -> buildList {
        add(if (stepCount == 1) "1 step" else "$stepCount steps")
        if (goal.bonus > 0) add("bonus +${goal.bonus}")
        goal.deadline?.let { add("due ${shortDate(it)}") }
    }.joinToString(" · ")
    GoalKind.TODO -> buildList {
        add("+${goal.points}")
        if (stepCount > 0) add(if (stepCount == 1) "1 step" else "$stepCount steps")
        add(Todos.dateOf(goal)?.let { "due ${shortDate(it)}" } ?: "no date")
        goal.reminder?.at?.let { add("reminder ${timeText(it)}") }
    }.joinToString(" · ")
}

/** Asks whether the new goal is a habit or a project. */
@Composable
fun NewGoalDialog(onHabit: () -> Unit, onProject: () -> Unit, onDismiss: () -> Unit, onTodo: () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHabit, modifier = Modifier.fillMaxWidth().testTag("new-habit")) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("Habit", style = MaterialTheme.typography.titleMedium)
                        Text("Repeats: every day, chosen days, so many times a week, and more", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onProject, modifier = Modifier.fillMaxWidth().testTag("new-project")) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("Project", style = MaterialTheme.typography.titleMedium)
                        Text("Done once, step by step", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onTodo, modifier = Modifier.fillMaxWidth().testTag("new-todo")) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("To-do", style = MaterialTheme.typography.titleMedium)
                        Text("A one-off task, with or without a date", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Editors

@Composable
private fun RemoveButton(what: String, name: String, onRemove: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, modifier = Modifier.testTag("remove")) {
        Text("Remove this $what", color = MaterialTheme.colorScheme.error)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Remove $name?") },
            text = { Text("It disappears from your lists. ${pointsTitle()} it already earned stay in your balance.") },
            confirmButton = {
                TextButton(onClick = { asking = false; onRemove() }, modifier = Modifier.testTag("confirm-remove")) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
}

/** The choices in "How often", as the user sees them. */
private enum class RepeatChoice(val label: String) {
    EVERY_DAY("Every day"),
    EVERY_FEW_DAYS("Every few days"),
    WEEKDAYS("Days of the week"),
    TIMES_PER("So many times a week, month or year"),
    MONTH_DATES("Dates of the month"),
    MONTH_WEEKDAY("A weekday of the month, like the first Monday"),
    YEARLY("Once a year, on a date"),
    ONCE("Once, on a date"),
}

private val WeekOfMonthChoices = listOf(1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth", Repeat.LAST_WEEK to "Last")

@Composable
fun HabitEditor(id: Long, vm: MainViewModel, onClose: () -> Unit, onOpen: (Long) -> Unit = {}) {
    val today = remember { nowLocal().date }
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(GOAL_ICON) }
    var points by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("1") }
    var bonus by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableStateOf(RepeatChoice.EVERY_DAY) }
    var interval by rememberSaveable { mutableStateOf("2") }
    var weekdays by rememberSaveable { mutableStateOf(listOf(today.dayOfWeek.isoDayNumber)) }
    var per by rememberSaveable { mutableStateOf(Period.WEEKLY) }
    var monthDays by rememberSaveable { mutableStateOf(listOf(today.dayOfMonth)) }
    var weekOfMonth by rememberSaveable { mutableStateOf(1) }
    var yearMonth by rememberSaveable { mutableStateOf(today.monthNumber) }
    var yearDay by rememberSaveable { mutableStateOf(today.dayOfMonth.toString()) }
    var startDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var atMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    // A time for each one, for a habit done several times a day; -1 where not set yet.
    var eachTimeOn by rememberSaveable { mutableStateOf(false) }
    // A routine's checklist.
    val items = rememberSaveable(saver = StepFieldsSaver) { mutableListOf<StepField>().toMutableStateList() }
    var nextItemKey by rememberSaveable { mutableStateOf(1L) }
    var eachTimes by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var finishByMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var finishDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var notifyOnTime by rememberSaveable { mutableStateOf(true) }
    var alertBefore by rememberSaveable { mutableStateOf<Int?>(null) }
    var remindAtMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var keepReminding by rememberSaveable { mutableStateOf(false) }
    var allowance by rememberSaveable { mutableStateOf(Goal.DEFAULT_ALLOWANCE.toString()) }
    var allowancePer by rememberSaveable { mutableStateOf(Period.WEEKLY) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var celebration by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf<Int?>(null) }
    // An amount in the user's own unit, like 20 pages.
    var amountText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    // How hard it is, by name, which suggests its points.
    var effort by rememberSaveable { mutableStateOf<String?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var choosingRepeat by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onClose() }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadGoal(id)?.let { (goal, existingItems) ->
                existingItems.forEach { items += StepField(nextItemKey++, it.id, it.name, "0") }
                name = goal.name
                icon = goal.icon
                points = goal.points.toString()
                target = goal.target.toString()
                bonus = if (goal.bonus > 0) goal.bonus.toString() else ""
                minutes = goal.minutes?.toString() ?: ""
                atMinute = goal.at?.toMinuteOfDay()
                if (goal.times.isNotEmpty()) {
                    eachTimeOn = true
                    eachTimes = goal.times.map { it.toMinuteOfDay() }
                } else if (goal.severalADay && goal.at != null) {
                    // One time for a goal done several times a day becomes its first time.
                    eachTimeOn = true
                    eachTimes = listOf(goal.at!!.toMinuteOfDay())
                }
                finishByMinute = goal.finishBy?.toMinuteOfDay()
                finishDay = goal.finishDay
                allowance = goal.allowed.toString()
                allowancePer = goal.allowanceWindow
                isPrivate = goal.private
                celebration = goal.celebration
                category = goal.category.orEmpty()
                color = goal.color
                amountText = goal.amount?.let { Amounts.format(it) }.orEmpty()
                unit = goal.unit.orEmpty()
                effort = goal.effort?.name
                goal.reminder?.let { r ->
                    notifyOnTime = r.onTime
                    alertBefore = r.minutesBefore
                    remindAtMinute = r.at?.toMinuteOfDay()
                    keepReminding = r.againEvery != null
                }
                goal.repeat?.let { r ->
                    startDay = r.start.toEpochDay()
                    endDay = r.end?.toEpochDay()
                    interval = r.interval.coerceAtLeast(1).toString()
                    choice = when (r.kind) {
                        RepeatKind.EVERY_N_DAYS -> if (r.interval <= 1) RepeatChoice.EVERY_DAY else RepeatChoice.EVERY_FEW_DAYS
                        RepeatKind.WEEKDAYS -> RepeatChoice.WEEKDAYS
                        RepeatKind.TIMES_PER -> RepeatChoice.TIMES_PER
                        RepeatKind.MONTH_DATES -> RepeatChoice.MONTH_DATES
                        RepeatKind.MONTH_WEEKDAY -> RepeatChoice.MONTH_WEEKDAY
                        RepeatKind.YEARLY -> RepeatChoice.YEARLY
                        RepeatKind.ONCE -> RepeatChoice.ONCE
                    }
                    if (r.weekdays.isNotEmpty()) weekdays = r.weekdays.sorted()
                    if (r.monthDays.isNotEmpty()) monthDays = r.monthDays.sorted()
                    r.per?.let { per = it }
                    weekOfMonth = r.weekOfMonth
                    yearMonth = r.month
                    yearDay = r.day.toString()
                }
            }
            loaded = true
        }
    }

    val intervalValue = interval.toIntOrNull()
    val needsInterval = choice in setOf(RepeatChoice.EVERY_FEW_DAYS, RepeatChoice.WEEKDAYS, RepeatChoice.MONTH_DATES, RepeatChoice.MONTH_WEEKDAY)
    val yearDayValue = yearDay.toIntOrNull()
    val repeat: Repeat? = run {
        val start = startDay.toDay()
        val end = endDay?.toDay()
        val n = if (needsInterval) intervalValue?.takeIf { it in 1..366 } ?: return@run null else 1
        when (choice) {
            RepeatChoice.EVERY_DAY -> Repeat(RepeatKind.EVERY_N_DAYS, start, end)
            RepeatChoice.EVERY_FEW_DAYS -> Repeat(RepeatKind.EVERY_N_DAYS, start, end, interval = n)
            RepeatChoice.WEEKDAYS ->
                if (weekdays.isEmpty()) null else Repeat(RepeatKind.WEEKDAYS, start, end, interval = n, weekdays = weekdays.toSet())
            RepeatChoice.TIMES_PER -> Repeat(RepeatKind.TIMES_PER, start, end, per = per)
            RepeatChoice.MONTH_DATES ->
                if (monthDays.isEmpty()) null else Repeat(RepeatKind.MONTH_DATES, start, end, interval = n, monthDays = monthDays.toSet())
            RepeatChoice.MONTH_WEEKDAY -> Repeat(
                RepeatKind.MONTH_WEEKDAY, start, end, interval = n, weekdays = setOf(weekdays.firstOrNull() ?: 1), weekOfMonth = weekOfMonth,
            )
            RepeatChoice.YEARLY ->
                if (yearDayValue == null || yearDayValue !in 1..lengthOfMonth(2024, yearMonth)) null
                else Repeat(RepeatKind.YEARLY, start, end, month = yearMonth, day = yearDayValue)
            RepeatChoice.ONCE -> Repeat(RepeatKind.ONCE, start)
        }
    }

    val nameError = if (name.isBlank()) "Give it a name" else null
    val pointsError = if (points.toIntOrNull() == null) "How many ${pointsWord()} it pays" else null
    val targetValue = target.toIntOrNull()
    val amountValue = Amounts.parse(amountText)
    val hasAmount = amountText.isNotBlank()
    val amountError = when {
        !hasAmount -> null
        amountValue == null -> "A number above 0, like 20 or 1.5"
        unit.isBlank() -> "Name the unit, like pages or miles"
        else -> null
    }
    val routine = !hasAmount && items.any { it.name.isNotBlank() }
    val targetError = if (!routine && (targetValue == null || targetValue !in 1..999)) "From 1 to 999" else null
    val end = endDay
    val repeatError = when {
        repeat == null -> "Finish choosing how often"
        end != null && end < startDay -> "The end date is before the start"
        else -> null
    }
    val minutesValue = minutes.toIntOrNull()?.takeIf { it > 0 }
    val counted = !routine && (targetValue ?: 1) > 1
    // Done several times on each day it's due: it can have a time for each one.
    val severalADay = counted && choice != RepeatChoice.TIMES_PER
    val eachTimesSet = if (severalADay && eachTimeOn) eachTimes.filter { it >= 0 }.distinct().sorted().take(targetValue ?: 1) else emptyList()

    EditorScaffold(
        title = if (id == 0L) "New habit" else "Edit habit",
        onBack = onClose,
        onSave = {
            if (nameError != null || pointsError != null || targetError != null || repeatError != null || amountError != null || repeat == null) {
                showErrors = true
            } else {
                vm.saveHabit(
                    HabitDraft(
                        id = id,
                        name = name.trim(),
                        icon = icon,
                        points = points.toInt(),
                        repeat = repeat,
                        target = if (routine) 1 else targetValue ?: 1,
                        bonus = if (counted) bonus.toIntOrNull() ?: 0 else 0,
                        minutes = minutesValue,
                        at = if (severalADay) null else atMinute?.toTime(),
                        times = eachTimesSet.map { it.toTime() },
                        items = if (hasAmount) emptyList() else items.filter { it.name.isNotBlank() }.map { StepDraft(it.id, it.name.trim(), 0) },
                        amount = if (hasAmount) amountValue else null,
                        unit = if (hasAmount) unit.trim() else null,
                        effort = effort?.let { Effort.valueOf(it) },
                        finishBy = finishByMinute?.toTime(),
                        finishDay = if (choice == RepeatChoice.TIMES_PER) finishDay else null,
                        reminder = Reminder(
                            onTime = notifyOnTime,
                            minutesBefore = if (atMinute != null || eachTimesSet.isNotEmpty()) alertBefore else null,
                            at = if (atMinute == null && eachTimesSet.isEmpty()) remindAtMinute?.toTime() else null,
                            againEvery = if (keepReminding) Reminder.AGAIN_EVERY else null,
                        ),
                        allowance = allowance.toIntOrNull() ?: Goal.DEFAULT_ALLOWANCE,
                        allowancePer = allowancePer,
                        private = isPrivate,
                        celebration = celebration,
                        category = category,
                        color = color,
                    ),
                    onDone = {
                        val wantsNotifications = when {
                            eachTimesSet.isNotEmpty() || (!severalADay && atMinute != null) -> notifyOnTime || alertBefore != null
                            else -> remindAtMinute != null
                        }
                        if (wantsNotifications && !Reminders.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onClose()
                        }
                    },
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
            label = "${pointsTitle()} for each tick",
            value = points,
            onValueChange = { points = it },
            tag = "field:points",
            error = if (showErrors) pointsError else null,
        )
        EffortField(
            effort = effort?.let { Effort.valueOf(it) },
            onEffort = { effort = it?.name },
            suggest = { e -> Suggest.points(e, minutesValue, target.toIntOrNull()?.takeIf { it > 0 } ?: 1) },
            points = points,
            onPoints = { points = it },
            vm = vm,
        )
        CategoryField(category, { category = it }, vm)
        GoalColourField(color, { color = it }, vm)

        // How often
        Text("How often", style = MaterialTheme.typography.titleSmall)
        OutlinedCard(Modifier.fillMaxWidth().clickable { choosingRepeat = true }.testTag("repeat-kind")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    repeat?.let { Text(it.describe(targetValue ?: 1), color = MaterialTheme.colorScheme.primary) }
                }
                Text("Change", color = MaterialTheme.colorScheme.primary)
            }
        }
        when (choice) {
            RepeatChoice.EVERY_DAY -> Unit
            RepeatChoice.EVERY_FEW_DAYS -> NumberField("Every how many days", interval, { interval = it }, "field:interval")
            RepeatChoice.WEEKDAYS -> {
                WeekdayChips(weekdays.toSet(), single = false) { day ->
                    weekdays = if (day in weekdays) weekdays - day else (weekdays + day).sorted()
                }
                Row {
                    TextButton(onClick = { weekdays = (1..5).toList() }) { Text("Weekdays") }
                    TextButton(onClick = { weekdays = listOf(6, 7) }) { Text("Weekends") }
                    TextButton(onClick = { weekdays = (1..7).toList() }) { Text("All") }
                }
                NumberField("Every how many weeks", interval, { interval = it }, "field:interval", hint = "1 for every week, 2 for every other week")
            }
            RepeatChoice.TIMES_PER -> {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(Period.WEEKLY to "Week", Period.MONTHLY to "Month", Period.YEARLY to "Year")
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = per == value,
                            onClick = {
                                per = value
                                finishDay = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            modifier = Modifier.testTag("per:$label"),
                        ) { Text(label) }
                    }
                }
            }
            RepeatChoice.MONTH_DATES -> {
                MonthDayChips(monthDays.toSet()) { day ->
                    monthDays = if (day in monthDays) monthDays - day else (monthDays + day).sorted()
                }
                NumberField("Every how many months", interval, { interval = it }, "field:interval", hint = "1 for every month")
            }
            RepeatChoice.MONTH_WEEKDAY -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekOfMonthChoices.forEach { (value, label) ->
                        FilterChip(selected = weekOfMonth == value, onClick = { weekOfMonth = value }, label = { Text(label) })
                    }
                }
                WeekdayChips(setOf(weekdays.firstOrNull() ?: 1), single = true) { weekdays = listOf(it) }
                NumberField("Every how many months", interval, { interval = it }, "field:interval", hint = "1 for every month")
            }
            RepeatChoice.YEARLY -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..12).forEach { m ->
                        FilterChip(selected = yearMonth == m, onClick = { yearMonth = m }, label = { Text(monthShort(m)) })
                    }
                }
                NumberField("Day of the month", yearDay, { yearDay = it }, "field:year-day")
            }
            RepeatChoice.ONCE -> Unit
        }
        if (choice == RepeatChoice.ONCE) {
            DateSetting("On", startDay.toDay(), { picked -> picked?.let { startDay = it.toEpochDay() } }, "field:start", canClear = false)
        } else {
            DateSetting("Starts", startDay.toDay(), { picked -> picked?.let { startDay = it.toEpochDay() } }, "field:start", canClear = false)
            DateSetting("Ends", endDay?.toDay(), { endDay = it?.toEpochDay() }, "field:end", emptyText = "No end date")
        }
        if (showErrors && repeatError != null) {
            Text(repeatError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        val perWord = when (per) {
            Period.MONTHLY -> "month"
            Period.YEARLY -> "year"
            else -> "week"
        }
        // An amount in the user's own unit, like 20 pages or 1 mile.
        Text("Amount (optional)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { typed -> amountText = typed.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                label = { Text("How much") },
                singleLine = true,
                isError = showErrors && amountError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("field:amount"),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it.take(Amounts.UNIT_LENGTH) },
                label = { Text("Unit") },
                placeholder = { Text("pages") },
                singleLine = true,
                isError = showErrors && amountError != null,
                modifier = Modifier.weight(1f).testTag("field:unit"),
            )
        }
        Text(
            if (showErrors && amountError != null) amountError
            else "Like 20 pages, 3 chapters or 1 mile. You enter how much you did as you go, and it's done, and pays its ${pointsWord()}, when this is reached. Done more than once (below), each time needs the whole amount.",
            style = MaterialTheme.typography.bodySmall,
            color = if (showErrors && amountError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!routine) {
            NumberField(
                label = if (choice == RepeatChoice.TIMES_PER) "Times per $perWord" else "Times each day it's due",
                value = target,
                onValueChange = { target = it },
                tag = "field:target",
                error = if (showErrors) targetError else null,
                hint = if (choice == RepeatChoice.TIMES_PER) null else "More than 1 makes it a counted goal, like 8 glasses of water",
            )
        }
        if (counted) {
            NumberField(
                label = "Bonus for finishing all of them",
                value = bonus,
                onValueChange = { bonus = it },
                tag = "field:bonus",
                hint = "Paid on top of each tick",
            )
        }

        // A checklist inside makes it a routine.
        if (!hasAmount) {
        HorizontalDivider()
        Text("Checklist (optional)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Add items to make it a routine, like water, stretch, make bed. You tick them one by one, and it's done, and pays its ${pointsWord()}, when every item is ticked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEachIndexed { index, item ->
            key(item.key) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { items[index] = item.copy(name = it.take(60)) },
                        label = { Text("Item ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field:item-$index"),
                    )
                    IconButton(onClick = { items.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove item ${index + 1}")
                    }
                }
            }
        }
        TextButton(onClick = { items += StepField(nextItemKey++, 0L, "", "0") }, modifier = Modifier.testTag("add-item")) {
            Text(if (items.isEmpty()) "Add a checklist" else "Add an item")
        }
        }

        // Time
        HorizontalDivider()
        Text("Time (optional)", style = MaterialTheme.typography.titleSmall)
        if (hasAmount) {
            val speed by produceState<Pair<Double, Int>?>(null, id) { value = if (id != 0L) vm.usualSpeed(id) else null }
            val usual = speed?.let { (rate, timings) ->
                amountValue?.let { whole ->
                    " Your usual: ${Amounts.format(whole)} ${unit.trim()} in about ${minutesText(kotlin.math.ceil(rate * whole).toInt().coerceAtLeast(1))}" +
                        (if (timings == 1) ", from 1 timing." else ", from $timings timings.")
                }
            }.orEmpty()
            NumberField(
                label = if ((targetValue ?: 1) > 1) "Time to budget for each one, in minutes" else "Time to budget for all of it, in minutes",
                value = minutes,
                onValueChange = { minutes = it },
                tag = "field:minutes",
                hint = "Leave it empty and the app learns how long it takes when you time it with Start and Stop on Today.$usual",
            )
        } else {
            NumberField(
                label = "How long it takes, in minutes",
                value = minutes,
                onValueChange = { minutes = it },
                tag = "field:minutes",
                hint = "Pacing keeps this much time for it. Leave empty to share the free time evenly.",
            )
        }
        if (severalADay) {
            EachTimeSetting(
                dayStart = remember { vm.settings.value.dayStart },
                count = targetValue ?: 1,
                on = eachTimeOn,
                onOn = { on ->
                    eachTimeOn = on
                    if (on && eachTimes.isEmpty()) eachTimes = List(targetValue ?: 1) { -1 }
                },
                times = eachTimes,
                onTimes = { eachTimes = it },
            )
        } else {
            TimeSetting("At a set time", atMinute?.toTime(), { atMinute = it?.toMinuteOfDay() }, "field:at", emptyText = "Any time")
        }
        TimeSetting(
            "Finish by",
            finishByMinute?.toTime(),
            { finishByMinute = it?.toMinuteOfDay() },
            "field:finish-by",
            emptyText = "Your default from Settings",
        )
        if (choice == RepeatChoice.TIMES_PER) {
            FinishDayPicker(per, finishDay) { finishDay = it }
        }

        // Reminders
        HorizontalDivider()
        Text("Reminders", style = MaterialTheme.typography.titleSmall)
        val clock = if (severalADay) null else atMinute
        if (clock != null || eachTimesSet.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (clock != null) "Notify at ${timeText(clock.toTime())}" else "Notify at each time",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = notifyOnTime, onCheckedChange = { notifyOnTime = it }, modifier = Modifier.testTag("field:notify-on-time"))
            }
            Text("Also alert before", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null, 5, 10, 15, 30, 60).forEach { minutes ->
                    FilterChip(
                        selected = alertBefore == minutes,
                        onClick = { alertBefore = minutes },
                        label = { Text(minutes?.let { "$it min" } ?: "No") },
                    )
                }
            }
        } else {
            TimeSetting(
                "Remind me at",
                remindAtMinute?.toTime(),
                { remindAtMinute = it?.toMinuteOfDay() },
                "field:remind-at",
                emptyText = "No reminder",
                hint = if (severalADay) "Or set a time for each one above, and it notifies at each." else "Or give it a set time above, and it notifies at that time.",
            )
        }
        val hasReminder = (clock != null && notifyOnTime) || (eachTimesSet.isNotEmpty() && notifyOnTime) || (clock == null && eachTimesSet.isEmpty() && remindAtMinute != null)
        if (hasReminder) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Keep reminding until it's done", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Again every ${Reminder.AGAIN_EVERY} minutes, until it's done or its finish time passes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keepReminding, onCheckedChange = { keepReminding = it }, modifier = Modifier.testTag("field:keep-reminding"))
            }
        }
        AllowanceAndPrivacy(
            allowance = allowance,
            onAllowance = { allowance = it },
            per = allowancePer,
            onPer = { allowancePer = it },
            isPrivate = isPrivate,
            onPrivate = { isPrivate = it },
        )
        Text(
            "Changes apply from now on. ${pointsTitle()} already earned stay as they were.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CelebrationPicker(celebration) { celebration = it }
        if (id != 0L) {
            TextButton(onClick = { vm.duplicateGoal(id, onOpen) }, modifier = Modifier.testTag("duplicate")) { Text("Make a copy of this habit") }
            RemoveButton("habit", name) { vm.removeGoal(id, onClose) }
        }
    }

    if (choosingRepeat) {
        AlertDialog(
            onDismissRequest = { choosingRepeat = false },
            title = { Text("How often") },
            text = {
                Column {
                    RepeatChoice.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choice = option
                                    if (option == RepeatChoice.MONTH_WEEKDAY) weekdays = listOf(weekdays.firstOrNull() ?: 1)
                                    choosingRepeat = false
                                }
                                .testTag("repeat:${option.name}"),
                        ) {
                            RadioButton(selected = option == choice, onClick = null)
                            Text(option.label, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingRepeat = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun WeekdayChips(selected: Set<Int>, single: Boolean, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { if (!single || day !in selected) onPick(day) },
                label = { Text(weekdayShort(day)) },
                modifier = Modifier.testTag("weekday:$day"),
            )
        }
    }
}

@Composable
private fun MonthDayChips(selected: Set<Int>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..31).forEach { day ->
            FilterChip(selected = day in selected, onClick = { onToggle(day) }, label = { Text("$day") })
        }
        FilterChip(selected = Repeat.LAST_DAY in selected, onClick = { onToggle(Repeat.LAST_DAY) }, label = { Text("Last day") })
    }
}

/** For "so many times a week, month or year": the day to finish them by, which turns pacing on. */
@Composable
private fun FinishDayPicker(per: Period, finishDay: Int?, onChange: (Int?) -> Unit) {
    Text("Finish them by", style = MaterialTheme.typography.bodyLarge)
    Text(
        "Optional. With a finish day, Today shows how often to do it to get there.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (per) {
        Period.MONTHLY -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = finishDay == null, onClick = { onChange(null) }, label = { Text("No finish day") })
                (1..31).forEach { day ->
                    FilterChip(selected = finishDay == day, onClick = { onChange(day) }, label = { Text(ordinal(day)) })
                }
                FilterChip(selected = finishDay == Repeat.LAST_DAY, onClick = { onChange(Repeat.LAST_DAY) }, label = { Text("Last day") })
            }
        }
        Period.YEARLY -> {
            DateSetting(
                "Each year by",
                finishDay?.let { LocalDate(2024, (it / 100).coerceIn(1, 12), (it % 100).coerceIn(1, 28)) },
                { date -> onChange(date?.let { it.monthNumber * 100 + it.dayOfMonth }) },
                "field:finish-day",
                emptyText = "No finish day",
            )
        }
        else -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = finishDay == null, onClick = { onChange(null) }, label = { Text("No finish day") })
                (1..7).forEach { day ->
                    FilterChip(selected = finishDay == day, onClick = { onChange(day) }, label = { Text(weekdayShort(day)) })
                }
            }
        }
    }
}

/** A step as the project editor holds it. [key] tells rows apart while editing. */
private data class StepField(val key: Long, val id: Long, val name: String, val points: String)

private val StepFieldsSaver = listSaver<MutableList<StepField>, Any>(
    save = { list -> list.flatMap { listOf(it.key, it.id, it.name, it.points) } },
    restore = { flat ->
        flat.chunked(4).map { StepField(it[0] as Long, it[1] as Long, it[2] as String, it[3] as String) }.toMutableStateList()
    },
)

@Composable
fun ProjectEditor(id: Long, vm: MainViewModel, onClose: () -> Unit, onOpen: (Long) -> Unit = {}) {
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(GOAL_ICON) }
    var bonus by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf<Long?>(null) }
    var finishByMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var allowance by rememberSaveable { mutableStateOf(Goal.DEFAULT_ALLOWANCE.toString()) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var celebration by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf<Int?>(null) }
    var effort by rememberSaveable { mutableStateOf<String?>(null) }
    var nextKey by rememberSaveable { mutableStateOf(2L) }
    val steps = rememberSaveable(saver = StepFieldsSaver) { mutableListOf(StepField(1L, 0L, "", "")).toMutableStateList() }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadGoal(id)?.let { (goal, existingSteps) ->
                name = goal.name
                icon = goal.icon
                bonus = if (goal.bonus > 0) goal.bonus.toString() else ""
                deadline = goal.deadline?.toEpochDay()
                finishByMinute = goal.finishBy?.toMinuteOfDay()
                allowance = goal.allowed.toString()
                isPrivate = goal.private
                celebration = goal.celebration
                category = goal.category.orEmpty()
                color = goal.color
                effort = goal.effort?.name
                steps.clear()
                existingSteps.forEach { steps += StepField(nextKey++, it.id, it.name, it.points.toString()) }
                if (steps.isEmpty()) steps += StepField(nextKey++, 0L, "", "")
            }
            loaded = true
        }
    }

    val filled = steps.filter { it.name.isNotBlank() }
    val nameError = if (name.isBlank()) "Give it a name" else null
    val stepsError = when {
        filled.isEmpty() -> "Add at least one step"
        filled.any { it.points.toIntOrNull() == null } -> "Each step needs its ${pointsWord()}"
        else -> null
    }

    EditorScaffold(
        title = if (id == 0L) "New project" else "Edit project",
        onBack = onClose,
        onSave = {
            if (nameError != null || stepsError != null) {
                showErrors = true
            } else {
                vm.saveProject(
                    ProjectDraft(
                        id = id,
                        name = name.trim(),
                        icon = icon,
                        steps = filled.map { StepDraft(it.id, it.name.trim(), it.points.toInt()) },
                        bonus = bonus.toIntOrNull() ?: 0,
                        deadline = deadline?.toDay(),
                        finishBy = finishByMinute?.toTime(),
                        allowance = allowance.toIntOrNull() ?: Goal.DEFAULT_ALLOWANCE,
                        private = isPrivate,
                        celebration = celebration,
                        category = category,
                        color = color,
                        effort = effort?.let { Effort.valueOf(it) },
                    ),
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
        CategoryField(category, { category = it }, vm)
        GoalColourField(color, { color = it }, vm)
        Text("Steps", style = MaterialTheme.typography.titleSmall)
        steps.forEachIndexed { index, step ->
            key(step.key) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = step.name,
                        onValueChange = { steps[index] = step.copy(name = it.take(60)) },
                        label = { Text("Step ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field:step-name-$index"),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = step.points,
                        onValueChange = { text -> steps[index] = step.copy(points = text.filter { it.isDigit() }.take(6)) },
                        label = { Text(pointsTitle()) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp).testTag("field:step-points-$index"),
                    )
                    IconButton(
                        onClick = { steps.removeAt(index) },
                        enabled = steps.size > 1,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove step ${index + 1}")
                    }
                }
            }
        }
        if (showErrors && stepsError != null) {
            Text(stepsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(
            onClick = { steps += StepField(nextKey++, 0L, "", steps.lastOrNull()?.points ?: "") },
            modifier = Modifier.testTag("add-step"),
        ) { Text("Add a step") }
        // One difficulty for the steps: its suggestion goes in every step's points.
        EffortField(
            effort = effort?.let { Effort.valueOf(it) },
            onEffort = { effort = it?.name },
            suggest = { e -> Suggest.points(e) },
            points = steps.map { it.points }.distinct().singleOrNull().orEmpty(),
            onPoints = { value -> steps.indices.forEach { i -> steps[i] = steps[i].copy(points = value) } },
            vm = vm,
            label = "How hard are the steps?",
        )

        NumberField(
            label = "Bonus for finishing the project",
            value = bonus,
            onValueChange = { bonus = it },
            tag = "field:bonus",
        )
        DateSetting("Deadline", deadline?.toDay(), { deadline = it?.toEpochDay() }, "field:deadline", emptyText = "No deadline")
        if (deadline != null) {
            TimeSetting(
                "Finish by on the day",
                finishByMinute?.toTime(),
                { finishByMinute = it?.toMinuteOfDay() },
                "field:finish-by",
                emptyText = "Your default from Settings",
            )
        }
        Text(
            "With a deadline, Today shows how many steps a day it takes to finish in time. Missing it counts as a miss.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (deadline != null) {
            AllowanceAndPrivacy(
                allowance = allowance,
                onAllowance = { allowance = it },
                per = null,
                onPer = {},
                isPrivate = isPrivate,
                onPrivate = { isPrivate = it },
            )
        } else {
            PrivateSwitch(isPrivate) { isPrivate = it }
        }
        CelebrationPicker(celebration) { celebration = it }
        if (id != 0L) {
            TextButton(onClick = { vm.duplicateGoal(id, onOpen) }, modifier = Modifier.testTag("duplicate")) { Text("Make a copy of this project") }
            RemoveButton("project", name) { vm.removeGoal(id, onClose) }
        }
    }
}

/**
 * For a habit done several times a day: a time for each one, or spread out by the app between
 * when the day starts and the finish time.
 */
@Composable
private fun EachTimeSetting(dayStart: LocalTime?, count: Int, on: Boolean, onOn: (Boolean) -> Unit, times: List<Int>, onTimes: (List<Int>) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("A time for each one", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (on) "Each gets its own reminder." else "Off: they're spread out evenly from when your day starts to the finish time, and Today shows when the next one is due.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onOn, modifier = Modifier.testTag("field:each-time"))
    }
    if (!on) return
    val shown = minOf(count, MAX_EACH_TIMES)
    // A quick fill: every few hours from the first time set, or when the day starts.
    val from = times.firstOrNull { it >= 0 }?.toTime() ?: dayStart ?: LocalTime(8, 0)
    Text("Fill in every few hours, from ${timeText(from)}:", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 2, 3, 4).forEach { hours ->
            FilterChip(
                selected = false,
                onClick = { onTimes(Pace.everyFewHours(from, hours, shown).map { it.toMinuteOfDay() }) },
                label = { Text(if (hours == 1) "Every hour" else "$hours h") },
                modifier = Modifier.testTag("fill:$hours"),
            )
        }
    }
    val padded = (times + List(maxOf(0, shown - times.size)) { -1 }).take(shown)
    padded.forEachIndexed { i, minute ->
        TimeSetting(
            "${ordinal(i + 1)} one",
            minute.takeIf { it >= 0 }?.toTime(),
            { time -> onTimes(padded.toMutableList().also { it[i] = time?.toMinuteOfDay() ?: -1 }) },
            "field:time-${i + 1}",
            emptyText = "Not set",
        )
    }
    if (count > MAX_EACH_TIMES) {
        Text(
            "Times can be set for the first $MAX_EACH_TIMES. The rest are any time before the finish.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Times for more than this many a day would make a very long form. */
private const val MAX_EACH_TIMES = 12

/** The frame shared by the editors: a top bar with Back and Save, and a scrolling form. */
/** A quick to-do: a name, its points, and an optional date. */
@Composable
fun TodoEditor(id: Long, vm: MainViewModel, onClose: () -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(Todos.ICON) }
    var points by rememberSaveable { mutableStateOf(Todos.POINTS.toString()) }
    var date by rememberSaveable { mutableStateOf<Long?>(null) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var remindAtMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var effort by rememberSaveable { mutableStateOf<String?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    // A date already in the past can stay as it is, but a new one can't be.
    var savedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    // Its steps, if any. Steps pay nothing; the to-do is done when every one is ticked.
    var nextKey by rememberSaveable { mutableStateOf(1L) }
    val steps = rememberSaveable(saver = StepFieldsSaver) { mutableListOf<StepField>().toMutableStateList() }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadGoal(id)?.let { (goal, existingSteps) ->
                name = goal.name
                icon = goal.icon
                points = goal.points.toString()
                date = Todos.dateOf(goal)?.toEpochDay()
                savedDate = date
                isPrivate = goal.private
                remindAtMinute = goal.reminder?.at?.toMinuteOfDay()
                effort = goal.effort?.name
                existingSteps.forEach { steps += StepField(nextKey++, it.id, it.name, "") }
            }
            loaded = true
        }
    }

    val today = vm.currentDay()
    val nameError = if (name.isBlank()) "Give it a name" else null
    val dateError = date?.takeIf { it != savedDate && it.toDay() < today }?.let { "Pick today or a later day" }

    EditorScaffold(
        title = if (id == 0L) "New to-do" else "Edit to-do",
        onBack = onClose,
        onSave = {
            if (nameError != null || dateError != null) {
                showErrors = true
            } else {
                vm.saveTodo(
                    TodoDraft(
                        id = id,
                        name = name.trim(),
                        icon = icon,
                        points = points.toIntOrNull() ?: 0,
                        date = date?.toDay(),
                        private = isPrivate,
                        steps = steps.filter { it.name.isNotBlank() }.map { StepDraft(it.id, it.name.trim(), 0) },
                        remindAt = remindAtMinute?.toTime(),
                        effort = effort?.let { Effort.valueOf(it) },
                    ),
                    onDone = onClose,
                )
            }
        },
    ) {
        if (!loaded) return@EditorScaffold
        IconPicker(icon) { icon = it }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("What to do") },
            singleLine = true,
            isError = showErrors && nameError != null,
            supportingText = if (showErrors && nameError != null) ({ Text(nameError) }) else null,
            modifier = Modifier.fillMaxWidth().testTag("field:name"),
        )
        NumberField(label = pointsTitle(), value = points, onValueChange = { points = it }, tag = "field:points")
        EffortField(
            effort = effort?.let { Effort.valueOf(it) },
            onEffort = { effort = it?.name },
            suggest = { e -> Suggest.points(e) },
            points = points,
            onPoints = { points = it },
            vm = vm,
        )
        Text("Steps", style = MaterialTheme.typography.titleSmall)
        Text(
            "Optional. Tick them off one by one, over as many days as it takes; the last one does the to-do.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { index, step ->
            key(step.key) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = step.name,
                        onValueChange = { steps[index] = step.copy(name = it.take(60)) },
                        label = { Text("Step ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field:step-name-$index"),
                    )
                    IconButton(onClick = { steps.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove step ${index + 1}")
                    }
                }
            }
        }
        TextButton(onClick = { steps += StepField(nextKey++, 0L, "", "") }, modifier = Modifier.testTag("add-step")) { Text("Add a step") }
        DateSetting("Due on", date?.toDay(), { date = it?.toEpochDay() }, "field:date", emptyText = "No date")
        if (showErrors && dateError != null) {
            Text(dateError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (date != null) {
            TimeSetting(
                "Remind me at",
                remindAtMinute?.toTime(),
                { remindAtMinute = it?.toMinuteOfDay() },
                "field:remind-at",
                emptyText = "No reminder",
                hint = "On the day it's due.",
            )
        }
        Text(
            if (date == null) {
                "Without a date it waits on Today until it's done, and is never missed."
            } else {
                "With a date it is due that day. Not done by the end of it, it's a miss, like any goal. To-dos share one allowance: " +
                    "${Goal.DEFAULT_ALLOWANCE} skip or miss a week between them before it costs ${pointsWord()}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrivateSwitch(isPrivate) { isPrivate = it }
        if (id != 0L) {
            RemoveButton("to-do", name) { vm.removeGoal(id, onClose) }
        }
    }
}

/**
 * "How hard is it?": picking one suggests the points, filled in when the box is empty and
 * offered with "Use it" otherwise. It also says what the points pay at today's difficulty.
 */
@Composable
private fun EffortField(
    effort: Effort?,
    onEffort: (Effort?) -> Unit,
    suggest: (Effort) -> Int,
    points: String,
    onPoints: (String) -> Unit,
    vm: MainViewModel,
    label: String = "How hard is it?",
) {
    val rate by vm.currentDifficulty.collectAsStateWithLifecycle()
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Effort.entries.forEach { e ->
            FilterChip(
                selected = effort == e,
                onClick = {
                    val next = if (effort == e) null else e
                    onEffort(next)
                    if (next != null && points.isBlank()) onPoints(suggest(next).toString())
                },
                label = { Text(e.label) },
                modifier = Modifier.testTag("effort:${e.name}"),
            )
        }
    }
    effort?.let { e ->
        val suggested = suggest(e)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Suggested: $suggested",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag("suggested-points"),
            )
            if (points != suggested.toString()) {
                TextButton(onClick = { onPoints(suggested.toString()) }, modifier = Modifier.testTag("use-suggested")) { Text("Use it") }
            }
        }
    }
    val typed = points.toIntOrNull()
    if (typed != null && typed > 0 && rate != Difficulty.NORMAL) {
        Text(
            "At ${rate.label} this week, it pays ${rate.pay(typed)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EditorScaffold(title: String, onBack: () -> Unit, onSave: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, modifier = Modifier.testTag("save")) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** How many skips and misses are free, and whether the goal sits behind the privacy lock. */
@Composable
private fun AllowanceAndPrivacy(
    allowance: String,
    onAllowance: (String) -> Unit,
    per: Period?,
    onPer: (Period) -> Unit,
    isPrivate: Boolean,
    onPrivate: (Boolean) -> Unit,
) {
    HorizontalDivider()
    Text("Skips and misses", style = MaterialTheme.typography.titleSmall)
    NumberField(
        label = if (per == null) "Allowed before it costs ${pointsWord()}" else "Allowed per ${if (per == Period.MONTHLY) "month" else "week"}",
        value = allowance,
        onValueChange = onAllowance,
        tag = "field:allowance",
        hint = "Within this, a skip needs nothing and a miss only a reason. Over it, each costs half the ${pointsWord()} and a penalty task.",
    )
    if (per != null) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(Period.WEEKLY to "Per week", Period.MONTHLY to "Per month").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = per == value,
                    onClick = { onPer(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) { Text(label) }
            }
        }
    }
    PrivateSwitch(isPrivate, onPrivate)
}

@Composable
private fun PrivateSwitch(isPrivate: Boolean, onPrivate: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Private", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Hidden behind the privacy lock, if it's on for private goals (Settings → Privacy).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = isPrivate, onCheckedChange = onPrivate, modifier = Modifier.testTag("field:private"))
    }
}

/** An optional category for a goal, typed or picked from the ones already in use. */
@Composable
private fun CategoryField(value: String, onValue: (String) -> Unit, vm: MainViewModel) {
    val existing by vm.categories.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(Categories.LENGTH)) },
        label = { Text("Category (optional)") },
        placeholder = { Text("Like Health, Home or Work") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("field:category"),
    )
    if (existing.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            existing.forEach { name ->
                val picked = value.trim().equals(name, ignoreCase = true)
                FilterChip(
                    selected = picked,
                    onClick = { onValue(if (picked) "" else name) },
                    label = { Text(name) },
                    modifier = Modifier.testTag("category:$name"),
                )
            }
        }
    }
}

/** A goal's own colour: none, one of the offered ones, or any colour from the colour picker. */
@Composable
private fun GoalColourField(value: Int?, onValue: (Int?) -> Unit, vm: MainViewModel) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    Text("Colour", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val offered = (Colors.GOAL_COLORS + listOfNotNull(value)).distinct()
        ColourDot(null, value == null, "No colour", "goal-colour:none") { onValue(null) }
        offered.forEach { c -> ColourDot(c, value == c, "Colour ${Colors.hex(c)}", "goal-colour:${Colors.hex(c)}") { onValue(c) } }
        Box(
            Modifier
                .size(40.dp)
                .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), CircleShape)
                .clickable { picking = true }
                .semantics { contentDescription = "Any colour" }
                .testTag("goal-colour:any"),
        )
    }
    if (picking) {
        var picked by remember { mutableStateOf(value ?: Colors.GARDEN) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Goal colour") },
            text = { ColorPicker(picked, appearance.recent, onChange = { picked = it }, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    onValue(picked)
                    vm.rememberColor(picked)
                    picking = false
                }, modifier = Modifier.testTag("use-goal-colour")) { Text("Use it") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ColourDot(color: Int?, selected: Boolean, description: String, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(4.dp) else Modifier)
            .background(if (color == null) MaterialTheme.colorScheme.surfaceVariant else Color(color), CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .testTag(tag),
    ) { if (color == null) Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
