@file:OptIn(ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Moves
import io.github.randomtools3377.checklist.core.TodayView
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.weekdayShort
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The planner: today and the coming two weeks, a day at a time. Each day lists what is due,
 * with its time, and a day's goal can be moved to another day of the same week.
 */
@Composable
fun PlanScreen(days: List<TodayView>, vm: MainViewModel, padding: PaddingValues, onTimeline: (Int) -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf(0) }
    var moving by remember { mutableStateOf<Pair<Goal, LocalDate>?>(null) }
    if (days.isEmpty()) return
    val today = days.first().day
    val view = days[selected.coerceIn(days.indices)]
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("plan"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                days.forEachIndexed { index, day ->
                    FilterChip(
                        selected = index == selected,
                        onClick = { selected = index },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(dayLabel(day.day, today), style = MaterialTheme.typography.labelSmall)
                                Text("${day.day.dayOfMonth}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(if (day.dueToday.isEmpty()) " " else "${day.dueToday.size}", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.testTag("plan-day:$index"),
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(longDate(view.day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = 4.dp))
                if (view.dueToday.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = { onTimeline(selected) }, modifier = Modifier.testTag("plan-timeline")) { Text("Timeline") }
                }
            }
        }
        val due = view.dueToday.sortedWith(compareBy({ it.goal.at ?: it.goal.times.minOrNull() }, { it.goal.position }))
        if (due.isEmpty() && view.thisWeek.isEmpty() && view.projects.none { it.goal.deadline == view.day }) {
            item { Text("Nothing due.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(due, key = { "plan-${it.goal.id}" }) { row ->
            PlanRow(row, view.day, today, onMove = { moving = row.goal to view.day })
        }
        if (view.thisWeek.isNotEmpty()) {
            item { SectionTitle("This week") }
            items(view.thisWeek, key = { "plan-week-${it.goal.id}" }) { row -> PlanRow(row, view.day, today, onMove = null) }
        }
        val deadlines = view.projects.filter { it.goal.deadline == view.day }
        if (deadlines.isNotEmpty()) {
            item { SectionTitle("Deadlines") }
            items(deadlines, key = { "plan-project-${it.goal.id}" }) { project ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.goalStrip(project.goal.color).padding(12.dp)) {
                        Text(project.goal.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        Text(project.goal.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${project.doneCount} of ${project.steps.size} steps", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Text(
                "Move a day's goal to another day of the same week, free. If it isn't done on the new day, that's a miss as usual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    moving?.let { (goal, from) ->
        MoveDialog(goal, from, today, onMove = { to -> vm.moveGoal(goal.id, from, to) }, onDismiss = { moving = null })
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tmrw"
    else -> weekdayShort(day.dayOfWeek.isoDayNumber)
}

@Composable
private fun PlanRow(row: HabitRow, day: LocalDate, today: LocalDate, onMove: (() -> Unit)?) {
    val goal = row.goal
    Card(Modifier.fillMaxWidth().testTag("plan-goal:${goal.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.goalStrip(goal.color).padding(12.dp)) {
            Text(goal.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium)
                val detail = buildList {
                    goal.at?.let { add(timeText(it)) }
                    if (goal.times.isNotEmpty()) add(goal.times.sorted().joinToString(", ") { timeText(it) })
                    if (goal.target > 1) add("${row.count} of ${goal.target}")
                    Moves.movedFrom(goal.repeat, day)?.let { add("moved from ${weekdayShort(it.dayOfWeek.isoDayNumber)} ${it.dayOfMonth}") }
                    when {
                        row.done && !row.skipped -> add("done ✓")
                        row.skipped -> add("skipped")
                    }
                }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val movable = onMove != null && row.count == 0 && row.skips == 0 && Moves.choices(goal, day, today).isNotEmpty()
            if (movable) {
                TextButton(onClick = { onMove?.invoke() }, modifier = Modifier.testTag("move:${goal.name}")) { Text("Move") }
            }
        }
    }
}

/** Where to move one day's goal: the other days of its week, from today on, where it isn't due already. */
@Composable
internal fun MoveDialog(goal: Goal, from: LocalDate, today: LocalDate, onMove: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val choices = remember(goal, from, today) { Moves.choices(goal, from, today) }
    val original = Moves.movedFrom(goal.repeat, from)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${goal.icon} ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From ${longDate(from)} to:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { day ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onMove(day)
                                onDismiss()
                            },
                            label = { Text((if (day == original) "Back to " else "") + "${weekdayShort(day.dayOfWeek.isoDayNumber)} ${day.dayOfMonth}") },
                            modifier = Modifier.testTag("move-to:${day.dayOfWeek.isoDayNumber}"),
                        )
                    }
                }
                if (choices.isEmpty()) {
                    Text("There's no free day left in this week. You can skip it instead.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Moving is free within the week. If it isn't done on the new day, that's a miss as usual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
