package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.ProjectRow
import io.github.randomtools3377.checklist.core.StepRow
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.TodayView

/** Where a tick button is on screen, so its burst starts there. */
private class Spot {
    var center: Offset = Offset.Zero
}

@Composable
fun TodayScreen(
    view: TodayView?,
    vm: MainViewModel,
    celebrations: Celebrations,
    onAddGoal: () -> Unit,
    padding: PaddingValues,
) {
    if (view == null) return
    val haptics = LocalHapticFeedback.current

    fun celebrateTick(result: TickResult, spot: Spot, bigWhenFinished: Boolean) {
        if (result !is TickResult.Ticked) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val total = result.points + result.bonus
        celebrations.burst(spot.center, "+$total", big = bigWhenFinished && result.finished)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("today-list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            Text(longDate(view.day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            BalanceCard(view.balance)
        }
        if (view.isEmpty) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                ) {
                    Text("Nothing to do yet.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Add a habit or a project, then tick it here to earn points.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddGoal) { Text("Add a goal") }
                }
            }
        }
        habitSection("Today", view.daily, vm, ::celebrateTick)
        habitSection("This week", view.weekly, vm, ::celebrateTick)
        habitSection("This month", view.monthly, vm, ::celebrateTick)
        if (view.projects.isNotEmpty()) {
            item { SectionTitle("Projects") }
            items(view.projects, key = { "project-${it.goal.id}" }) { project ->
                ProjectCard(
                    project = project,
                    onTick = { step, spot ->
                        vm.tickStep(step.step.id) { result ->
                            celebrateTick(result, spot, bigWhenFinished = false)
                            if (result is TickResult.Ticked && result.finished) {
                                celebrations.show(
                                    BigCelebration.ProjectFinished(project.goal.icon, project.goal.name, result.points + result.bonus),
                                )
                            }
                        }
                    },
                    onUndo = { step -> vm.undoStep(step.step.id) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun LazyListScope.habitSection(
    title: String,
    rows: List<HabitRow>,
    vm: MainViewModel,
    celebrate: (TickResult, Spot, Boolean) -> Unit,
) {
    if (rows.isEmpty()) return
    item { SectionTitle(title) }
    items(rows, key = { "habit-${it.goal.id}" }) { row ->
        HabitCard(
            row = row,
            onTick = { spot -> vm.tickHabit(row.goal.id) { celebrate(it, spot, row.goal.target > 1) } },
            onUndo = { vm.undoHabitTick(row.goal.id) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HabitCard(row: HabitRow, onTick: (Spot) -> Unit, onUndo: () -> Unit) {
    val goal = row.goal
    val spot = remember { Spot() }
    val counted = goal.target > 1
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(goal.icon, fontSize = 30.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = buildString {
                    append("+${goal.points}")
                    if (counted) append(" each · ${row.count} of ${goal.target}")
                    if (counted && goal.bonus > 0) append(" · bonus +${goal.bonus}")
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (counted) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (row.count.toFloat() / goal.target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (counted && row.count > 0) {
                OutlinedIconButton(
                    onClick = onUndo,
                    modifier = Modifier.semantics { contentDescription = "Take back one tick of ${goal.name}" }.testTag("undo:${goal.name}"),
                ) {
                    Text("−", fontSize = 22.sp)
                }
            }
            TickButton(
                done = row.done,
                label = if (counted && !row.done) "${row.count}/${goal.target}" else null,
                description = if (row.done) "Undo ${goal.name}" else "Tick ${goal.name}",
                modifier = Modifier
                    .onGloballyPositioned { spot.center = it.boundsInRoot().center }
                    .testTag("tick:${goal.name}"),
                onClick = { if (row.done && !counted) onUndo() else if (!row.done) onTick(spot) },
            )
        }
    }
}

/** The round button that ticks a goal. Filled with a check mark once done. */
@Composable
private fun TickButton(done: Boolean, label: String?, description: String, modifier: Modifier, onClick: () -> Unit) {
    val semantics = Modifier.semantics { contentDescription = description }
    if (done) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(52.dp).then(semantics)) {
            Icon(Icons.Filled.Check, contentDescription = null)
        }
    } else {
        OutlinedIconButton(
            onClick = onClick,
            modifier = modifier.size(52.dp).then(semantics),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            if (label != null) Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) else Box(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectRow, onTick: (StepRow, Spot) -> Unit, onUndo: (StepRow) -> Unit) {
    val goal = project.goal
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.icon, fontSize = 30.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                    val detail = buildList {
                        add("${project.doneCount} of ${project.steps.size} steps")
                        if (goal.bonus > 0) add("bonus +${goal.bonus}")
                        goal.deadline?.let { add("due ${shortDate(it)}") }
                    }.joinToString(" · ")
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (project.finished) {
                    Text("Finished", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (project.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { project.doneCount.toFloat() / project.steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
            for (step in project.steps) {
                StepLine(step, onTick = { onTick(step, it) }, onUndo = { onUndo(step) })
            }
        }
    }
}

@Composable
private fun StepLine(step: StepRow, onTick: (Spot) -> Unit, onUndo: () -> Unit) {
    val spot = remember { Spot() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (step.done) onUndo() else onTick(spot) }
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .onGloballyPositioned { spot.center = it.boundsInRoot().center }
                .testTag("step:${step.step.name}"),
        ) {
            if (step.done) {
                FilledIconButton(onClick = onUndo, modifier = Modifier.size(32.dp), shape = CircleShape) {
                    Icon(Icons.Filled.Check, contentDescription = "Undo ${step.step.name}", modifier = Modifier.size(18.dp))
                }
            } else {
                OutlinedIconButton(
                    onClick = { onTick(spot) },
                    modifier = Modifier.size(32.dp).semantics { contentDescription = "Tick ${step.step.name}" },
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                ) {}
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            step.step.name,
            modifier = Modifier.weight(1f),
            color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text("+${step.step.points}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
