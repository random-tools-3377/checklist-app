@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.describe
import io.github.randomtools3377.checklist.core.lengthOfMonth
import io.github.randomtools3377.checklist.core.monthShort
import io.github.randomtools3377.checklist.core.ordinal
import io.github.randomtools3377.checklist.core.weekdayShort
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toMinuteOfDay
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// The list of goals

@Composable
fun GoalsScreen(view: GoalsView?, reordering: Boolean, onEdit: (Goal) -> Unit, onMove: (Goal, Int) -> Unit, padding: PaddingValues) {
    if (view == null) return
    val habits = view.goals.filter { it.kind == GoalKind.HABIT }
    val projects = view.goals.filter { it.kind == GoalKind.PROJECT }
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
                    "No goals yet. Add a habit that repeats, like drinking water every day, or a project made of steps.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
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
        goal.at?.let { add("at ${timeText(it)}") }
        goal.minutes?.let { add(minutesText(it)) }
    }.joinToString(" · ")
    GoalKind.PROJECT -> buildList {
        add(if (stepCount == 1) "1 step" else "$stepCount steps")
        if (goal.bonus > 0) add("bonus +${goal.bonus}")
        goal.deadline?.let { add("due ${shortDate(it)}") }
    }.joinToString(" · ")
}

/** Asks whether the new goal is a habit or a project. */
@Composable
fun NewGoalDialog(onHabit: () -> Unit, onProject: () -> Unit, onDismiss: () -> Unit) {
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
            text = { Text("It disappears from your lists. Points it already earned stay in your balance.") },
            confirmButton = {
                TextButton(onClick = { asking = false; onRemove() }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
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
fun HabitEditor(id: Long, vm: MainViewModel, onClose: () -> Unit) {
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
    var finishByMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var finishDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var choosingRepeat by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadGoal(id)?.first?.let { goal ->
                name = goal.name
                icon = goal.icon
                points = goal.points.toString()
                target = goal.target.toString()
                bonus = if (goal.bonus > 0) goal.bonus.toString() else ""
                minutes = goal.minutes?.toString() ?: ""
                atMinute = goal.at?.toMinuteOfDay()
                finishByMinute = goal.finishBy?.toMinuteOfDay()
                finishDay = goal.finishDay
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
    val pointsError = if (points.toIntOrNull() == null) "How many points it pays" else null
    val targetValue = target.toIntOrNull()
    val targetError = if (targetValue == null || targetValue !in 1..999) "From 1 to 999" else null
    val end = endDay
    val repeatError = when {
        repeat == null -> "Finish choosing how often"
        end != null && end < startDay -> "The end date is before the start"
        else -> null
    }
    val minutesValue = minutes.toIntOrNull()?.takeIf { it > 0 }
    val counted = (targetValue ?: 1) > 1

    EditorScaffold(
        title = if (id == 0L) "New habit" else "Edit habit",
        onBack = onClose,
        onSave = {
            if (nameError != null || pointsError != null || targetError != null || repeatError != null || repeat == null) {
                showErrors = true
            } else {
                vm.saveHabit(
                    HabitDraft(
                        id = id,
                        name = name.trim(),
                        icon = icon,
                        points = points.toInt(),
                        repeat = repeat,
                        target = targetValue ?: 1,
                        bonus = if (counted) bonus.toIntOrNull() ?: 0 else 0,
                        minutes = minutesValue,
                        at = atMinute?.toTime(),
                        finishBy = finishByMinute?.toTime(),
                        finishDay = if (choice == RepeatChoice.TIMES_PER) finishDay else null,
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
        NumberField(
            label = "Points for each tick",
            value = points,
            onValueChange = { points = it },
            tag = "field:points",
            error = if (showErrors) pointsError else null,
        )

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
        NumberField(
            label = if (choice == RepeatChoice.TIMES_PER) "Times per $perWord" else "Times each day it's due",
            value = target,
            onValueChange = { target = it },
            tag = "field:target",
            error = if (showErrors) targetError else null,
            hint = if (choice == RepeatChoice.TIMES_PER) null else "More than 1 makes it a counted goal, like 8 glasses of water",
        )
        if (counted) {
            NumberField(
                label = "Bonus for finishing all of them",
                value = bonus,
                onValueChange = { bonus = it },
                tag = "field:bonus",
                hint = "Paid on top of each tick",
            )
        }

        // Time
        HorizontalDivider()
        Text("Time (optional)", style = MaterialTheme.typography.titleSmall)
        NumberField(
            label = "How long it takes, in minutes",
            value = minutes,
            onValueChange = { minutes = it },
            tag = "field:minutes",
            hint = "Pacing keeps this much time for it. Leave empty to share the free time evenly.",
        )
        TimeSetting("At a set time", atMinute?.toTime(), { atMinute = it?.toMinuteOfDay() }, "field:at", emptyText = "Any time")
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
        Text(
            "Changes apply from now on. Points already earned stay as they were.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (id != 0L) RemoveButton("habit", name) { vm.removeGoal(id, onClose) }
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
fun ProjectEditor(id: Long, vm: MainViewModel, onClose: () -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(GOAL_ICON) }
    var bonus by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf<Long?>(null) }
    var finishByMinute by rememberSaveable { mutableStateOf<Int?>(null) }
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
        filled.any { it.points.toIntOrNull() == null } -> "Each step needs its points"
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
                        label = { Text("Points") },
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
            "With a deadline, Today shows how many steps a day it takes to finish in time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (id != 0L) RemoveButton("project", name) { vm.removeGoal(id, onClose) }
    }
}

/** The frame shared by the editors: a top bar with Back and Save, and a scrolling form. */
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
