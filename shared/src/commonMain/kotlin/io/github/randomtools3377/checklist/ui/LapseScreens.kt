@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Penalty
import io.github.randomtools3377.checklist.core.PenaltyTask
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.Streaks

/** A reason as picked: one of the four, or one of the user's own by its words. */
data class ReasonChoice(val reason: Reason, val text: String? = null)

/** Reason chips, shared by misses, skips over the limit, gap check-ins and past misses: the four, then the user's own. */
@Composable
internal fun ReasonChips(selected: ReasonChoice?, tagPrefix: String, own: List<String>, onPick: (ReasonChoice) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Reason.entries.filter { it != Reason.OTHER }.forEach { reason ->
            FilterChip(
                selected = selected?.reason == reason,
                onClick = { onPick(ReasonChoice(reason)) },
                label = { Text(reason.label) },
                modifier = Modifier.testTag("$tagPrefix:${reason.name}"),
            )
        }
        own.forEach { text ->
            FilterChip(
                selected = selected?.reason == Reason.OTHER && selected.text == text,
                onClick = { onPick(ReasonChoice(Reason.OTHER, text)) },
                label = { Text(text) },
                modifier = Modifier.testTag("$tagPrefix:own:$text"),
            )
        }
    }
}

/**
 * Misses waiting for a reason. It fills the screen until each one has a reason, or turns out to
 * have been a forgotten tick. Goals in [hiddenGoals], private ones while locked, aren't named.
 */
