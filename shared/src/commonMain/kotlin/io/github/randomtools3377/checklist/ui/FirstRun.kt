@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * The first-run setup: a few short steps for someone brand new, in place of a tour. Each step
 * can be skipped, and then Today opens. Someone who already has goals, or who answered the old
 * welcome card, never sees it. The full guides stay behind ⓘ and in Help.
 */

/** The setup's steps, in order. */
enum class SetupStep { PARTS, GOAL, DAY }

/** A main part of the app the setup offers, with the extras it turns on. Goals and habits are always on. */
enum class SetupPart(val icon: String, val title: String, val hint: String, val features: List<Feature>) {
    TODOS("📝", "To-dos", "Quick one-off tasks, with a date or without", listOf(Feature.TODOS)),
    MOOD("🙂", "Mood check-in", "How you feel each day, kept in a mood journal", listOf(Feature.MOOD)),
    BUDGET("💰", "Budget", "Your pay, spending, bills and savings", listOf(Feature.BUDGET)),
    GAME(
        "🎮", "The game", "A character that levels up, daily quests, a weekly boss and more",
        listOf(
            Feature.CHARACTER, Feature.CHECKINS, Feature.QUESTS, Feature.COMBOS, Feature.RACE,
            Feature.BOSSES, Feature.LUCKY, Feature.SHIELDS,
        ),
    ),
}

/** A first goal the setup suggests, added with one tap as a daily habit. */
data class FirstGoal(val icon: String, val name: String, val points: Int = 5)

object FirstRun {
    /** The step the setup is at, by name, or [DONE] once it is finished or skipped. */
    const val SETTING = "setup"
    const val DONE = "done"

    /** The first goals on offer. */
    val GOALS: List<FirstGoal> = listOf(
        FirstGoal("💧", "Drink water"),
        FirstGoal("📖", "Read"),
        FirstGoal("🚶", "Go for a walk"),
        FirstGoal("🧘", "Stretch"),
        FirstGoal("🛏️", "Make the bed"),
    )

    /** The start-of-day hours offered, and the end-of-day hours (more of both are in Settings). */
    val START_HOURS: List<Int> = listOf(6, 7, 8, 9)
    val END_HOURS: List<Int> = listOf(0, 1, 2, 3, 4)

    /**
     * The step to show, or null for none: once started, the step it is at; before that, the
     * first step for someone brand new (no goals, none removed, and the old welcome card never
     * answered, [guide]). Under the app's own tests it shows only when a test starts it.
     */
    fun step(setting: String?, guide: String?, nothingYet: Boolean, underTest: Boolean): SetupStep? {
        if (setting == DONE) return null
        SetupStep.entries.firstOrNull { it.name == setting }?.let { return it }
        if (underTest) return null
        return if (guide != "done" && nothingYet) SetupStep.PARTS else null
    }

    /** The step after [step], or null after the last. */
    fun next(step: SetupStep): SetupStep? = SetupStep.entries.getOrNull(step.ordinal + 1)

    /** The step before [step], or null before the first. */
    fun before(step: SetupStep): SetupStep? = SetupStep.entries.getOrNull(step.ordinal - 1)

    /** The extras to turn on for the [parts] picked. */
    fun features(parts: Collection<SetupPart>): List<Feature> =
        SetupPart.entries.filter { it in parts }.flatMap { it.features }.distinct()

    /** The parts whose extras are all [on] already, shown switched on (after coming back with the back arrow). */
    fun picked(on: Set<Feature>): List<SetupPart> = SetupPart.entries.filter { part -> part.features.all { it in on } }

    /**
     * What "Next" switches, given the extras [on] now: a part's extras on when it is picked, and
     * off when it was picked before and is switched off now. Nothing else changes.
     */
    fun changes(parts: Collection<SetupPart>, on: Set<Feature>): Map<Feature, Boolean> = buildMap {
        for (part in SetupPart.entries) {
            val wanted = part in parts
            for (feature in part.features) if ((feature in on) != wanted) put(feature, wanted)
        }
    }

    /** [goal] as a habit due every day from [today]. */
    fun draft(goal: FirstGoal, today: LocalDate): HabitDraft = HabitDraft(
        id = 0, name = goal.name, icon = goal.icon, points = goal.points, repeat = Repeat(RepeatKind.EVERY_N_DAYS, today),
        target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null,
    )
}

/**
 * The setup, filling the app: one short step at a time, each with "Skip". [onOwnGoal] opens the
 * habit editor; once it closes, the setup carries on where it was.
 */
