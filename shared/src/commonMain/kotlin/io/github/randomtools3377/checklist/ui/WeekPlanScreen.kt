@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.WeekAhead
import io.github.randomtools3377.checklist.core.weekdayShort
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** The card on Today from Monday: a few minutes to plan the week, or not this week. */
@Composable
fun WeekPlanCard(onPlan: () -> Unit, onLater: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("week-plan-card")) {
        Column(Modifier.padding(16.dp)) {
            Text("Plan your week", style = MaterialTheme.typography.titleMedium)
            Text("A look at last week, this week's target, and the days ahead.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = onPlan, modifier = Modifier.testTag("week-plan")) { Text("Plan") }
                TextButton(onClick = onLater, modifier = Modifier.padding(start = 8.dp).testTag("week-plan-later")) { Text("Not this week") }
            }
        }
    }
}

/** "2 of 3 left" for a goal due so many times this week, or that it's done. */
internal fun flexibleLeft(row: HabitRow): String = when {
    row.done -> "all ${row.goal.target} done ✓"
    row.left == 0 -> "none left this week"
    else -> "${row.left} of ${row.goal.target} left"
}

/** "today", "tomorrow" or "Fri 14". */
internal fun dayWord(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    else -> "${weekdayShort(day.dayOfWeek.isoDayNumber)} ${day.dayOfMonth}"
}

/**
 * Weekly planning: last week summed up, this week's target, and the days left in the week, with
 * the week's flexible goals, deadlines, dated to-dos and calendar events (see [WeekAhead]). Last
 * week shows the lock instead while [lastWeekLocked] (the review is locked). [onOpenPlan] opens
 * the planner on top, so Back returns here.
 */
@Composable
fun WeekPlanScreen(vm: MainViewModel, onOpenPlan: () -> Unit, onBack: () -> Unit, lastWeekLocked: Boolean = false, onUnlock: () -> Unit = {}) {
    val last by vm.lastWeek.collectAsStateWithLifecycle()
    val target by vm.weeklyTarget.collectAsStateWithLifecycle()
    val days by vm.planDays.collectAsStateWithLifecycle()
    // The phone's calendar, only read: empty unless it is turned on in Settings and allowed.
    val events by vm.calendarEvents.collectAsStateWithLifecycle()
    val calendarChoice by vm.calendarChoice.collectAsStateWithLifecycle()
    val calendarOn = calendarChoice.first
    val settings by vm.settings.collectAsStateWithLifecycle()
    var text by remember(target) { mutableStateOf(if (target > 0) target.toString() else "") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan your week") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("week-plan-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Last week")
            if (lastWeekLocked) {
                TextButton(onClick = onUnlock, modifier = Modifier.testTag("week-plan-unlock")) { Text("🔒 The review is locked · Unlock") }
            } else last?.let { summary ->
                val due = summary.days.sumOf { it.due }
                val done = summary.days.sumOf { it.done }
                Text(
                    if (due == 0) "Nothing was due." else "Done $done of $due · earned ${summary.earned}" + if (summary.lost > 0) " · lost ${summary.lost}" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                summary.mostMissed?.let { Text("Missed most: ${it.goal.icon} ${it.goal.name}, ${it.missed} times. Worth a better day or time?", style = MaterialTheme.typography.bodyMedium) }
                // Praise only a goal that got done, as the weekly summary does.
                summary.mostConsistent?.takeIf { it.done > 0 }?.let { Text("Most consistent: ${it.goal.icon} ${it.goal.name}. Well done!", style = MaterialTheme.typography.bodyMedium) }
            }
            SectionTitle("This week's target")
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(label = "${pointsTitle()} this week", value = text, onValueChange = { text = it.take(5) }, tag = "week-plan-target", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setWeeklyTarget(text.toIntOrNull() ?: 0) }, modifier = Modifier.testTag("week-plan-set-target")) { Text("Set") }
            }
            SectionTitle("The days ahead")
            // From today to Sunday.
            val week = WeekAhead.of(days, if (calendarOn) events else emptyList(), settings.dayEndHour)
            week.days.forEach { day ->
                Row(Modifier.fillMaxWidth()) {
                    Text(weekdayShort(day.day.dayOfWeek.isoDayNumber), modifier = Modifier.width(48.dp))
                    Text(
                        if (day.due.isEmpty()) "Nothing due" else day.due.joinToString(" ") { it.goal.icon } + "  ·  ${day.due.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (week.flexible.isNotEmpty()) {
                SectionTitle("Any day this week")
                week.flexible.forEach { row ->
                    Text(
                        "${row.goal.icon} ${row.goal.name} · " + flexibleLeft(row),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("week-plan-flexible:${row.goal.name}"),
                    )
                }
            }
            if (week.deadlines.isNotEmpty()) {
                SectionTitle("Deadlines this week")
                week.deadlines.forEach { project ->
                    val on = project.goal.deadline!!
                    Text(
                        "${project.goal.icon} ${project.goal.name} · ${dayWord(on, week.days.first().day)} · ${project.doneCount} of ${project.steps.size} steps done",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("week-plan-deadline:${project.goal.name}"),
                    )
                }
            }
            if (week.todos.isNotEmpty()) {
                SectionTitle("To-dos this week")
                week.todos.forEach { (on, row) ->
                    Text(
                        "${row.goal.icon} ${row.goal.name} · ${dayWord(on, week.days.first().day)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("week-plan-todo:${row.goal.name}"),
                    )
                }
            }
            if (week.hasEvents) {
                SectionTitle("In your calendar")
                week.days.filter { it.events.isNotEmpty() }.forEach { day ->
                    Text(
                        dayWord(day.day, week.days.first().day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("week-plan-events:${day.day.dayOfWeek.isoDayNumber}"),
                    )
                    day.events.forEach { EventLine(it) }
                }
            }
            OutlinedButton(onClick = onOpenPlan, modifier = Modifier.testTag("week-plan-open")) { Text("Move things in the planner") }
            Button(
                onClick = {
                    vm.weekPlanned()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().testTag("week-plan-done"),
            ) { Text("Done planning") }
        }
    }
}
