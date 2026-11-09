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
import io.github.randomtools3377.checklist.core.TodaySection
import io.github.randomtools3377.checklist.core.WeekProgress
import io.github.randomtools3377.checklist.core.Badge
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.asImageBitmap
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
    onBadges: () -> Unit,
    skipRequest: Long?,
    onSkipRequestHandled: () -> Unit,
    padding: PaddingValues,
) {
    if (screen == null) return
    val view = screen.view
    val backup by vm.backupState.collectAsStateWithLifecycle()
    val week by vm.weekProgress.collectAsStateWithLifecycle()
    val look by vm.appearance.collectAsStateWithLifecycle()
    val grid = look.layout == TodayLayout.GRID
    // In the grid, holding a tile opens its full card here.
    var details by remember { mutableStateOf<Long?>(null) }
    val newBadges by vm.newBadges.collectAsStateWithLifecycle()
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
        celebrations.buzz(haptics)
        val total = result.points + result.bonus
        celebrations.burst(spot.center, "+$total", big = bigWhenFinished && result.finished)
    }

    fun tick(row: HabitRow, spot: Spot) {
        val action: (Long, (TickResult) -> Unit) -> Unit = if (row.isRoutine) vm::finishRoutine else vm::tickHabit
        action(row.goal.id) { result ->
            val gif = row.goal.celebration?.takeIf { Celebration.isValid(it) } ?: celebrations.style.defaultGif
            if (gif != null && result is TickResult.Ticked && result.finished) {
                // Its own GIF, full screen.
                celebrations.buzz(haptics)
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
                val gif = row.goal.celebration?.takeIf { Celebration.isValid(it) } ?: celebrations.style.defaultGif
                if (gif != null) {
                    celebrations.buzz(haptics)
                    celebrations.show(BigCelebration.GoalFinished(row.goal.icon, row.goal.name, result.points + result.bonus, gif))
                } else {
                    celebrateTick(result, spot, bigWhenFinished = true)
                }
            }
        }
    }

    details?.let { id ->
        val row = (screen.plan.map { it.row } + view.thisWeek + view.thisMonth + view.thisYear).firstOrNull { it.goal.id == id }
        if (row == null) {
            details = null
        } else {
            AlertDialog(
                onDismissRequest = { details = null },
                confirmButton = { TextButton(onClick = { details = null }, modifier = Modifier.testTag("close-details")) { Text("Close") } },
                text = {
                    HabitCard(
                        row = row,
                        timeLabel = screen.plan.firstOrNull { it.row.goal.id == id }?.takeIf { !screen.nextSlots.containsKey(id) }?.let { timeLabel(it, screen) },
                        late = screen.plan.firstOrNull { it.row.goal.id == id }?.late ?: false,
                        paceLine = screen.longPaces[id]?.let { paceText(it) },
                        nextLine = nextLine(row, screen),
                        onTick = { spot -> tick(row, spot) },
                        onUndo = { if (row.isRoutine) vm.undoRoutine(id) else vm.undoHabitTick(id) },
                        onSkip = {
                            details = null
                            skipping = row.goal
                        },
                        onItem = { item, spot -> checkItem(row, item, spot) },
                        onUncheck = { item -> vm.uncheckItem(item.step.id) },
                        onFocus = row.goal.minutes?.let { minutes -> { details = null; vm.startFocus(id, minutes) } },
                        lastTick = screen.lastTicks[id],
                        onNote = { details = null; editingTick = it },
                    )
                },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
    TodayBackdrop(look)
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
            if (newBadges.isNotEmpty()) {
                NewBadgesCard(newBadges, onSee = onBadges, onDismiss = vm::badgesSeen)
                Spacer(Modifier.height(12.dp))
            }
            backup.due?.let { days ->
                BackupReminderCard(days, onBackUp = onSettings, onLater = vm::backupLater)
                Spacer(Modifier.height(12.dp))
            }
            if (screen.hiddenPrivate > 0) {
                OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth().testTag("unlock-private")) {
                    Text(if (screen.hiddenPrivate == 1) "🔒 1 private goal · Unlock" else "🔒 ${screen.hiddenPrivate} private goals · Unlock")
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // The category chips and the empty message go just before the first list of goals.
        var goalsStarted = false
        fun startGoals() {
            if (goalsStarted) return
            goalsStarted = true
            if (categories.isNotEmpty()) {
                item(key = "categories") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp).testTag("today-categories"),
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
                item(key = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                    ) {
                        Text("Nothing due today.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Add a habit or a project, then tick it here to earn ${pointsWord()}.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onAddGoal) { Text("Add a goal") }
                    }
                }
            }
        }

        for (choice in look.sections) {
            if (!choice.shown) continue
            when (choice.section) {
                TodaySection.BALANCE -> item(key = "balance") {
                    BalanceCard(view.balance, modifier = Modifier.clickable(onClick = onHistory), note = "Tap for your ${pointsWord()} history")
                    Spacer(Modifier.height(12.dp))
                }
                TodaySection.WEEKLY_TARGET -> week?.let { progress ->
                    item(key = "weekly-target") {
                        WeeklyTargetCard(progress)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                TodaySection.STREAKS -> item(key = "streaks") {
                    StreakCard(screen.streaks, screen.streakSettings)
                    Spacer(Modifier.height(12.dp))
                }
                TodaySection.PACE -> if (screen.plan.isNotEmpty()) {
                    item(key = "pace") {
                        PaceCard(screen, onSettings)
                        Spacer(Modifier.height(12.dp))
                        OrderSwitch(screen.recommended) { vm.setTodayOrder(if (it) ORDER_RECOMMENDED else ORDER_OWN) }
                    }
                }
                TodaySection.DUE_TODAY -> {
                    startGoals()
                    if (plan.isNotEmpty()) item(key = "title-today") { SectionTitle("Today") }
                    if (grid) {
                        items(plan.chunked(2), key = { "today-tiles-${it.first().row.goal.id}" }) { pair ->
                            TileRow(pair.map { it.row }, screen, ::tick) { details = it }
                        }
                    } else {
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
                }
                TodaySection.THIS_WEEK -> {
                    startGoals()
                    habitSection("This week", shown(view.thisWeek), screen, ::tick, vm, grid, { details = it }) { skipping = it }
                }
                TodaySection.THIS_MONTH -> {
                    startGoals()
                    habitSection("This month", shown(view.thisMonth), screen, ::tick, vm, grid, { details = it }) { skipping = it }
                }
                TodaySection.THIS_YEAR -> {
                    startGoals()
                    habitSection("This year", shown(view.thisYear), screen, ::tick, vm, grid, { details = it }) { skipping = it }
                }
                TodaySection.PROJECTS -> {
                    startGoals()
                    if (projects.isNotEmpty()) {
                        item(key = "title-projects") { SectionTitle("Projects") }
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
                                                    gif = project.goal.celebration?.takeIf { Celebration.isValid(it) } ?: celebrations.style.defaultGif,
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
        }
        // With every list of goals hidden, still offer the empty message.
        startGoals()
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
    grid: Boolean,
    onDetails: (Long) -> Unit,
    onSkip: (Goal) -> Unit,
) {
    if (rows.isEmpty()) return
    item { SectionTitle(title) }
    if (grid) {
        items(rows.chunked(2), key = { "tiles-${it.first().goal.id}" }) { pair -> TileRow(pair, screen, tick, onDetails) }
        return
    }
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
    val cardSize = LocalCardSize.current
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.goalStrip(goal.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(cardSize.padding)) {
            Text(goal.icon, fontSize = cardSize.icon, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
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
    val cardSize = LocalCardSize.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.goalStrip(goal.color).padding(cardSize.padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.icon, fontSize = cardSize.icon, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
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

/** How far along the week is toward the weekly points target. */
@Composable
private fun WeeklyTargetCard(week: WeekProgress) {
    Card(Modifier.fillMaxWidth().testTag("weekly-target")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                (if (week.reached) "🎯 " else "") + week.describe(),
                style = MaterialTheme.typography.titleSmall,
                color = if (week.reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("weekly-target-text"),
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { week.share }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Badges earned since the user last looked. */
@Composable
private fun NewBadgesCard(badges: List<Badge>, onSee: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("new-badges"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            val color = MaterialTheme.colorScheme.onSecondaryContainer
            Text(
                if (badges.size == 1) "New badge: ${badges[0].icon} ${badges[0].title}" else "${badges.size} new badges",
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
            if (badges.size > 1) {
                Text(badges.joinToString("  ") { it.icon }, fontSize = 26.sp, color = color)
            } else {
                Text(badges[0].how, style = MaterialTheme.typography.bodyMedium, color = color)
            }
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = onSee, modifier = Modifier.testTag("see-badges")) { Text("See badges") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("badges-ok")) { Text("OK") }
            }
        }
    }
}

/** Two big tiles side by side, for Today as a grid. */
@Composable
private fun TileRow(rows: List<HabitRow>, screen: TodayState, tick: (HabitRow, Spot) -> Unit, onDetails: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        rows.forEach { row ->
            GoalTile(row, tileLine(row, screen), Modifier.weight(1f), onTap = { spot ->
                val done = row.count >= row.goal.target
                when {
                    // A routine's items, and taking back a counted goal, are on its card.
                    row.isRoutine -> onDetails(row.goal.id)
                    done && row.goal.target > 1 -> onDetails(row.goal.id)
                    else -> tick(row, spot)
                }
            }, onHold = { onDetails(row.goal.id) })
        }
        if (rows.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** What a tile says under its name. */
private fun tileLine(row: HabitRow, screen: TodayState): String {
    val goal = row.goal
    return when {
        row.count >= goal.target -> "Done ✓"
        row.isRoutine -> "${row.items.count { it.done }} of ${row.items.size} items"
        goal.target > 1 -> buildString {
            append("${row.count} of ${goal.target}")
            screen.nextSlots[goal.id]?.let { append(" · next by ${momentText(it.at, screen.view.day)}") }
        }
        row.skipped -> "Skipped"
        else -> listOfNotNull("+${goal.points}", goal.minutes?.let { minutesText(it) }).joinToString(" · ")
    }
}

/** A goal as a big tile: tap to tick it, hold it for its card with every button. */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun GoalTile(row: HabitRow, line: String, modifier: Modifier, onTap: (Spot) -> Unit, onHold: () -> Unit) {
    val goal = row.goal
    val spot = remember { Spot() }
    val done = row.count >= goal.target
    val cardSize = LocalCardSize.current
    val strip = goal.color?.let { androidx.compose.ui.graphics.Color(io.github.randomtools3377.checklist.core.Schemes.goalStrip(it, LocalBrightness.current)) }
        ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .height(cardSize.tile)
            .onGloballyPositioned { spot.center = it.boundsInRoot().center }
            .combinedClickable(
                onClickLabel = if (done) "Details" else "Tick",
                onLongClickLabel = "Details, skip or focus",
                onClick = { onTap(spot) },
                onLongClick = onHold,
            )
            .semantics { contentDescription = "${goal.name}: $line" }
            .testTag("tile:${goal.name}"),
        colors = if (done) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { if (!done) drawRect(strip, size = androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx())) }
                .padding(8.dp),
        ) {
            Text(goal.icon, fontSize = cardSize.icon * 1.4f)
            Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/** What is behind Today: nothing, a gradient of the app's colour, or the user's photo, faded toward the background. */
@Composable
private fun TodayBackdrop(look: Appearance) {
    when (look.backdrop) {
        Backdrop.NONE -> Unit
        Backdrop.GRADIENT -> Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
                .testTag("backdrop:gradient"),
        )
        Backdrop.PHOTO -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, look.photoStamp) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(java.io.File(context.filesDir, "background.jpg").path)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            image?.let {
                androidx.compose.foundation.Image(
                    it,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = look.dim / 100f)).testTag("backdrop:photo"))
            }
        }
    }
}
