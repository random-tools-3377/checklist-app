@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.LongPace
import io.github.randomtools3377.checklist.core.Planned
import io.github.randomtools3377.checklist.core.ProjectRow
import io.github.randomtools3377.checklist.core.StepRow
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.TodayPace
import io.github.randomtools3377.checklist.core.dateText
import io.github.randomtools3377.checklist.core.ordinal
import io.github.randomtools3377.checklist.data.ORDER_OWN
import io.github.randomtools3377.checklist.data.ORDER_RECOMMENDED

/** Where a tick button is on screen, so its burst starts there. */
private class Spot {
    var center: Offset = Offset.Zero
}

@Composable
fun TodayScreen(
    screen: TodayState?,
    vm: MainViewModel,
    celebrations: Celebrations,
    onAddGoal: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onUnlock: () -> Unit,
    skipRequest: Long?,
    onSkipRequestHandled: () -> Unit,
    padding: PaddingValues,
) {
    if (screen == null) return
    val view = screen.view
    val haptics = LocalHapticFeedback.current
    var skipping by remember { mutableStateOf<Goal?>(null) }
    LaunchedEffect(skipRequest, screen.plan) {
        if (skipRequest != null) {
            val all = screen.plan.map { it.row } + view.thisWeek + view.thisMonth + view.thisYear
            all.firstOrNull { it.goal.id == skipRequest }?.let { skipping = it.goal }
            onSkipRequestHandled()
        }
    }
    skipping?.let { goal -> SkipDialog(goal, vm) { skipping = null } }

    fun celebrateTick(result: TickResult, spot: Spot, bigWhenFinished: Boolean) {
        if (result !is TickResult.Ticked) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val total = result.points + result.bonus
        celebrations.burst(spot.center, "+$total", big = bigWhenFinished && result.finished)
    }

    fun tick(row: HabitRow, spot: Spot) {
        vm.tickHabit(row.goal.id) { result ->
            val gif = row.goal.celebration?.takeIf { Celebration.isValid(it) }
            if (gif != null && result is TickResult.Ticked && result.finished) {
                // Its own GIF, full screen.
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                celebrations.show(BigCelebration.GoalFinished(row.goal.icon, row.goal.name, result.points + result.bonus, gif))
            } else {
                celebrateTick(result, spot, row.goal.target > 1)
            }
        }
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
            BalanceCard(view.balance, modifier = Modifier.clickable(onClick = onHistory), note = "Tap for your points history")
            Spacer(Modifier.height(12.dp))
            StreakCard(screen.streaks, screen.streakSettings)
            if (screen.hiddenPrivate > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth().testTag("unlock-private")) {
                    Text(if (screen.hiddenPrivate == 1) "🔒 1 private goal · Unlock" else "🔒 ${screen.hiddenPrivate} private goals · Unlock")
                }
            }
        }
        if (view.isEmpty) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                ) {
                    Text("Nothing due today.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Add a habit or a project, then tick it here to earn points.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddGoal) { Text("Add a goal") }
                }
            }
        }

        if (screen.plan.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                PaceCard(screen, onSettings)
                Spacer(Modifier.height(12.dp))
                OrderSwitch(screen.recommended) { vm.setTodayOrder(if (it) ORDER_RECOMMENDED else ORDER_OWN) }
            }
            item { SectionTitle("Today") }
            items(screen.plan, key = { "today-${it.row.goal.id}" }) { planned ->
                HabitCard(
                    row = planned.row,
                    // A habit done several times a day shows its next one instead.
                    timeLabel = if (screen.nextSlots.containsKey(planned.row.goal.id)) null else timeLabel(planned, screen),
                    late = planned.late,
                    paceLine = null,
                    nextLine = nextLine(planned.row, screen),
                    onTick = { spot -> tick(planned.row, spot) },
                    onUndo = { vm.undoHabitTick(planned.row.goal.id) },
                    onSkip = { skipping = planned.row.goal },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        habitSection("This week", view.thisWeek, screen, ::tick, vm) { skipping = it }
        habitSection("This month", view.thisMonth, screen, ::tick, vm) { skipping = it }
        habitSection("This year", view.thisYear, screen, ::tick, vm) { skipping = it }
        if (view.projects.isNotEmpty()) {
            item { SectionTitle("Projects") }
            items(view.projects, key = { "project-${it.goal.id}" }) { project ->
                ProjectCard(
                    project = project,
                    paceLine = screen.longPaces[project.goal.id]?.let { paceText(it) },
                    onTick = { step, spot ->
                        vm.tickStep(step.step.id) { result ->
                            celebrateTick(result, spot, bigWhenFinished = false)
                            if (result is TickResult.Ticked && result.finished) {
                                celebrations.show(
                                    BigCelebration.ProjectFinished(
                                        project.goal.icon, project.goal.name, result.points + result.bonus,
                                        gif = project.goal.celebration?.takeIf { Celebration.isValid(it) },
                                    ),
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

private fun timeLabel(planned: Planned, screen: TodayState): String? {
    val time = planned.time ?: return null
    if (planned.row.done) return null
    val text = momentText(time, screen.view.day)
    return if (planned.fixed) "at $text" else "by about $text"
}

private fun paceText(pace: LongPace): String = "${pace.describe()} (finish by ${dateText(pace.finish)})"

/**
 * For a habit done several times a day: when the next one is due, at its own time or by the
 * time it was spread to, and whether that has passed. Without times or a day start, a hint.
 */
private fun nextLine(row: HabitRow, screen: TodayState): Pair<String, Boolean>? {
    if (!row.goal.severalADay || row.done) return null
    val slot = screen.nextSlots[row.goal.id]
        ?: return if (row.goal.times.isEmpty() && !screen.hasDayStart) "Set when your day starts in Settings to space these out" to false else null
    val text = momentText(slot.at, screen.view.day)
    val late = slot.at < screen.now
    val label = if (slot.fixed) "Next: the ${ordinal(slot.number)} at $text" else "Next one by $text"
    return (if (late) "$label · late" else label) to late
}

private fun LazyListScope.habitSection(
    title: String,
    rows: List<HabitRow>,
    screen: TodayState,
    tick: (HabitRow, Spot) -> Unit,
    vm: MainViewModel,
    onSkip: (Goal) -> Unit,
) {
    if (rows.isEmpty()) return
    item { SectionTitle(title) }
    items(rows, key = { "habit-${it.goal.id}" }) { row ->
        HabitCard(
            row = row,
            timeLabel = null,
            late = false,
            paceLine = screen.longPaces[row.goal.id]?.let { paceText(it) },
            onTick = { spot -> tick(row, spot) },
            onUndo = { vm.undoHabitTick(row.goal.id) },
            onSkip = { onSkip(row.goal) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** How fast to go to get through what is left today. */
@Composable
private fun PaceCard(screen: TodayState, onSettings: () -> Unit) {
    val pace: TodayPace? = screen.pace
    val left = screen.plan.count { !it.row.done }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("pace"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val color = MaterialTheme.colorScheme.onSecondaryContainer
            when {
                left == 0 -> Text("Everything due today is done. 🎉", color = color, style = MaterialTheme.typography.titleSmall)
                pace == null -> {
                    Text("$left left today", color = color, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (screen.hasDefaultFinish) "None of them has a finish time, so there is no pace yet."
                        else "Set when you want to be done each day to see how fast to go.",
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!screen.hasDefaultFinish) TextButton(onClick = onSettings) { Text("Set a finish time") }
                }
                else -> {
                    val day = screen.view.day
                    val headline = when {
                        pace.minutesEach == null -> "$left left today"
                        pace.minutesEach == 0 -> "$left left today · no time to spare"
                        else -> "$left left today · about ${minutesText(pace.minutesEach!!)} each"
                    }
                    Text(headline, color = color, style = MaterialTheme.typography.titleSmall)
                    val next = pace.nextBy?.let { "Next one by ${momentText(it, day)} · " } ?: ""
                    Text("${next}finish by ${momentText(pace.finishAt, day)}", color = color, style = MaterialTheme.typography.bodyMedium)
                    if (pace.reservedMinutes > 0) {
                        Text("Includes ${minutesText(pace.reservedMinutes)} for goals with a set length", color = color, style = MaterialTheme.typography.bodySmall)
                    }
                    if (pace.shortBy > 0) {
                        Text(
                            "Not enough time: ${minutesText(pace.shortBy)} short",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (pace.unpaced > 0) {
                        Text("${pace.unpaced} without a finish time aren't counted", color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSwitch(recommended: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = recommended,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("order:recommended"),
        ) { Text("Recommended order") }
        SegmentedButton(
            selected = !recommended,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("order:own"),
        ) { Text("My order") }
    }
}

@Composable
private fun HabitCard(
    row: HabitRow,
    timeLabel: String?,
    late: Boolean,
    paceLine: String?,
    onTick: (Spot) -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
    nextLine: Pair<String, Boolean>? = null,
) {
    val goal = row.goal
    val spot = remember { Spot() }
    val counted = goal.target > 1
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(goal.icon, fontSize = 30.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = buildList {
                    add("+${goal.points}" + if (counted) " each" else "")
                    if (counted && goal.bonus > 0) add("bonus +${goal.bonus}")
                    goal.minutes?.let { add(minutesText(it)) }
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (timeLabel != null) {
                    Text(
                        if (late) "$timeLabel · late" else timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                if (paceLine != null) {
                    Text(paceLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (nextLine != null) {
                    val (text, isLate) = nextLine
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isLate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("next:${goal.name}"),
                    )
                }
                if (row.skipped) {
                    Text("Skipped", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("skipped:${goal.name}"))
                } else if (!row.done) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable(onClick = onSkip).padding(vertical = 4.dp).testTag("skip:${goal.name}"),
                    )
                }
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
            val shownDone = row.count >= goal.target
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TickButton(
                    done = shownDone,
                    counted = counted,
                    description = when {
                        shownDone && !counted -> "Undo ${goal.name}"
                        shownDone -> "${goal.name} is done"
                        counted -> "Add one to ${goal.name}"
                        else -> "Mark ${goal.name} done"
                    },
                    modifier = Modifier
                        .onGloballyPositioned { spot.center = it.boundsInRoot().center }
                        .testTag("tick:${goal.name}"),
                    onClick = { if (shownDone && !counted) onUndo() else if (!shownDone) onTick(spot) },
                )
                if (counted) {
                    Text(
                        "${row.count} of ${goal.target}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp).testTag("count:${goal.name}"),
                    )
                }
            }
        }
    }
}

/**
 * The button that ticks a goal, saying what it does: "Done" for a single goal, "+1" for one done
 * several times. Once done it fills in with a check mark; for a single goal, tapping it again
 * takes the tick back.
 */
@Composable
private fun TickButton(done: Boolean, counted: Boolean, description: String, modifier: Modifier, onClick: () -> Unit) {
    val semantics = Modifier.semantics { contentDescription = description }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    if (done) {
        Button(onClick = onClick, contentPadding = padding, modifier = modifier.then(semantics)) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Done")
        }
    } else {
        FilledTonalButton(onClick = onClick, contentPadding = padding, modifier = modifier.then(semantics)) {
            if (counted) {
                Text("+1", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done")
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectRow, paceLine: String?, onTick: (StepRow, Spot) -> Unit, onUndo: (StepRow) -> Unit) {
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
                    if (paceLine != null) {
                        Text(paceLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
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
