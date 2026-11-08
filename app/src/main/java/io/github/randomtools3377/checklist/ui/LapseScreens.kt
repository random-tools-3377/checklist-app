@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import io.github.randomtools3377.checklist.core.Penalty
import io.github.randomtools3377.checklist.core.PenaltyTask
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.Streaks

/** Reason chips, shared by misses and skips over the limit. */
@Composable
private fun ReasonChips(selected: Reason?, tagPrefix: String, onPick: (Reason) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Reason.entries.forEach { reason ->
            FilterChip(
                selected = selected == reason,
                onClick = { onPick(reason) },
                label = { Text(reason.label) },
                modifier = Modifier.testTag("$tagPrefix:${reason.name}"),
            )
        }
    }
}

/**
 * Misses waiting for a reason. It fills the screen until each one has a reason, or turns out to
 * have been a forgotten tick.
 */
@Composable
fun MissesScreen(misses: List<Lapse>, vm: MainViewModel) {
    BackHandler {} // can't be skipped
    val reasons = remember { mutableStateMapOf<Long, Reason>() }
    val notes = remember { mutableStateMapOf<Long, String>() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("misses")) {
        Column(Modifier.padding(16.dp)) {
            Text("What happened?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (misses.size == 1) "This one wasn't done or skipped in time. Give it a reason to carry on."
                else "These weren't done or skipped in time. Give each a reason to carry on.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(misses, key = { it.id }) { miss ->
                Card(Modifier.fillMaxWidth().testTag("miss:${miss.label}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(miss.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append("Missed ${shortDate(miss.day)}")
                                if (miss.overLimit) append(" · over the limit: −${miss.cost} points and a penalty task")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (miss.overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReasonChips(reasons[miss.id], "reason:${miss.label}") { reasons[miss.id] = it }
                        OutlinedTextField(
                            value = notes[miss.id] ?: "",
                            onValueChange = { notes[miss.id] = it.take(200) },
                            label = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (miss.period != null) {
                            TextButton(onClick = { vm.forgotToTick(miss.id) }, modifier = Modifier.testTag("forgot:${miss.label}")) {
                                Text("I did it, I forgot to tick it")
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { misses.forEach { miss -> reasons[miss.id]?.let { vm.explain(miss.id, it, notes[miss.id]) } } },
            enabled = misses.all { reasons[it.id] != null },
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("misses-done"),
        ) { Text("Carry on") }
    }
}

/**
 * The penalty task, owed for going over the limit. It fills the screen until it is done: first
 * the user picks to make up a missed goal or to draw one of their penalty tasks, then does it.
 */
@Composable
fun PenaltyScreen(penalty: Penalty, makeUp: List<Goal>, tasks: List<PenaltyTask>, vm: MainViewModel) {
    BackHandler {} // no putting it off
    var newTask by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp).testTag("penalty"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Penalty task", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("You went over the limit, so there's a penalty task to do now. It pays nothing.", style = MaterialTheme.typography.bodyMedium)
        val label = penalty.label
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
                makeUp.forEach { goal ->
                    OutlinedButton(onClick = { vm.makeUp(penalty.id, goal.id) }, modifier = Modifier.fillMaxWidth().testTag("makeup:${goal.name}")) {
                        Text("${goal.icon} ${goal.name}")
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
    var reason by remember { mutableStateOf<Reason?>(null) }
    var note by remember { mutableStateOf("") }
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
                        ReasonChips(reason, "skip-reason") { reason = it }
                        OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("Note (optional)") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.skip(goal.id, reason, note) { if (it !is SkipResult.NeedsReason) onDone() }
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
                StreakChip("No misses", streaks.noMisses)
                StreakChip("No skips", streaks.noSkips)
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