@Composable
fun MissesScreen(misses: List<Lapse>, vm: MainViewModel, hiddenGoals: Set<Long> = emptySet(), onUnlock: () -> Unit = {}) {
    BackButton {} // can't be skipped
    val reasons = remember { mutableStateMapOf<Long, ReasonChoice>() }
    val own by vm.ownReasons.collectAsStateWithLifecycle()
    val notes = remember { mutableStateMapOf<Long, String>() }
    // A miss made by "I slipped" on a goal to quit is worded as a slip.
    val quit by vm.quitGoals.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    fun slipped(miss: Lapse) = Lapses.isSlip(miss, miss.goalId in quit, settings.dayEndHour)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("misses")) {
        Column(Modifier.padding(16.dp)) {
            GuideTitle("What happened?", HelpTopic.SKIPS_AND_MISSES)
            Text(
                when {
                    misses.size == 1 && slipped(misses[0]) -> "You said you slipped on this one. Give it a reason to carry on."
                    misses.size == 1 -> "This one wasn't done or skipped in time. Give it a reason to carry on."
                    else -> "These weren't done or skipped in time. Give each a reason to carry on."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("misses-list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Several waiting: one tap gives them all the same reason. Each card can still be changed.
            if (misses.size > 1) {
                item(key = "same-reason") {
                    OutlinedCard(Modifier.fillMaxWidth().testTag("same-reason")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Same reason for all", style = MaterialTheme.typography.titleSmall)
                            val shared = misses.map { reasons[it.id] }.distinct().singleOrNull()
                            ReasonChips(shared, "reason-all", own) { choice -> misses.forEach { reasons[it.id] = choice } }
                            Text(
                                "One tap fills in every one below. Each can still be changed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(misses, key = { it.id }) { miss ->
                val slip = slipped(miss)
                Card(Modifier.fillMaxWidth().testTag("miss:${miss.label}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (miss.goalId in hiddenGoals) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔒 ${io.github.randomtools3377.checklist.core.PrivateGoals.NAME}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = onUnlock) { Text("Unlock") }
                            }
                        } else {
                            Text(miss.label, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            buildString {
                                append(if (slip) "You slipped on ${shortDate(miss.day)}" else "Missed ${shortDate(miss.day)}")
                                if (miss.overLimit) append(" · over the limit: −${pointsText(miss.cost)} and a penalty task")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (miss.overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReasonChips(reasons[miss.id], "reason:${miss.label}:${miss.day}", own) { reasons[miss.id] = it }
                        OutlinedTextField(
                            value = notes[miss.id] ?: "",
                            onValueChange = { notes[miss.id] = it.take(200) },
                            label = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (miss.period != null) {
                            TextButton(onClick = { vm.forgotToTick(miss.id) }, modifier = Modifier.testTag("forgot:${miss.label}:${miss.day}")) {
                                Text(if (slip) "Undo, I didn't slip" else "I did it, I forgot to tick it")
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { misses.forEach { miss -> reasons[miss.id]?.let { vm.explain(miss.id, it.reason, notes[miss.id], it.text) } } },
            enabled = misses.all { reasons[it.id] != null },
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("misses-done"),
        ) { Text("Carry on") }
    }
}

/**
 * The penalty task, owed for going over the limit. It fills the screen until it is done: first
 * the user picks to make up a missed goal or to draw one of their penalty tasks, then does it.
 * Goals in [hiddenGoals], private ones while locked, aren't named or offered until unlocked.
 */
@Composable
fun PenaltyScreen(
    penalty: Penalty,
    makeUp: List<Goal>,
    tasks: List<PenaltyTask>,
    vm: MainViewModel,
    hiddenGoals: Set<Long> = emptySet(),
    onUnlock: () -> Unit = {},
) {
    BackButton {} // no putting it off
    var newTask by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("penalty"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GuideTitle("Penalty task", HelpTopic.PENALTY)
        Text(
            if (penalty.gapId != null) "There was a gap, so there's one penalty task for the whole stretch, to do now. It pays nothing."
            else "You went over the limit, so there's a penalty task to do now. It pays nothing.",
            style = MaterialTheme.typography.bodyMedium,
        )
        // Owed for a skip made today: the skip can still be taken back, and this task goes with it.
        val skip by produceState<Lapse?>(null, penalty.id) { value = vm.skipBehind(penalty) }
        skip?.let { skipped ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Changed your mind? Take the skip back and it's as if it never happened: no penalty task, and the ${pointsWord()} come back.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { vm.takeBackThisSkip(skipped) }, modifier = Modifier.fillMaxWidth().testTag("penalty-take-back-skip")) {
                        Text("Take back the skip")
                    }
                }
            }
        }
        val hiddenGoal = penalty.goalId?.let { it in hiddenGoals } == true
        val label = if (hiddenGoal) "🔒 ${io.github.randomtools3377.checklist.core.PrivateGoals.NAME}" else penalty.label
        if (penalty.choice != null && label != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (penalty.goalId != null) "Make up:" else "Your penalty task:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.testTag("penalty-task"),
                    )
                    if (hiddenGoal) TextButton(onClick = onUnlock) { Text("Unlock", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
            Button(onClick = { vm.finishPenalty(penalty.id) }, modifier = Modifier.fillMaxWidth().testTag("penalty-done")) { Text("Done") }
            return@Column
        }

        Text("Pick one:", style = MaterialTheme.typography.titleMedium)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Make up a missed goal", style = MaterialTheme.typography.titleSmall)
                if (makeUp.isEmpty()) {
                    Text("Nothing was skipped or missed recently to make up.", style = MaterialTheme.typography.bodySmall)
                }
                makeUp.filter { it.id !in hiddenGoals }.forEach { goal ->
                    OutlinedButton(onClick = { vm.makeUp(penalty.id, goal.id) }, modifier = Modifier.fillMaxWidth().testTag("makeup:${goal.name}")) {
                        Text("${goal.icon} ${goal.name}")
                    }
                }
                val hiddenCount = makeUp.count { it.id in hiddenGoals }
                if (hiddenCount > 0) {
                    TextButton(onClick = onUnlock) {
                        Text(if (hiddenCount == 1) "🔒 1 private goal · Unlock" else "🔒 $hiddenCount private goals · Unlock")
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Draw one of your penalty tasks at random", style = MaterialTheme.typography.titleSmall)
                if (tasks.isEmpty()) {
                    Text("You haven't written any yet. Write one now:", style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(onClick = { vm.drawTask(penalty.id) }, modifier = Modifier.fillMaxWidth().testTag("draw-task")) {
                        Text("Draw from your ${tasks.size} penalty tasks")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTask,
                        onValueChange = { newTask = it.take(80) },
                        label = { Text("A penalty task, like 20 push-ups") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("new-penalty-task"),
                    )
                    TextButton(
                        onClick = {
                            vm.addPenaltyTask(newTask)
                            newTask = ""
                        },
                        enabled = newTask.isNotBlank(),
                        modifier = Modifier.testTag("add-penalty-task"),
                    ) { Text("Add") }
                }
            }
        }
    }
}

/** Asks before skipping, and for a reason when the skip goes over the limit. */
@Composable
fun SkipDialog(goal: Goal, vm: MainViewModel, onDone: () -> Unit) {
    val check by produceState<SkipCheck?>(null, goal.id) { value = vm.checkSkip(goal.id) }
    var reason by remember { mutableStateOf<ReasonChoice?>(null) }
    var note by remember { mutableStateOf("") }
    val own by vm.ownReasons.collectAsStateWithLifecycle()
    val allowed = check as? SkipCheck.Allowed
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Skip ${goal.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    check == null -> Text("…")
                    allowed == null -> Text("It can't be skipped now: it's done, or not due today.")
                    !allowed.overLimit -> Text(
                        "That uses ${allowed.used + 1} of the ${allowed.allowed} skips and misses allowed " +
                            "this ${if (goal.allowanceWindow == io.github.randomtools3377.checklist.core.Period.MONTHLY) "month" else "week"}. No reason needed.",
                    )
                    else -> {
                        Text(
                            "This goes over the limit (${allowed.used} of ${allowed.allowed} already used). " +
                                "It needs a reason, costs ${pointsText(allowed.cost)}, and there's a penalty task to do right away.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        ReasonChips(reason, "skip-reason", own) { reason = it }
                        OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("Note (optional)") })
                    }
                }
                // A skip is never counted as done, and it isn't final until the day ends.
                if (allowed != null) {
                    Text(
                        "A skipped goal doesn't count as done. You can take the skip back until the day ends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("skip-note"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.skip(goal.id, reason?.reason, note, reason?.text) { if (it !is SkipResult.NeedsReason) onDone() }
                },
                enabled = allowed != null && (!allowed.overLimit || reason != null),
                modifier = Modifier.testTag("confirm-skip"),
            ) { Text(if (allowed?.overLimit == true) "Skip anyway" else "Skip") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** The current streaks. When every one is running at once, it is shown big and flashy. */
@Composable
fun StreakCard(streaks: Streaks, settings: StreakSettings) {
    val all = streaks.allRunning
    val pop = remember { Animatable(if (all) 0.6f else 1f) }
    LaunchedEffect(all) { if (all) pop.animateTo(1f, tween(600)) }
    val shape = RoundedCornerShape(16.dp)
    val modifier = if (all) {
        Modifier.background(
            Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFC145), Color(0xFF7BD389), Color(0xFF5B8DEF), Color(0xFFB57EDC))),
            shape,
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape)
    }
    Box(modifier.fillMaxWidth().padding(if (all) 3.dp else 0.dp).testTag("streaks")) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = if (all) 0.92f else 0f), shape).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🔥 ${streaks.done}",
                    fontSize = if (all) 30.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(pop.value),
                )
                Spacer(Modifier.padding(start = 8.dp))
                Column {
                    Text(if (streaks.done == 1) "day in a row" else "days in a row", style = MaterialTheme.typography.titleSmall)
                    Text(settings.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (all) {
                Text(
                    "Every streak is going! 🎉",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).scale(pop.value),
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StreakChip("Miss-free", streaks.noMisses)
                StreakChip("Skip-free", streaks.noSkips)
                StreakChip("Within the limit", streaks.withinLimit)
                StreakChip("Perfect days", streaks.perfect)
            }
        }
    }
}

@Composable
private fun StreakChip(label: String, days: Int) {
    val running = days > 0
    Box(
        Modifier
            .background(
                if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            "$label ${if (running) "· $days" else "· 0"}",
            style = MaterialTheme.typography.labelMedium,
            color = if (running) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown in place of something behind the privacy lock. */
@Composable
fun LockedPanel(what: String, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp).testTag("locked"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🔒", fontSize = 48.sp)
        Text("$what is locked", style = MaterialTheme.typography.titleMedium)
        Text("It stays open until you leave the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onUnlock, modifier = Modifier.testTag("unlock")) { Text("Unlock") }
    }
}

/**
 * The gap check-in: it looks like the user stopped for a while. One reason and an optional note
 * cover the whole stretch, and are marked in the history. Points still came off for each miss.
 * Each goal missed in the stretch is listed by day with its own "I forgot to tick it": that
 * ticks it for that day and gives back what the miss cost, and when every miss turns out to be
 * a forgotten tick the gap goes away. Goals in [hiddenGoals], private ones while locked, aren't named.
 */
@Composable
fun GapCheckInScreen(
    gap: io.github.randomtools3377.checklist.core.Gap,
    vm: MainViewModel,
    hiddenGoals: Set<Long> = emptySet(),
    onUnlock: () -> Unit = {},
) {
    BackButton {}
    var reason by remember { mutableStateOf<ReasonChoice?>(null) }
    val own by vm.ownReasons.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }
    val misses by vm.gapMisses.collectAsStateWithLifecycle()
    val quit by vm.quitGoals.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp).testTag("gap")) {
        LazyColumn(Modifier.weight(1f).testTag("gap-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "top") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideTitle("Looks like you stopped for a while", HelpTopic.GAPS)
                    Text(
                        if (gap.from == gap.to) shortDate(gap.from) else "${shortDate(gap.from)} to ${shortDate(gap.to)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when (gap.kind) {
                            io.github.randomtools3377.checklist.core.GapKind.NOTHING_TICKED -> "Nothing was ticked on these days."
                            io.github.randomtools3377.checklist.core.GapKind.POINT_LOSS -> "A lot of ${pointsWord()} were lost in one week."
                        } + " What happened? One reason covers the whole stretch.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val points = misses.sumOf { it.cost }
                    if (misses.isNotEmpty()) {
                        Text(
                            "${pointsTitle()} still come off for each miss: ${if (misses.size == 1) "1 miss" else "${misses.size} misses"}" +
                                if (points > 0) ", −${pointsText(points)}." else ".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("gap-cost"),
                        )
                    }
                    if (misses.any { it.period != null }) {
                        Text(
                            "Did some of it and forgot to tick it? See \"What was missed\" below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "reason") { ReasonChips(reason, "gap-reason", own) { reason = it } }
            item(key = "note") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        label = { Text("What happened (optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("gap-note"),
                    )
                    Text("It's marked in your history with what you write here.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (misses.isNotEmpty()) {
                item(key = "missed") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("What was missed", style = MaterialTheme.typography.titleMedium)
                        if (misses.any { it.period != null }) {
                            Text(
                                "Did one and forgot to tick it? Say so here: it's ticked for that day and you get back what the miss cost. " +
                                    "If that goes for all of them, there was no gap, and it goes away with no penalty task.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                misses.groupBy { it.day }.forEach { (day, onDay) ->
                    item(key = "day-$day") {
                        Text(longDate(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(onDay, key = { it.id }) { miss ->
                        val hidden = miss.goalId in hiddenGoals
                        val slip = Lapses.isSlip(miss, miss.goalId in quit, settings.dayEndHour)
                        OutlinedCard(Modifier.fillMaxWidth().testTag("gap-miss:${miss.label}:${miss.day}")) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (hidden) "🔒 ${io.github.randomtools3377.checklist.core.PrivateGoals.NAME}" else miss.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    val details = listOfNotNull(
                                        "You slipped".takeIf { slip },
                                        "Its deadline passed".takeIf { miss.period == null },
                                        "−${pointsText(miss.cost)}".takeIf { miss.cost > 0 },
                                    )
                                    if (details.isNotEmpty()) {
                                        Text(
                                            details.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (miss.cost > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                when {
                                    hidden -> TextButton(onClick = onUnlock) { Text("Unlock") }
                                    // A project's missed deadline has no tick to forget.
                                    miss.period != null -> TextButton(
                                        onClick = { vm.forgotToTick(miss.id) },
                                        modifier = Modifier.testTag("gap-forgot:${miss.label}:${miss.day}"),
                                    ) { Text(if (slip) "Undo, I didn't slip" else "I forgot to tick it") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { reason?.let { vm.explainGap(gap.id, it.reason, note, it.text) } },
            enabled = reason != null,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("gap-done"),
        ) { Text("Carry on") }
    }
}