@Composable
fun FirstRunScreen(step: SetupStep, vm: MainViewModel, onOwnGoal: () -> Unit) {
    val back = FirstRun.before(step)
    BackButton(enabled = back != null) { back?.let { vm.setupTo(it) } }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("setup"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            if (back != null) {
                IconButton(onClick = { vm.setupTo(back) }, modifier = Modifier.align(Alignment.CenterStart).testTag("setup-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.Center)) {
                SetupStep.entries.forEach { s ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (s == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().testTag("setup-step:${step.name}"), horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                SetupStep.PARTS -> PartsStep(vm)
                SetupStep.GOAL -> GoalStep(vm, onOwnGoal)
                SetupStep.DAY -> DayStep(vm)
            }
        }
    }
}

/** A step's big emoji and its question. */
@Composable
private fun StepTitle(emoji: String, title: String, line: String? = null) {
    Spacer(Modifier.height(16.dp))
    Text(emoji, fontSize = 48.sp)
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    if (line != null) {
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** "Skip" on the left and the step's own button on the right. */
@Composable
private fun StepButtons(onSkip: (() -> Unit)?, next: String, onNext: (() -> Unit)?, nextTag: String = "setup-next") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        if (onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.testTag("setup-skip")) { Text("Skip") }
        Spacer(Modifier.weight(1f))
        if (onNext != null) Button(onClick = onNext, modifier = Modifier.testTag(nextTag)) { Text(next) }
    }
}

/** The welcome, and the main parts to use, each a switch. "Next" turns on the ones picked. */
@Composable
private fun PartsStep(vm: MainViewModel) {
    // Back here with the back arrow, the parts already turned on show on.
    val on by vm.features.collectAsStateWithLifecycle()
    var picked by rememberSaveable { mutableStateOf(FirstRun.picked(on).map { it.name }) }
    Spacer(Modifier.height(16.dp))
    Text("👋", fontSize = 48.sp)
    Text(
        "Welcome! Tick off your goals to earn ${pointsWord()}, then spend them on rewards you choose.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp).testTag("setup-welcome"),
    )
    Text("What would you like to use?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        "Goals and habits are always on.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    SetupPart.entries.forEach { part ->
        SwitchRow("${part.icon} ${part.title}", part.name in picked, "setup-part:${part.name}", hint = part.hint) { on ->
            picked = if (on) picked + part.name else picked - part.name
        }
    }
    Text(
        "Change these any time in Settings → Features.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    StepButtons(
        onSkip = { vm.setupTo(SetupStep.GOAL) },
        next = "Next",
        onNext = { vm.setupParts(SetupPart.entries.filter { it.name in picked }) },
    )
}

/** A first goal: one tap adds a suggestion, or "Add my own" opens the editor. */
@Composable
private fun GoalStep(vm: MainViewModel, onOwnGoal: () -> Unit) {
    val view by vm.goalsView.collectAsStateWithLifecycle()
    val goals = view?.goals.orEmpty()
    // Tapped already, so a quick second tap doesn't add it twice.
    var tapped by remember { mutableStateOf(setOf<String>()) }
    StepTitle("🎯", "Add a first goal", "Tap one to add it as a daily habit, or add your own.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        FirstRun.GOALS.forEach { goal ->
            val added = goal.name in tapped || goals.any { it.name == goal.name }
            FilterChip(
                selected = added,
                onClick = {
                    if (!added) {
                        tapped = tapped + goal.name
                        vm.addFirstGoal(goal)
                    }
                },
                label = { Text((if (added) "✓ " else "") + "${goal.icon} ${goal.name}") },
                modifier = Modifier.testTag("setup-goal:${goal.name}"),
            )
        }
    }
    OutlinedButton(onClick = onOwnGoal, modifier = Modifier.padding(top = 12.dp).testTag("setup-own")) { Text("Add my own") }
    if (goals.isNotEmpty()) {
        Text(
            if (goals.size == 1) "1 goal added. Change it any time in Goals." else "${goals.size} goals added. Change them any time in Goals.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp).testTag("setup-added"),
        )
    }
    val next = { vm.setupTo(SetupStep.DAY) }
    if (goals.isEmpty()) StepButtons(onSkip = next, next = "Next", onNext = null) else StepButtons(onSkip = null, next = "Next", onNext = next)
}

/** When the day starts and ends, picked from a few; "Done" saves them and opens Today. */
@Composable
private fun DayStep(vm: MainViewModel) {
    val phone = LocalPhone.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Minutes after midnight, as picked here; kept until "Done".
    var start by rememberSaveable { mutableStateOf(settings.dayStart?.let { it.hour * 60 + it.minute }) }
    var end by rememberSaveable { mutableStateOf(settings.dayEndHour) }
    var picking by remember { mutableStateOf(false) }
    StepTitle("🌅", "When does your day start and end?")
    Text("My day starts at", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FirstRun.START_HOURS.forEach { hour ->
            FilterChip(
                selected = start == hour * 60,
                onClick = { start = hour * 60 },
                label = { Text(timeText(LocalTime(hour, 0))) },
                modifier = Modifier.testTag("setup-start:$hour"),
            )
        }
        val own = start?.takeIf { minute -> FirstRun.START_HOURS.none { it * 60 == minute } }
        FilterChip(
            selected = own != null,
            onClick = { picking = true },
            label = { Text(own?.let { timeText(it.toTime()) } ?: "Other…") },
            modifier = Modifier.testTag("setup-start-other"),
        )
    }
    Text(
        "Goals done several times a day are spread out from then" + if (phone.hasPlanNotifications) ", and the morning plan comes then." else ".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    )
    Text("My day ends at", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FirstRun.END_HOURS.forEach { hour ->
            FilterChip(
                selected = end == hour,
                onClick = { end = hour },
                label = { Text(hourText(hour)) },
                modifier = Modifier.testTag("setup-end:$hour"),
            )
        }
    }
    Text(
        "Ticks before then count for the day before, so a late night still counts. More choices are in Settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    StepButtons(
        onSkip = { vm.setupTo(null) },
        next = "Done",
        onNext = { vm.finishSetup(start?.toTime(), end) },
        nextTag = "setup-done",
    )
    if (picking) {
        val initial = start ?: (7 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = is24HourClock())
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = { start = state.hour * 60 + state.minute; picking = false }, modifier = Modifier.testTag("setup-start-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
