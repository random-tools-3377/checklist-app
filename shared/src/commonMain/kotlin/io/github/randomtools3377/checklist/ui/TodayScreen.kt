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
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Moves
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.payFor
import io.github.randomtools3377.checklist.core.TodaySection
import io.github.randomtools3377.checklist.core.WeekProgress
import io.github.randomtools3377.checklist.core.Badge
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.OutlinedTextField
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
    onEditTodo: (Long) -> Unit,
    onTimeline: () -> Unit = {},
    onPlanWeek: () -> Unit = {},
    onCharacter: () -> Unit = {},
    onIdeas: (io.github.randomtools3377.checklist.core.SkillArea) -> Unit = {},
    onBudget: () -> Unit = {},
    onSpent: () -> Unit = {},
    onShop: () -> Unit = {},
    onMoodGoals: () -> Unit = {},
    onMoodJournal: () -> Unit = {},
    skipRequest: Long?,
    onSkipRequestHandled: () -> Unit,
    padding: PaddingValues,
) {
    if (screen == null) return
    val view = screen.view
    val backup by vm.backupState.collectAsStateWithLifecycle()
    val week by vm.weekProgress.collectAsStateWithLifecycle()
    val look by vm.appearance.collectAsStateWithLifecycle()
    val character by vm.character.collectAsStateWithLifecycle()
    val characterLook by vm.look.collectAsStateWithLifecycle()
    val characterTitles by vm.characterTitles.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val quests by vm.quests.collectAsStateWithLifecycle()
    val race by vm.race.collectAsStateWithLifecycle()
    val boss by vm.boss.collectAsStateWithLifecycle()
    val grid = look.layout == TodayLayout.GRID
    // In the grid, holding a tile opens its full card here.
    var details by remember { mutableStateOf<Long?>(null) }
    // For a goal with an amount: the box to enter how much, and the running timing.
    val stopwatch by vm.stopwatch.collectAsStateWithLifecycle()
    var entering by remember { mutableStateOf<AmountRequest?>(null) }
    // Moving a goal due today to another day of this week.
    var moving by remember { mutableStateOf<Goal?>(null) }
    moving?.let { goal ->
        MoveDialog(goal, view.day, view.day, onMove = { to -> vm.moveGoal(goal.id, view.day, to) }, onDismiss = { moving = null })
    }
    // Stop on the timing notification: ask how much was done, with the time it took.
    val stopRequest by vm.stopRequest.collectAsStateWithLifecycle()
    LaunchedEffect(stopRequest, stopwatch) {
        val goalId = stopRequest ?: return@LaunchedEffect
        val running = stopwatch ?: return@LaunchedEffect
        if (running.goalId == goalId) {
            val seconds = ((io.github.randomtools3377.checklist.data.nowMillis() - running.startedAt) / 1000).toInt().coerceAtLeast(1)
            entering = AmountRequest(goalId, seconds, Spot())
        }
        vm.stopRequest.value = null
    }
    val newBadges by vm.newBadges.collectAsStateWithLifecycle()
    val boosts by vm.boosts.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    val askMood by vm.askMood.collectAsStateWithLifecycle()
    val showWeekPlan by vm.showWeekPlan.collectAsStateWithLifecycle()
    val todayMood by vm.todayMood.collectAsStateWithLifecycle()
    // The check-in once everything is done, and what the moods show about the goals.
    val moodAfter by vm.moodAfter.collectAsStateWithLifecycle()
    val moodStart by vm.moodStart.collectAsStateWithLifecycle()
    val moodFindings by vm.moodFindings.collectAsStateWithLifecycle()
    val moodMore by vm.moodMore.collectAsStateWithLifecycle()
    val moodFactorList by vm.moodFactorList.collectAsStateWithLifecycle()
    val lockSettings by vm.settings.collectAsStateWithLifecycle()
    val isUnlocked by vm.unlocked.collectAsStateWithLifecycle()
    // Moods sit behind the lock with reasons and notes, so what they show about goals does too.
    val moodsLocked = lockSettings.locks.reasons && !isUnlocked
    // Today's mood opened in full: from its line on Today, or from the mood reminder.
    val moodStartRow by vm.moodStartRow.collectAsStateWithLifecycle()
    val lookForward by vm.lookForward.collectAsStateWithLifecycle()
    val savingFor by vm.savingFor.collectAsStateWithLifecycle()
    val moodPutOff by vm.moodPutOff.collectAsStateWithLifecycle()
    val moodOpen by vm.moodOpen.collectAsStateWithLifecycle()
    var moodDay by remember { mutableStateOf(false) }
    LaunchedEffect(moodOpen) {
        if (moodOpen) {
            moodDay = true
            vm.moodOpen.value = false
        }
    }
    if (moodDay && io.github.randomtools3377.checklist.core.Feature.MOOD in features) {
        MoodDayDialog(
            // With no mood yet, like from the reminder, it asks for one.
            mood = todayMood ?: io.github.randomtools3377.checklist.core.Mood(view.day, 0),
            list = moodFactorList,
            onAdd = { name -> vm.addMoodFactor(name, day = null) },
            onSave = { changed ->
                vm.saveMoodDay(changed)
                moodDay = false
            },
            onDismiss = { moodDay = false },
            onJournal = {
                moodDay = false
                onMoodJournal()
            },
            locked = moodsLocked,
            onUnlock = onUnlock,
            today = true,
        )
    }
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
    // Typing how much to add to an "at most" goal with a unit, like 45 minutes.
    var using by remember { mutableStateOf<Long?>(null) }
    using?.let { id ->
        val row = view.limits.firstOrNull { it.goal.id == id }
        if (row == null) {
            using = null
        } else {
            UseDialog(row, onDismiss = { using = null }) { amount ->
                using = null
                vm.addUse(id, amount)
            }
        }
    }

    // Showing one category at a time, when goals have them.
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = Categories.of(
        screen.plan.map { it.row.goal } + (view.limits + view.thisWeek + view.thisMonth + view.thisYear).map { it.goal } + view.projects.map { it.goal } +
            // To-dos take categories too, so one used only on to-dos has its chip.
            view.todos.map { it.goal },
    )
    val filter = category?.takeIf { it in categories }
    val plan = screen.plan.filter { Categories.shows(filter, it.row.goal) }
    fun shown(rows: List<HabitRow>) = rows.filter { Categories.shows(filter, it.goal) }
    val projects = view.projects.filter { Categories.shows(filter, it.goal) }
    val todos = view.todos.filter { Categories.shows(filter, it.goal) }

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
                        stopwatch = stopwatch,
                        onAmount = { spot, seconds -> details = null; entering = AmountRequest(id, seconds, spot) },
                        onStart = { vm.startStopwatch(id) },
                        // The same buttons as its card in the list: Move for one due today, and a dated to-do's editor.
                        onMove = if (screen.plan.any { it.row.goal.id == id } && Moves.choices(row.goal, view.day, view.day).isNotEmpty()) ({ details = null; moving = row.goal }) else null,
                        onEdit = if (row.goal.kind == GoalKind.TODO) ({ details = null; onEditTodo(id) }) else null,
                    )
                },
            )
        }
    }

    entering?.let { request ->
        val row = (screen.plan.map { it.row } + view.thisWeek + view.thisMonth + view.thisYear).firstOrNull { it.goal.id == request.goalId }
        if (row == null) {
            entering = null
        } else {
            AmountDialog(
                row = row,
                seconds = request.seconds,
                onDismiss = { entering = null },
                onDiscardTiming = {
                    entering = null
                    vm.cancelStopwatch()
                },
                onSave = { amount ->
                    entering = null
                    vm.logAmount(row.goal.id, amount, request.seconds) { result ->
                        if (result !is TickResult.Ticked) return@logAmount
                        if (result.finished) {
                            val gif = row.goal.celebration?.takeIf { Celebration.isValid(it) } ?: celebrations.style.defaultGif
                            if (gif != null) {
                                celebrations.buzz(haptics)
                                celebrations.show(BigCelebration.GoalFinished(row.goal.icon, row.goal.name, result.points, gif))
                            } else {
                                celebrateTick(result, request.spot, bigWhenFinished = true)
                            }
                        } else if (result.points > 0) {
                            // One whole time of a goal done more than once.
                            celebrateTick(result, request.spot, bigWhenFinished = false)
                        } else {
                            celebrations.burst(request.spot.center, "+" + io.github.randomtools3377.checklist.core.Amounts.format(amount) + (row.goal.unit?.let { " $it" } ?: ""))
                        }
                    }
                },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
    TodayBackdrop(look)
    LazyColumn(
        modifier = Modifier.fillMaxSize().readable().testTag("today-list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(longDate(view.day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(bottom = 8.dp))
                if (view.dueToday.isNotEmpty() && Feature.TIMELINE in features) {
                    TextButton(onClick = onTimeline, modifier = Modifier.testTag("open-timeline")) { Text("Timeline") }
                }
            }
            if (showWeekPlan) {
                WeekPlanCard(onPlan = onPlanWeek, onLater = vm::weekPlanned)
                Spacer(Modifier.height(12.dp))
            }
            if (screen.streakPercent > 100) {
                Text(
                    "🔥 Streak bonus: goals pay ${screen.streakPercent - 100}% more today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("streak-bonus"),
                )
            }
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
                        Text("Nothing due today. Add a goal, then tick it off here.", textAlign = TextAlign.Center, modifier = Modifier.testTag("today-empty"))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAddGoal, modifier = Modifier.testTag("today-add-goal")) { Text("Add a goal") }
                    }
                }
            }
        }

        for (choice in look.sections) {
            if (!choice.shown) continue
            when (choice.section) {
                TodaySection.MOOD -> item(key = "mood") {
                    Column {
                    // With check-ins on, the character asks instead, on its own card.
                    val characterAsks = companion?.say?.askMood == true &&
                        look.sections.any { it.section == TodaySection.CHARACTER && it.shown }
                    if (askMood && !characterAsks) {
                        MoodCheckIn(onMood = { vm.setMood(it) }, onLater = vm::skipMood)
                        Spacer(Modifier.height(12.dp))
                    }
                    // Put off with "Not today": a small way to take that back.
                    if (moodPutOff) {
                        Text(
                            "Check in now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = vm::checkInNow).padding(bottom = 8.dp).testTag("mood-check-in-now"),
                        )
                    }
                    // On a low morning the character's card, when it talks, carries the one suggestion.
                    val characterSuggests = character != null && companion?.talks == true && companion?.say?.hardDay == true &&
                        look.sections.any { it.section == TodaySection.CHARACTER && it.shown }
                    todayMood?.let { mood ->
                        if (moodMore) {
                            // Just picked: what's behind it, and a note.
                            MoodMoreCard(
                                mood = mood,
                                list = moodFactorList,
                                locked = moodsLocked,
                                onToggle = { vm.toggleMoodFactor(it) },
                                onAdd = { vm.addMoodFactor(it) },
                                onNote = { vm.setMoodNote(it, after = false) },
                                onUnlock = onUnlock,
                                onDone = vm::closeMoodMore,
                            )
                            Spacer(Modifier.height(12.dp))
                        } else {
                            Text(
                                "Today: " + moodFaces(mood) + (if (mood.note != null || mood.afterNote != null) " · note added" else " · Add a note"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Opens today's mood in full. Notes sit behind the lock with reasons and notes: unlock first.
                                modifier = Modifier.clickable { if (moodsLocked) onUnlock() else moodDay = true }.padding(bottom = 8.dp).testTag("mood-today"),
                            )
                        }
                    }
                    // A morning that started low: a goal still to do that usually lifts the user, to start with.
                    if (!moodsLocked && !characterSuggests) {
                        moodStart?.let { goal ->
                            Text(
                                "💡 " + io.github.randomtools3377.checklist.core.MoodInsights.startWithLine(goal),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp).testTag("mood-start-with"),
                            )
                        }
                    }
                    // And perhaps a small reward to look forward to (never while the shop is locked).
                    if (!characterSuggests) {
                        lookForward?.let { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp).testTag("mood-look-forward"),
                            )
                        }
                    }
                    // Everything due today is done: how does the user feel now?
                    moodAfter?.let { after ->
                        val answered = after.after
                        if (answered == null) {
                            MoodAfterCheckIn(after.morning, after.skipped, onMood = vm::setMoodAfter, onLater = vm::closeMoodAfter)
                        } else {
                            MoodAfterAnswer(
                                morning = after.morning,
                                after = answered,
                                mood = todayMood,
                                list = moodFactorList,
                                locked = moodsLocked,
                                lifts = if (moodsLocked) null else io.github.randomtools3377.checklist.core.MoodInsights.liftsLine(moodFindings),
                                onToggle = { vm.toggleMoodFactor(it) },
                                onAdd = { vm.addMoodFactor(it) },
                                onNote = { vm.setMoodNote(it, after = true) },
                                onUnlock = onUnlock,
                                onMoodGoals = onMoodGoals,
                                onJournal = onMoodJournal,
                                onClose = vm::closeMoodAfter,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    }
                }
                TodaySection.BALANCE -> item(key = "balance") {
                    BalanceCard(
                        view.balance,
                        modifier = Modifier.clickable(onClick = onHistory),
                        note = "Tap for your ${pointsWord()} history",
                        // The starred reward; hidden while the shop is behind the privacy lock.
                        saving = savingFor,
                        onShop = onShop,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                TodaySection.CHARACTER -> if (character != null) item(key = "character") {
                    // With the character on: its level, under the balance.
                    character?.let { sheet ->
                        LevelCard(
                            sheet,
                            characterLook,
                            onOpen = {
                                if (companion?.say?.levelUp == true) vm.levelSeen()
                                onCharacter()
                            },
                            title = characterTitles?.shown,
                            companion = companion,
                            onMood = { vm.setMood(it) },
                            onMoodLater = vm::skipMood,
                            onSuggestion = { row, at -> tick(row, Spot().apply { center = at }) },
                            // On a low morning: the goal that often goes with a better mood, and a reward to look forward to.
                            moodAlso = if (moodsLocked) null else moodStartRow,
                            lookForward = lookForward,
                            onCheered = vm::levelSeen,
                            onIdeas = onIdeas,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                TodaySection.QUESTS -> if (quests?.list?.isNotEmpty() == true) item(key = "quests") {
                    quests?.list?.takeIf { it.isNotEmpty() }?.let { list ->
                        QuestsCard(list)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                TodaySection.RACE -> if (race != null) item(key = "race") {
                    race?.let { standing ->
                        RaceCard(standing)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                TodaySection.BOSS -> if (boss != null) item(key = "boss") {
                    boss?.let { fight ->
                        BossCard(fight) { say ->
                            vm.attackBoss { attack -> say(attack?.let { attackText(it.dealt, it.beaten, it.paid) } ?: "Nothing to use.") }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                TodaySection.WEEKLY_TARGET -> week?.let { progress ->
                    item(key = "weekly-target") {
                        WeeklyTargetCard(progress)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                // Nothing to count yet: no streak card.
                TodaySection.STREAKS -> if (!(view.isEmpty && screen.streaks.done == 0 && screen.streaks.perfect == 0)) {
                    item(key = "streaks") {
                        StreakCard(screen.streaks, screen.streakSettings)
                        // With streak shields on: those held, and the last day one saved.
                        screen.shields?.let { shields ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "🛡️ " + (if (shields.held == 1) "1 shield" else "${shields.held} shields") +
                                        (shields.saved.lastOrNull()?.let { " · one saved your streak on ${shortDate(it)}" } ?: "") +
                                        (if (shields.held < io.github.randomtools3377.checklist.core.Shields.MOST) " · next at ${(screen.streaks.done / io.github.randomtools3377.checklist.core.Shields.EVERY + 1) * io.github.randomtools3377.checklist.core.Shields.EVERY} days" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(top = 4.dp).testTag("shields"),
                                )
                                GuideButton(HelpTopic.SHIELDS)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                TodaySection.PACE -> if (screen.plan.isNotEmpty() || screen.events.isNotEmpty()) {
                    if (screen.events.isNotEmpty()) item(key = "events") {
                        Column(Modifier.padding(bottom = 8.dp).testTag("today-events")) {
                            Text("In your calendar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            screen.events.forEach { EventLine(it) }
                        }
                    }
                    if (screen.plan.isNotEmpty()) item(key = "pace") {
                        PaceCard(screen, onSettings)
                        Spacer(Modifier.height(12.dp))
                        OrderSwitch(screen.recommended) { vm.setTodayOrder(if (it) ORDER_RECOMMENDED else ORDER_OWN) }
                    }
                }
                TodaySection.DUE_TODAY -> {
                    startGoals()
                    if (plan.isNotEmpty()) item(key = "title-today") { SectionTitle("Today") }
                    // Everything due today done (with goals whose week, month or year ends today), none
                    // skipped: the perfect-day bonus comes when the day ends.
                    if (filter == null && boosts.perfectDayBonus > 0 && screen.perfectSoFar) {
                        item(key = "perfect-day") {
                            Text(
                                "🌟 Perfect day so far: +${boosts.perfectDayBonus} when the day ends",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp).testTag("perfect-day"),
                            )
                        }
                    }
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
                                stopwatch = stopwatch,
                                onAmount = { spot, seconds -> entering = AmountRequest(planned.row.goal.id, seconds, spot) },
                                onStart = { vm.startStopwatch(planned.row.goal.id) },
                                onMove = if (Moves.choices(planned.row.goal, view.day, view.day).isNotEmpty()) ({ moving = planned.row.goal }) else null,
                                // A to-do with a date opens its editor from here, to push it to another day.
                                onEdit = if (planned.row.goal.kind == GoalKind.TODO) ({ onEditTodo(planned.row.goal.id) }) else null,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    // "At most" goals: nothing to do, only to stay under, so they come after the day's goals.
                    val limits = shown(view.limits)
                    if (limits.isNotEmpty()) {
                        item(key = "title-limits") { SectionTitle("Stay under") }
                        items(limits, key = { "limit-${it.goal.id}" }) { row ->
                            AtMostCard(
                                row = row,
                                onAdd = { vm.addUse(row.goal.id) },
                                onAmount = { using = row.goal.id },
                                onUndo = { vm.undoUse(row.goal.id) },
                                onConfirm = { vm.confirmAtMost(row.goal.id) },
                                onUnconfirm = { vm.undoHabitTick(row.goal.id) },
                                onSkip = { skipping = row.goal },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                // An extra: shown when turned on, or while there are to-dos.
                TodaySection.TODOS -> if (Feature.TODOS in features || view.todos.isNotEmpty()) {
                    startGoals()
                    item(key = "title-todos") { SectionTitle("To-dos") }
                    item(key = "todo-add") {
                        QuickTodoField(
                            // Added while one category is showing, it takes that category, so it stays on screen.
                            onAdd = { name -> vm.addTodo(name, filter) },
                            onMore = { name -> if (name.isBlank()) onEditTodo(0) else vm.addTodo(name, filter, onEditTodo) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    items(todos, key = { "todo-${it.goal.id}" }) { row ->
                        TodoCard(
                            row = row,
                            onDone = { spot ->
                                // With steps, Done ticks the rest of them, which does the to-do.
                                val action: (Long, (TickResult) -> Unit) -> Unit = if (row.items.isNotEmpty()) vm::finishRoutine else vm::tickHabit
                                action(row.goal.id) { result -> celebrateTick(result, spot, bigWhenFinished = row.items.isNotEmpty()) }
                            },
                            onUndo = { if (row.items.isNotEmpty()) vm.undoRoutine(row.goal.id) else vm.undoHabitTick(row.goal.id) },
                            onEdit = { onEditTodo(row.goal.id) },
                            onItem = { item, spot ->
                                vm.checkItem(item.step.id) { result ->
                                    if (result is TickResult.Ticked && result.finished) celebrateTick(result, spot, bigWhenFinished = true)
                                }
                            },
                            onUncheck = { item -> vm.uncheckItem(item.step.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                TodaySection.THIS_WEEK -> {
                    startGoals()
                    habitSection("This week", shown(view.thisWeek), screen, ::tick, vm, grid, { details = it }, stopwatch, { r -> entering = r }, ::checkItem, { editingTick = it }) { skipping = it }
                }
                TodaySection.THIS_MONTH -> {
                    startGoals()
                    habitSection("This month", shown(view.thisMonth), screen, ::tick, vm, grid, { details = it }, stopwatch, { r -> entering = r }, ::checkItem, { editingTick = it }) { skipping = it }
                }
                TodaySection.THIS_YEAR -> {
                    startGoals()
                    habitSection("This year", shown(view.thisYear), screen, ::tick, vm, grid, { details = it }, stopwatch, { r -> entering = r }, ::checkItem, { editingTick = it }) { skipping = it }
                }
                TodaySection.MONEY -> if (io.github.randomtools3377.checklist.core.Feature.BUDGET in features && !(vm.settings.value.locks.budget && !vm.unlocked.value)) {
                    item(key = "money") { TodayMoneyCard(vm, onBudget, onSpent) }
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
    if (planned.row.settled) return null
    val text = momentText(time, screen.view.day)
    return if (planned.fixed) "at $text" else "by about $text"
}

/**
 * The box to add a to-do: type it and press Add. The calendar button adds it too and opens it, to
 * give it a date or change its points.
 */
@Composable
private fun QuickTodoField(onAdd: (String) -> Unit, onMore: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun add() {
        if (text.isBlank()) return
        onAdd(text)
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            placeholder = { Text("Add a to-do") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f).testTag("todo-field"),
        )
        Spacer(Modifier.width(4.dp))
        OutlinedIconButton(
            onClick = {
                onMore(text)
                text = ""
            },
            modifier = Modifier.semantics { contentDescription = "Add with a date or points" }.testTag("todo-more"),
        ) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
        }
        FilledIconButton(
            onClick = ::add,
            enabled = text.isNotBlank(),
            modifier = Modifier.semantics { contentDescription = "Add to-do" }.testTag("todo-add"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

/** A to-do without a date: open until it's done. Tap it to edit it or give it a date. */
@Composable
private fun TodoCard(
    row: io.github.randomtools3377.checklist.core.TodoRow,
    onDone: (Spot) -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onItem: (StepRow, Spot) -> Unit = { _, _ -> },
    onUncheck: (StepRow) -> Unit = {},
) {
    val goal = row.goal
    val spot = remember { Spot() }
    val cardSize = LocalCardSize.current
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit).testTag("todo:${goal.name}")) {
      Column(Modifier.goalStrip(goal.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(cardSize.padding)) {
            Text(goal.icon, fontSize = cardSize.icon, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (row.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                val pay = payNow(goal.points, goal.id)
                val detail = if (row.items.isEmpty()) "+$pay" else "+$pay · ${row.items.count { it.done }} of ${row.items.size} steps"
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TickButton(
                done = row.done,
                counted = false,
                description = if (row.done) "Undo ${goal.name}" else "Mark ${goal.name} done",
                modifier = Modifier
                    .onGloballyPositioned { spot.center = it.boundsInRoot().center }
                    .testTag("tick:${goal.name}"),
                onClick = { if (row.done) onUndo() else onDone(spot) },
            )
        }
        if (row.items.isNotEmpty()) {
            Column(Modifier.padding(start = 64.dp, end = 12.dp, bottom = 8.dp)) {
                row.items.forEach { item ->
                    RoutineItemLine(item, onTick = { onItem(item, it) }, onUndo = { onUncheck(item) })
                }
            }
        }
      }
    }
}

/** The daily mood check-in: five faces, one tap; "Not today" puts it off until tomorrow. */
@Composable
private fun MoodCheckIn(onMood: (Int) -> Unit, onLater: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("mood-checkin")) {
        Column(Modifier.padding(12.dp)) {
            Text("How are you feeling today?", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                io.github.randomtools3377.checklist.core.Moods.FACES.forEachIndexed { index, face ->
                    Text(
                        face,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .clickable { onMood(index + 1) }
                            .semantics { contentDescription = io.github.randomtools3377.checklist.core.Moods.spoken(index + 1) }
                            .padding(6.dp)
                            .testTag("mood:${index + 1}"),
                    )
                }
            }
            TextButton(onClick = onLater, modifier = Modifier.testTag("mood-later")) { Text("Not today") }
        }
    }
}

/**
 * The second mood check-in, once everything due today is done: the same five faces, gentler after
 * a morning that started low. With something [skipped] today it says "That's everything", not
 * that everything was done. "Not now" puts it off until tomorrow.
 */
@Composable
private fun MoodAfterCheckIn(morning: Int, skipped: Boolean, onMood: (Int) -> Unit, onLater: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("mood-after")) {
        Column(Modifier.padding(12.dp)) {
            Text(
                io.github.randomtools3377.checklist.core.MoodInsights.question(morning, skipped),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("mood-after-question"),
            )
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                io.github.randomtools3377.checklist.core.Moods.FACES.forEachIndexed { index, face ->
                    Text(
                        face,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .clickable { onMood(index + 1) }
                            .semantics { contentDescription = io.github.randomtools3377.checklist.core.Moods.spoken(index + 1).replaceFirst("Mood", "Mood now") }
                            .padding(6.dp)
                            .testTag("mood-after:${index + 1}"),
                    )
                }
            }
            TextButton(onClick = onLater, modifier = Modifier.testTag("mood-after-later")) { Text("Not now") }
        }
    }
}

/**
 * Under the morning's mood, once it is picked: "What's behind it?" with the things that can
 * play a part, and a note. Behind the lock with reasons and notes, it asks to unlock first.
 */
@Composable
private fun MoodMoreCard(
    mood: io.github.randomtools3377.checklist.core.Mood,
    list: List<String>,
    locked: Boolean,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onNote: (String) -> Unit,
    onUnlock: () -> Unit,
    onDone: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("mood-more")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Today: ${io.github.randomtools3377.checklist.core.Moods.face(mood.score)} · " + io.github.randomtools3377.checklist.core.MoodInsights.BEHIND_QUESTION,
                style = MaterialTheme.typography.titleSmall,
            )
            MoodNotes(mood, list, locked, note = mood.note, tag = "mood-factor", noteTag = "mood-more-note", onToggle, onAdd, onNote, onUnlock)
            TextButton(onClick = onDone, modifier = Modifier.testTag("mood-more-done")) { Text("Done") }
        }
    }
}

/** The chips and note box at a check-in, or, behind the lock, a button to unlock them. */
@Composable
private fun MoodNotes(
    mood: io.github.randomtools3377.checklist.core.Mood,
    list: List<String>,
    locked: Boolean,
    note: String?,
    tag: String,
    noteTag: String,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onNote: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    if (locked) {
        OutlinedButton(onClick = onUnlock, modifier = Modifier.testTag("mood-notes-unlock")) { Text("🔒 Notes are locked · Unlock") }
        return
    }
    MoodFactorChips(
        offered = io.github.randomtools3377.checklist.core.MoodFactors.offered(list, mood.factors),
        picked = mood.factors,
        tag = tag,
        onToggle = onToggle,
        onAdd = onAdd,
    )
    MoodNoteField(stored = note, placeholder = "A note, if you like", tag = noteTag, onSave = onNote)
}

/**
 * The answer to the check-in once everything is done: how the mood moved since the morning, and
 * nothing about why: "What played a part?" lets the user say, with a note. Then the goals that
 * often go with a better mood (left out behind the lock), the journal and "Mood and your goals".
 */
@Composable
private fun MoodAfterAnswer(
    morning: Int,
    after: Int,
    mood: io.github.randomtools3377.checklist.core.Mood?,
    list: List<String>,
    locked: Boolean,
    lifts: String?,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onNote: (String) -> Unit,
    onUnlock: () -> Unit,
    onMoodGoals: () -> Unit,
    onJournal: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("mood-after-answer"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                io.github.randomtools3377.checklist.core.MoodInsights.answer(morning, after, inMorning = mood == null || firstLabel(mood) == "Morning"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("mood-after-line"),
            )
            Text(
                io.github.randomtools3377.checklist.core.MoodInsights.PART_QUESTION,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("mood-after-part"),
            )
            if (mood != null) {
                MoodNotes(mood, list, locked, note = mood.afterNote, tag = "mood-after-factor", noteTag = "mood-after-note", onToggle, onAdd, onNote, onUnlock)
            }
            lifts?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("mood-after-lifts")) }
            // Two rows, so the buttons fit a narrow phone.
            TextButton(onClick = onMoodGoals, modifier = Modifier.testTag("mood-goals-open")) { Text("Mood and your goals") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onJournal, modifier = Modifier.testTag("mood-journal-open")) { Text("Mood journal") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose, modifier = Modifier.testTag("mood-after-close")) { Text("Close") }
            }
        }
    }
}

/** Phone backup is off for this app, so a reminder when the last backup is getting old. */
@Composable
private fun BackupReminderCard(days: Int, onBackUp: () -> Unit, onLater: () -> Unit) {
    val phone = LocalPhone.current
    Card(
        modifier = Modifier.fillMaxWidth().testTag("backup-reminder"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Time for a backup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                (if (days < 0) "You haven't backed up yet." else "Your last backup was $days days ago.") +
                    if (phone.outOfDeviceBackups) " ${phone.deviceTitle} backup is off for this app, so your backup file is the only copy."
                    else " A backup keeps a copy of everything somewhere safe.",
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
    if (!row.goal.severalADay || row.settled) return null
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
    stopwatch: Stopwatch?,
    onEnter: (AmountRequest) -> Unit,
    /** Ticks a routine's item; the last one finishes the routine and celebrates, as in the daily list. */
    checkItem: (HabitRow, StepRow, Spot) -> Unit,
    onNote: (Long) -> Unit,
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
            onItem = { item, spot -> checkItem(row, item, spot) },
            onUncheck = { item -> vm.uncheckItem(item.step.id) },
            // The same buttons as a goal due today: Focus for one with a length, and a note on its latest tick.
            onFocus = row.goal.minutes?.let { minutes -> { vm.startFocus(row.goal.id, minutes) } },
            lastTick = screen.lastTicks[row.goal.id],
            onNote = onNote,
            stopwatch = stopwatch,
            onAmount = { spot, seconds -> onEnter(AmountRequest(row.goal.id, seconds, spot)) },
            onStart = { vm.startStopwatch(row.goal.id) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** How fast to go to get through what is left today. */
@Composable
private fun PaceCard(screen: TodayState, onSettings: () -> Unit) {
    val pace: TodayPace? = screen.pace
    // Each time still to do, so "8 glasses" with 2 done counts 6, as the pace shares the time out.
    val left = screen.plan.sumOf { it.row.left }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("pace"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val color = MaterialTheme.colorScheme.onSecondaryContainer
            when {
                left == 0 && screen.plan.any { it.row.skipped } -> Text("That's everything for today.", color = color, style = MaterialTheme.typography.titleSmall)
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
                    if (pace.busyMinutes > 0) {
                        Text("Leaves out ${minutesText(pace.busyMinutes)} in your calendar", color = color, style = MaterialTheme.typography.bodySmall)
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
    /** Opens the box to move this day's goal to another day of its week, when it can move. */
    onMove: (() -> Unit)? = null,
    /** For a goal with an amount: the running Start and Stop timing, if any (of any goal). */
    stopwatch: Stopwatch? = null,
    /** For a goal with an amount: opens the box to enter how much, with the timed seconds if it was timed. */
    onAmount: ((Spot, Int?) -> Unit)? = null,
    onStart: (() -> Unit)? = null,
    /** For a to-do with a date: opens its editor, from a tap on its name or "Change date". */
    onEdit: (() -> Unit)? = null,
) {
    val goal = row.goal
    val spot = remember { Spot() }
    val amountMode = goal.hasAmount && onAmount != null
    val counted = !amountMode && (goal.target > 1 || row.isRoutine)
    val cardSize = LocalCardSize.current
    val rate = LocalPayRate.current
    val boosted = LocalBoosted.current == goal.id
    val streakPercent = LocalStreakPercent.current
    val extras = LocalTodayExtras.current
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.goalStrip(goal.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(cardSize.padding)) {
            Text(goal.icon, fontSize = cardSize.icon, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // A to-do with a date opens its editor on a tap, like one without.
                    modifier = if (onEdit != null) Modifier.clickable(onClickLabel = "Edit", onClick = onEdit).testTag("open-todo:${goal.name}") else Modifier,
                )
                val detail = buildList {
                    add("+${payFor(goal.points, rate, boosted, streakPercent)}" + if (counted) " each" else "")
                    if (counted && goal.bonus > 0) add("bonus +${payFor(goal.bonus, rate, boosted, streakPercent)}")
                    if (boosted) add("🔥 ${io.github.randomtools3377.checklist.core.Rules.current.boostExtraPercent}% more this week")
                    // A "since last done" goal not done on its day waits here until it is.
                    goal.repeat?.lateSince?.let { add("waiting since ${shortDate(it)}") }
                    if (goal.quit) extras.quitDays[goal.id]?.let { add(if (it == 1) "🚭 1 day without" else "🚭 $it days without") }
                    extras.challenges[goal.id]?.let { c -> add(if (c.finished) "🏁 challenge done" else "🏁 ${c.done} of ${c.days} days") }
                    if (!goal.hasAmount) goal.minutes?.let { add(minutesText(it)) }
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (goal.hasAmount) {
                    // "12 of 20 pages · about 16 min left": the time comes from a typed time or the usual speed.
                    val left = goal.minutes?.takeIf { !row.settled && it > 0 }?.let { " · about ${minutesText(it)} left" } ?: ""
                    Text(
                        io.github.randomtools3377.checklist.core.Amounts.progressText(row.progress, goal) + left,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("amount:${goal.name}"),
                    )
                    if (amountMode && !row.settled) {
                        val running = stopwatch?.takeIf { it.goalId == goal.id }
                        if (running != null) {
                            var now by remember { mutableStateOf(io.github.randomtools3377.checklist.data.nowMillis()) }
                            LaunchedEffect(running.startedAt) {
                                while (true) {
                                    now = io.github.randomtools3377.checklist.data.nowMillis()
                                    kotlinx.coroutines.delay(1000)
                                }
                            }
                            val seconds = ((now - running.startedAt) / 1000).coerceAtLeast(0)
                            Text(
                                "⏱ ${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')} · Stop",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable { onAmount?.invoke(spot, ((io.github.randomtools3377.checklist.data.nowMillis() - running.startedAt) / 1000).toInt().coerceAtLeast(1)) }
                                    .padding(vertical = 4.dp)
                                    .testTag("stop:${goal.name}"),
                            )
                        } else if (stopwatch == null && onStart != null) {
                            Text(
                                "▶ Start",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onStart).padding(vertical = 4.dp).testTag("start:${goal.name}"),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ((row.progress / (goal.amount ?: 1.0)).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (timeLabel != null) {
                    Text(
                        if (late) "$timeLabel · late" else timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (late) MaterialTheme.colorScheme.error else goodColor(),
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
                        color = if (isLate) MaterialTheme.colorScheme.error else goodColor(),
                        modifier = Modifier.testTag("next:${goal.name}"),
                    )
                }
                val length = goal.minutes
                if (onFocus != null && !row.settled && length != null && !goal.hasAmount) {
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
                if (goal.quit) {
                    Text(
                        "I slipped",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { extras.onSlip(goal.id) }.padding(vertical = 4.dp).testTag("slip:${goal.name}"),
                    )
                }
                if (row.skipped) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Skipped", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("skipped:${goal.name}"))
                        // A skip made today can be taken back until the day ends, as if it never happened.
                        if (row.canTakeBackSkip) {
                            Text(
                                "Take back the skip",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable { extras.onTakeBackSkip(goal.id) }.padding(vertical = 4.dp).testTag("take-back-skip:${goal.name}"),
                            )
                        }
                        if (onEdit != null) {
                            Text(
                                "Change date",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onEdit).padding(vertical = 4.dp).testTag("change-date:${goal.name}"),
                            )
                        }
                    }
                } else if (!row.done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable(onClick = onSkip).padding(vertical = 4.dp).testTag("skip:${goal.name}"),
                        )
                        if (onMove != null && row.count == 0) {
                            Text(
                                "Move",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onMove).padding(vertical = 4.dp).testTag("move:${goal.name}"),
                            )
                        }
                        if (onEdit != null) {
                            Text(
                                "Change date",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onEdit).padding(vertical = 4.dp).testTag("change-date:${goal.name}"),
                            )
                        }
                    }
                }
                // Its own page: the calendar, runs and reasons.
                extras.onHistory?.let { open ->
                    Text(
                        "History",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { open(goal.id) }.padding(vertical = 4.dp).testTag("history:${goal.name}"),
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
            if (amountMode) {
                if (row.progress > 0) {
                    OutlinedIconButton(
                        onClick = onUndo,
                        modifier = Modifier.semantics { contentDescription = "Take back the last entry of ${goal.name}" }.testTag("undo:${goal.name}"),
                    ) {
                        Text("−", fontSize = 22.sp)
                    }
                }
                if (row.done) {
                    Button(onClick = {}, modifier = Modifier.testTag("tick:${goal.name}").semantics { contentDescription = "${goal.name} is done" }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done")
                    }
                } else {
                    FilledTonalButton(
                        onClick = { onAmount?.invoke(spot, null) },
                        modifier = Modifier
                            .onGloballyPositioned { spot.center = it.boundsInRoot().center }
                            .semantics { contentDescription = "Add how much of ${goal.name} you did" }
                            .testTag("add-amount:${goal.name}"),
                    ) { Text("Add", fontWeight = FontWeight.Bold) }
                }
            } else {
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
                    label = if (goal.quit) "Avoided" else "Done",
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
private fun TickButton(done: Boolean, counted: Boolean, description: String, modifier: Modifier, onClick: () -> Unit, routine: Boolean = false, label: String = "Done") {
    val semantics = Modifier.semantics { contentDescription = description }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    if (done) {
        Button(onClick = onClick, contentPadding = padding, modifier = modifier.then(semantics)) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
    } else {
        FilledTonalButton(onClick = onClick, contentPadding = padding, modifier = modifier.then(semantics)) {
            if (counted && !routine) {
                Text("+1", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(label)
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
                    Text("Achieved", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
            // Its own page: the calendar, and its steps with the day each was done.
            LocalTodayExtras.current.onHistory?.let { open ->
                Text(
                    "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable { open(goal.id) }.padding(vertical = 4.dp).testTag("history:${goal.name}"),
                )
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
        Text("+${payNow(step.step.points, step.step.goalId)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            GoalTile(row, tileLine(row, screen, LocalPayRate.current, LocalBoosted.current, LocalStreakPercent.current), Modifier.weight(1f), onTap = { spot ->
                val done = row.count >= row.goal.target
                when {
                    // A routine's items, an amount to enter, and taking back a counted goal, are on its card.
                    row.isRoutine || row.goal.hasAmount -> onDetails(row.goal.id)
                    // A done one opens its card too: to add a note, or take the tick back.
                    done -> onDetails(row.goal.id)
                    else -> tick(row, spot)
                }
            }, onHold = { onDetails(row.goal.id) })
        }
        if (rows.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** What a tile says under its name. */
private fun tileLine(row: HabitRow, screen: TodayState, rate: io.github.randomtools3377.checklist.core.Difficulty, boosted: Long?, streakPercent: Int): String {
    val goal = row.goal
    return when {
        row.count >= goal.target -> "Done ✓"
        goal.hasAmount -> io.github.randomtools3377.checklist.core.Amounts.progressText(row.progress, goal)
        row.isRoutine -> "${row.items.count { it.done }} of ${row.items.size} items"
        goal.target > 1 -> buildString {
            append("${row.count} of ${goal.target}")
            screen.nextSlots[goal.id]?.let { append(" · next by ${momentText(it.at, screen.view.day)}") }
        }
        row.skipped -> "Skipped"
        else -> listOfNotNull("+${payFor(goal.points, rate, boosted == goal.id, streakPercent)}", goal.minutes?.let { minutesText(it) }).joinToString(" · ")
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
                onLongClickLabel = "Details, skip, move or focus",
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
            val phone = LocalPhone.current
            val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, look.photoStamp) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { phone.backgroundPhoto()?.let { decodeImage(it) } }.getOrNull()
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

/** The box to enter how much of a goal's amount was done, opened by Add or by stopping a timing. */
private class AmountRequest(val goalId: Long, val seconds: Int?, val spot: Spot)

/**
 * An "at most" goal on Today, like no more than 2 coffees: how much is used and left, "+1" for
 * each one as it happens (or an amount, with a unit), and "That's all for today" to have the day
 * done, and paid, before it ends. Over the limit it says so; the miss is asked about like any other.
 */
@Composable
private fun AtMostCard(
    row: HabitRow,
    onAdd: () -> Unit,
    onAmount: () -> Unit,
    onUndo: () -> Unit,
    onConfirm: () -> Unit,
    onUnconfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    val goal = row.goal
    val cardSize = LocalCardSize.current
    val rate = LocalPayRate.current
    val boosted = LocalBoosted.current == goal.id
    val streakPercent = LocalStreakPercent.current
    val extras = LocalTodayExtras.current
    val over = row.overLimit || row.slipped
    val hasUnit = !goal.unit.isNullOrBlank()
    Card(Modifier.fillMaxWidth().testTag("limit:${goal.name}")) {
        Column(Modifier.goalStrip(goal.color)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(cardSize.padding)) {
                Text(goal.icon, fontSize = cardSize.icon, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "At most ${io.github.randomtools3377.checklist.core.AtMost.limitText(goal)} a day · +${payFor(goal.points, rate, boosted, streakPercent)} for a day at or under it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        io.github.randomtools3377.checklist.core.AtMost.text(row.progress, goal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("used:${goal.name}"),
                    )
                    when {
                        row.done -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓ That's all for today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("confirmed:${goal.name}"))
                            Text(
                                "Take it back",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onUnconfirm).padding(vertical = 4.dp).testTag("unconfirm:${goal.name}"),
                            )
                        }
                        row.slipped -> Text(
                            "Over for today, so today is a miss.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("over:${goal.name}"),
                        )
                        row.skips > 0 -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Skipped", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("skipped:${goal.name}"))
                            if (row.canTakeBackSkip) {
                                Text(
                                    "Take back the skip",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.clickable { extras.onTakeBackSkip(goal.id) }.padding(vertical = 4.dp).testTag("take-back-skip:${goal.name}"),
                                )
                            }
                        }
                        !row.overLimit -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "That's all for today",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onConfirm).padding(vertical = 4.dp).testTag("confirm:${goal.name}"),
                            )
                            Text(
                                "Skip",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onSkip).padding(vertical = 4.dp).testTag("skip:${goal.name}"),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (hasUnit) {
                            Text(
                                "Add an amount",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onAmount).padding(vertical = 4.dp).testTag("use-amount:${goal.name}"),
                            )
                        }
                        if (row.progress > 0) {
                            Text(
                                "Take back the last one",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable(onClick = onUndo).padding(vertical = 4.dp).testTag("undo-use:${goal.name}"),
                            )
                        }
                    }
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag("use:${goal.name}").semantics { contentDescription = "Add one to ${goal.name}" },
                ) { Text("+1") }
            }
        }
    }
}

/** How much to add to an "at most" goal with a unit, like 45 minutes. */
@Composable
private fun UseDialog(row: HabitRow, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val goal = row.goal
    val unit = goal.unit.orEmpty()
    var text by remember { mutableStateOf("") }
    val value = io.github.randomtools3377.checklist.core.Amounts.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${goal.icon} ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(io.github.randomtools3377.checklist.core.AtMost.text(row.progress, goal) + ". How much more?", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { typed -> text = typed.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                    label = { Text(if (unit.isNotEmpty()) "How much ($unit)" else "How much") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("field:use"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null, modifier = Modifier.testTag("save-use")) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AmountDialog(row: HabitRow, seconds: Int?, onDismiss: () -> Unit, onDiscardTiming: () -> Unit, onSave: (Double) -> Unit) {
    val goal = row.goal
    val unit = goal.unit.orEmpty()
    val left = row.amountLeft
    var text by remember { mutableStateOf("") }
    val value = io.github.randomtools3377.checklist.core.Amounts.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${goal.icon} ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (seconds != null) {
                    Text("Timed: ${minutesText(((seconds + 59) / 60).coerceAtLeast(1))}.", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    io.github.randomtools3377.checklist.core.Amounts.progressText(row.progress, goal) + " so far. How much did you do?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { typed -> text = typed.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                    label = { Text(if (unit.isNotEmpty()) "How much ($unit)" else "How much") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("field:amount"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSave(left) }, modifier = Modifier.testTag("amount-all")) {
                        Text("All ${io.github.randomtools3377.checklist.core.Amounts.format(left)} left")
                    }
                    if (seconds != null) {
                        TextButton(onClick = onDiscardTiming, modifier = Modifier.testTag("discard-timing")) { Text("Discard the timing") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null, modifier = Modifier.testTag("save-amount")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
