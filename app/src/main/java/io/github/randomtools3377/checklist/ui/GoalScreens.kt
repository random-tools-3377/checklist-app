@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay

// The list of goals

@Composable
fun GoalsScreen(view: GoalsView?, onEdit: (Goal) -> Unit, padding: PaddingValues) {
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
        goalSection("Daily habits", habits.filter { it.period == Period.DAILY }, view, onEdit)
        goalSection("Weekly habits", habits.filter { it.period == Period.WEEKLY }, view, onEdit)
        goalSection("Monthly habits", habits.filter { it.period == Period.MONTHLY }, view, onEdit)
        goalSection("Projects", projects, view, onEdit)
    }
}

private fun LazyListScope.goalSection(title: String, goals: List<Goal>, view: GoalsView, onEdit: (Goal) -> Unit) {
    if (goals.isEmpty()) return
    item { SectionTitle(title) }
    items(goals, key = { it.id }) { goal ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clickable { onEdit(goal) }
                .testTag("goal:${goal.name}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Text(goal.icon, fontSize = 28.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                    Text(describe(goal, view.steps[goal.id].orEmpty().size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun describe(goal: Goal, stepCount: Int): String = when (goal.kind) {
    GoalKind.HABIT -> buildList {
        add("+${goal.points}")
        val per = when (goal.period) {
            Period.DAILY -> "a day"
            Period.WEEKLY -> "a week"
            Period.MONTHLY -> "a month"
            null -> ""
        }
        add(if (goal.target == 1) "once $per" else "${goal.target} times $per")
        if (goal.target > 1 && goal.bonus > 0) add("bonus +${goal.bonus}")
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
                        Text("Repeats every day, week or month", style = MaterialTheme.typography.bodySmall)
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

@Composable
fun HabitEditor(id: Long, vm: MainViewModel, onClose: () -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(GOAL_ICON) }
    var points by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(Period.DAILY) }
    var target by rememberSaveable { mutableStateOf("1") }
    var bonus by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadGoal(id)?.first?.let { goal ->
                name = goal.name
                icon = goal.icon
                points = goal.points.toString()
                period = goal.period ?: Period.DAILY
                target = goal.target.toString()
                bonus = if (goal.bonus > 0) goal.bonus.toString() else ""
            }
            loaded = true
        }
    }

    val nameError = if (name.isBlank()) "Give it a name" else null
    val pointsError = if (points.toIntOrNull() == null) "How many points it pays" else null
    val targetValue = target.toIntOrNull()
    val targetError = if (targetValue == null || targetValue !in 1..99) "From 1 to 99" else null
    val counted = (targetValue ?: 1) > 1

    EditorScaffold(
        title = if (id == 0L) "New habit" else "Edit habit",
        onBack = onClose,
        onSave = {
            if (nameError != null || pointsError != null || targetError != null) {
                showErrors = true
            } else {
                vm.saveHabit(
                    HabitDraft(
                        id = id,
                        name = name.trim(),
                        icon = icon,
                        points = points.toInt(),
                        period = period,
                        target = targetValue!!,
                        bonus = if (counted) bonus.toIntOrNull() ?: 0 else 0,
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
        Text("How often", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(Period.DAILY to "Daily", Period.WEEKLY to "Weekly", Period.MONTHLY to "Monthly")
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = period == value,
                    onClick = { period = value },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.testTag("period:$label"),
                ) { Text(label) }
            }
        }
        val per = when (period) {
            Period.DAILY -> "day"
            Period.WEEKLY -> "week"
            Period.MONTHLY -> "month"
        }
        NumberField(
            label = "Times per $per",
            value = target,
            onValueChange = { target = it },
            tag = "field:target",
            error = if (showErrors) targetError else null,
            hint = "More than 1 makes it a counted goal, like 8 glasses of water",
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
        Text(
            "Changes apply from now on. Points already earned stay as they were.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (id != 0L) RemoveButton("habit", name) { vm.removeGoal(id, onClose) }
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
    var nextKey by rememberSaveable { mutableStateOf(2L) }
    val steps = rememberSaveable(saver = StepFieldsSaver) { mutableListOf(StepField(1L, 0L, "", "")).toMutableStateList() }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.loadGoal(id)?.let { (goal, existingSteps) ->
                name = goal.name
                icon = goal.icon
                bonus = if (goal.bonus > 0) goal.bonus.toString() else ""
                deadline = goal.deadline?.toEpochDay()
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
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                deadline?.let { "Deadline: ${longDate(it.toDay())}" } ?: "No deadline",
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickingDate = true }) { Text(if (deadline == null) "Set" else "Change") }
            if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Clear") }
        }
        Text(
            "Changes apply from now on. Points already earned stay as they were.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (id != 0L) RemoveButton("project", name) { vm.removeGoal(id, onClose) }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (deadline ?: nowLocal().date.toEpochDay()) * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        deadline = Math.floorDiv(it, 86_400_000L)
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** The frame shared by the editors: a top bar with Back and Save, and a scrolling form. */
@Composable
fun EditorScaffold(title: String, onBack: () -> Unit, onSave: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
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
