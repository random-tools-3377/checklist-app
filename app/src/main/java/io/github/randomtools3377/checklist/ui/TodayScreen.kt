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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import io.github.randomtools3377.checklist.core.Categories
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val backup by vm.backupState.collectAsStateWithLifecycle()
    var editingTick by remember { mutableStateOf<Long?>(null) }
    editingTick?.let { id -> EditTickDialog(id, vm) { editingTick = null } }
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

    // Showing one category at a time, when goals have them.
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = Categories.of(
        screen.plan.map { it.row.goal } + (view.thisWeek + view.thisMonth + view.thisYear).map { it.goal } + view.projects.map { it.goal },
    )
    val filter = category?.takeIf { it in categories }
    val plan = screen.plan.filter { Categories.shows(filter, it.row.goal) }
    fun shown(rows: List<HabitRow>) = rows.filter { Categories.shows(filter, it.goal) }
    val projects = view.projects.filter { Categories.shows(filter, it.goal) }

    fun celebrateTick(result: TickResult, spot: Spot, bigWhenFinished: Boolean) {
        if (result !is TickResult.Ticked) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val total = result.points + result.bonus
        celebrations.burst(spot.center, "+$total", big = bigWhenFinished && result.finished)
    }

    fun tick(row: HabitRow, spot: Spot) {
        val action: (Long, (TickResult) -> Unit) -> Unit = if (row.isRoutine) vm::finishRoutine else vm::tickHabit
        action(row.goal.id) { result ->
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

    /** A routine's item; the last one finishes the routine and celebrates like a tick. */
    fun checkItem(row: HabitRow, item: StepRow, spot: Spot) {
        vm.checkItem(item.step.id) { result ->
            if (result is TickResult.Ticked && result.finished) {
                val gif = row.goal.celebration?.takeIf { Celebration.isValid(it) }
                if (gif != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    celebrations.show(BigCelebration.GoalFinished(row.goal.icon, row.goal.name, result.points + result.bonus, gif))
                } else {
                    celebrateTick(result, spot, bigWhenFinished = true)
                }
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
            backup.due?.let { days ->
                BackupReminderCard(days, onBackUp = onSettings, onLater = vm::backupLater)
                Spacer(Modifier.height(12.dp))
            }
            StreakCard(screen.streaks, screen.streakSettings)
            if (screen.hiddenPrivate > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth().testTag("unlock-private")) {
                    Text(if (screen.hiddenPrivate == 1) "🔒 1 private goal · Unlock" else "🔒 ${screen.hiddenPrivate} private goals · Unlock")
                }
            }
        }
        if (categories.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp).testTag("today-categories"),
                ) {
                    FilterChip(selected = filter == null, onClick = { category = null }, label = { Text("All") }, modifier = Modifier.testTag("today-category:All"))
                    categories.forEach { name ->
                        FilterChip(
                            selected = filter == name,
                            onClick = { category = if (filter == name) null else name },
                            label = { Text(name) },
                            modifier = Modifier.testTag("today-category:$name"),
                        )
                    }
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
            if (plan.isNotEmpty()) item { SectionTitle("Today") }
            items(plan, key = { "today-${it.row.goal.id}" }) { planned ->
                HabitCard(
                    row = planned.row,
                    // A habit done several times a day shows its next one instead.
                    timeLabel = if (screen.nextSlots.containsKey(planned.row.goal.id)) null else timeLabel(planned, screen),
                    late = planned.late,
                    paceLine = null,
                    nextLine = nextLine(planned.row, screen),
                    onTick = { spot -> tick(planned.row, spot) },
                    onUndo = { if (planned.row.isRoutine) vm.undoRoutine(planned.row.goal.id) else vm.undoHabitTick(planned.row.goal.id) },
                    onSkip = { skipping = planned.row.goal },
                    onItem = { item, spot -> checkItem(planned.row, item, spot) },
                    onUncheck = { item -> vm.uncheckItem(item.step.id) },
                    onFocus = planned.row.goal.minutes?.let { minutes -> { vm.startFocus(planned.row.goal.id, minutes) } },
                    lastTick = screen.lastTicks[planned.row.goal.id],
                    onNote = { editingTick = it },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        habitSection("This week", shown(view.thisWeek), screen, ::tick, vm) { skipping = it }
        habitSection("This month", shown(view.thisMonth), screen, ::tick, vm) { skipping = it }
        habitSection("This year", shown(view.thisYear), screen, ::tick, vm) { skipping = it }
        if (projects.isNotEmpty()) {
            item { SectionTitle("Projects") }
            items(projects, key = { "project-${it.goal.id}" }) { project ->
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

/** Phone backup is off for this app, so a reminder when the last backup is getting old. */
@Composable
private fun BackupReminderCard(days: Int, onBackUp: () -> Unit, onLater: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("backup-reminder"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Time for a backup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                (if (days < 0) "You haven't backed up yet." else "Your last backup was $days days ago.") +
                    " Phone backup is off for this app, so your backup file is the only copy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = onBackUp, modifier = Modifier.testTag("backup-now")) { Text("Back up") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onLater, modifier = Modifier.testTag("backup-later")) { Text("Later") }
            }
        }
    }
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
            onUndo = { if (row.isRoutine) vm.undoRoutine(row.goal.id) else vm.undoHabitTick(row.goal.id) },
            onSkip = { onSkip(row.goal) },
            onItem = { item, _ -> vm.checkItem(item.step.id) },
            onUncheck = { item -> vm.uncheckItem(item.step.id) },
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
    onItem: (StepRow, Spot) -> Unit = { _, _ -> },
    onUncheck: (StepRow) -> Unit = {},
    onFocus: (() -> Unit)? = null,
    lastTick: io.github.randomtools3377.checklist.core.Tick? = null,
    onNote: (Long) -> Unit = {},
) {
    val goal = row.goal
    val spot = remember { Spot() }
    val counted = goal.target > 1 || row.isRoutine
    Card(Modifier.fillMaxWidth()) {
      Column {
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
                val length = goal.minutes
                if (onFocus != null && !row.done && length != null) {
                    Text(
                        "▶ Focus for ${minutesText(length)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onFocus).padding(vertical = 4.dp).testTag("focus:${goal.name}"),
                    )
                }
                // A note about the latest tick, like "ran 5 km".
                if (lastTick != null) {
                    Text(
                        lastTick.note?.let { "“$it”" } ?: "Add a note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNote(lastTick.id) }.padding(vertical = 4.dp).testTag("note:${goal.name}"),
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
                        shownDone && (!counted || row.isRoutine) -> "Undo ${goal.name}"
                        shownDone -> "${goal.name} is done"
                        row.isRoutine -> "Tick every item of ${goal.name}"
                        counted -> "Add one to ${goal.name}"
                        else -> "Mark ${goal.name} done"
                    },
                    modifier = Modifier
                        .onGloballyPositioned { spot.center = it.boundsInRoot().center }
                        .testTag("tick:${goal.name}"),
                    onClick = { if (shownDone && (!counted || row.isRoutine)) onUndo() else if (!shownDone) onTick(spot) },
                    routine = row.isRoutine,
                )
                if (counted) {
                    Text(
                        if (row.isRoutine) "${row.items.count { it.done }} of ${row.items.size}" else "${row.count} of ${goal.target}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp).testTag("count:${goal.name}"),
                    )
                }
            }
        }
        // A routine's checklist, ticked item by item.
        if (row.isRoutine) {
            Column(Modifier.padding(start = 64.dp, end = 12.dp, bottom = 8.dp)) {
                row.items.forEach { item ->
                    RoutineItemLine(item, onTick = { onItem(item, it) }, onUndo = { onUncheck(item) })
                }
            }
        }
      }
    }
}

/** One item of a routine: a box to tick and its name. */
@Composable
private fun RoutineItemLine(item: StepRow, onTick: (Spot) -> Unit, onUndo: () -> Unit) {
    val spot = remember { Spot() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (item.done) onUndo() else onTick(spot) }
            .semantics { contentDescription = if (item.done) "Untick ${item.step.name}" else "Tick ${item.step.name}" }
            .testTag("item:${item.step.name}")
            .padding(vertical = 4.dp),
    ) {
        androidx.compose.material3.Checkbox(
            checked = item.done,
            onCheckedChange = null,
            modifier = Modifier.onGloballyPositioned { spot.center = it.boundsInRoot().center },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            item.step.name,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The button that ticks a goal, saying what it does: "Done" for a single goal, "+1" for one done
 * several times. Once done it fills in with a check mark; for a single goal, tapping it again
 * takes the tick back.
 */
@Composable
private fun TickButton(done: Boolean, counted: Boolean, description: String, modifier: Modifier, onClick: () -> Unit, routine: Boolean = false) {
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
            if (counted && !routine) {
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
