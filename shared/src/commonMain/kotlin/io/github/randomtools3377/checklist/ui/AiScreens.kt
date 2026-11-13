package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.randomtools3377.checklist.core.AiFile
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.GoalFromSentence
import io.github.randomtools3377.checklist.core.GoalIdea
import io.github.randomtools3377.checklist.core.ReviewSummary
import io.github.randomtools3377.checklist.core.VoiceCommand
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import io.github.randomtools3377.checklist.data.AiStatus
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import io.github.randomtools3377.checklist.core.AiTrouble
import io.github.randomtools3377.checklist.core.AreaIdea
import io.github.randomtools3377.checklist.core.CoachNote
import io.github.randomtools3377.checklist.core.CoachNotes
import io.github.randomtools3377.checklist.core.IdeaSuggestion
import io.github.randomtools3377.checklist.core.ProjectSteps
import io.github.randomtools3377.checklist.core.describe
import io.github.randomtools3377.checklist.core.missing
import io.github.randomtools3377.checklist.core.suggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/** One line on how the AI stands, for the Features page and the goal editor. */
fun AiStatus?.describe(): String = when {
    this == null -> "Checking Checklist AI…"
    !installed -> "Checklist AI isn't installed. It's a separate app, on the same Releases page as this one: " +
        "checklist-ai-$appVersion.apk. Keeping the AI separate keeps this app small for anyone who doesn't use it."
    problem != null -> "Checklist AI didn't answer ($problem). Try opening it once."
    !ready -> "Checklist AI is installed. Open it to download and add its AI file (${AiFile.sizeText}, once)."
    outOfStep -> "Ready, but Checklist AI is version $version and this app is $appVersion. " +
        "Install both from the same Release so they stay in step."
    else -> "Ready: Checklist AI $version, with its AI file."
}

/** Under the AI helper's switch: how it stands, and the way to the next step. */
@Composable
fun AiStatusPanel(vm: MainViewModel) {
    val status by vm.aiStatus.collectAsState()
    val phone = LocalPhone.current
    // Checked again on coming back, like after installing Checklist AI or adding its file.
    LifecycleResumeEffect(Unit) {
        vm.refreshAi()
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            status.describe(),
            style = MaterialTheme.typography.bodySmall,
            color = if (status?.usable == true && status?.outOfStep != true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("ai-status"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status?.installed == false) {
                TextButton(onClick = { phone.openReleases() }, modifier = Modifier.testTag("ai-get")) { Text("Get Checklist AI") }
            } else if (status != null) {
                TextButton(onClick = { phone.openAiApp() }, modifier = Modifier.testTag("ai-open")) { Text("Open Checklist AI") }
            }
            TextButton(onClick = vm::refreshAi) { Text("Check again") }
        }
    }
}

/**
 * While the AI works: how long it has been and what to expect, with "Stop waiting", which drops
 * the wait (Checklist AI finishes by itself and its answer is thrown away).
 */
@Composable
fun AiWaiting(onStop: () -> Unit) {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("ai-waiting")) {
        // The moving bar is left out with less motion; the words beside it say the same.
        if (!LocalCalm.current) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AiTrouble.waiting(seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStop, modifier = Modifier.testTag("ai-stop")) { Text("Stop waiting") }
        }
    }
}

/** Sentences to tap as a start for "Describe it". */
internal val describeExamples = listOf("Read 20 pages every weekday at 9pm", "Gym 3 times a week for an hour", "Drink water every day")

/**
 * At the top of a new habit: describe the goal in a sentence and the fields below fill
 * themselves in, to check and save. The app reads the days, times and amounts itself; with the
 * AI helper on and ready, the AI adds a tidy name and an icon. Without the AI helper the card
 * waits behind one line, so the editor stays as short as it was. [onSuggest] takes up the
 * points and skill area the app suggests.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiDescribeCard(vm: MainViewModel, onSuggest: (IdeaSuggestion) -> Unit = {}, onIdea: (GoalIdea) -> Unit) {
    val features by vm.features.collectAsState()
    val aiOn = Feature.AI in features
    val status by vm.aiStatus.collectAsState()
    LaunchedEffect(aiOn) {
        if (aiOn) {
            vm.refreshAi()
            vm.warmAi()
        }
    }
    var open by rememberSaveable { mutableStateOf(false) }
    if (!aiOn && !open) {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("describe-open")) { Text("✨ Describe it in a sentence") }
        return
    }
    val usable = aiOn && status?.usable == true
    var sentence by rememberSaveable { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val thinking = job != null
    var message by remember { mutableStateOf<String?>(null) }
    var understood by remember { mutableStateOf<GoalIdea?>(null) }
    var taken by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("ai-describe")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ Describe it", style = MaterialTheme.typography.titleSmall)
            if (aiOn && !usable) {
                Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!usable) {
                Text(
                    "The app reads the days, the time, how long and how much from your sentence, and leaves the name as you typed it." +
                        if (aiOn) "" else " The AI helper extra adds a tidy name and an icon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = sentence,
                onValueChange = { sentence = it.take(GoalFromSentence.MAX_SENTENCE) },
                placeholder = { Text("Like: read 20 pages every weekday at 9pm") },
                enabled = !thinking,
                modifier = Modifier.fillMaxWidth().testTag("ai-sentence"),
            )
            if (sentence.isBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    describeExamples.forEachIndexed { index, example ->
                        AssistChip(onClick = { sentence = example }, label = { Text(example) }, modifier = Modifier.testTag("ai-example:$index"))
                    }
                }
            }
            Button(
                onClick = {
                    message = null
                    understood = null
                    taken = false
                    job = vm.goalFromSentence(sentence, useAi = usable) { idea, _, error ->
                        job = null
                        if (idea != null) {
                            onIdea(idea)
                            understood = idea
                            message = error?.let { "$it The app read the sentence itself instead." }
                        } else {
                            message = "That didn't come out as a goal. Say what it is first, like \"read 20 pages every day\", or fill in the fields below by hand."
                        }
                    }
                },
                enabled = sentence.isNotBlank() && !thinking,
                modifier = Modifier.testTag("ai-fill"),
            ) { Text("Fill in") }
            if (thinking) {
                AiWaiting(onStop = {
                    job?.cancel()
                    job = null
                    message = "Stopped waiting. Tap \"Fill in\" to try again, or fill in the fields below by hand."
                })
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai-message")) }
            understood?.let { idea ->
                val suggested = remember(idea) { idea.suggestion() }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("ai-understood")) {
                    Text("Here's what I understood", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(idea.describe(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ai-understood-text"))
                    val missing = idea.missing()
                    if (missing.isNotEmpty()) {
                        Text(
                            "Still to set, if you want: " + missing.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("ai-missing"),
                        )
                    }
                    Text(
                        "Suggested: " + suggested.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ai-suggested"),
                    )
                    if (!taken) {
                        FilledTonalButton(
                            onClick = {
                                onSuggest(suggested)
                                taken = true
                            },
                            modifier = Modifier.testTag("ai-use-suggested"),
                        ) { Text("Use ${suggested.points} ${pointsWord(suggested.points)}") }
                    }
                    Text(
                        "It's filled in below. Check it all, then save.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * In a project or a to-do, with the AI helper on: steps suggested from its name, steered by an
 * optional hint, and different each time it is asked. The user ticks the ones to keep, and
 * they go into the steps above, to change or remove like any other. [existing] are the steps
 * already there, which aren't suggested again.
 */
@Composable
fun AiStepsCard(vm: MainViewModel, projectName: String, existing: List<String> = emptyList(), what: String = "project", onSteps: (List<String>) -> Unit) {
    val features by vm.features.collectAsState()
    if (Feature.AI !in features) return
    val status by vm.aiStatus.collectAsState()
    LaunchedEffect(Unit) {
        vm.refreshAi()
        vm.warmAi()
    }
    var job by remember { mutableStateOf<Job?>(null) }
    val thinking = job != null
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var hint by rememberSaveable { mutableStateOf("") }
    // Every step suggested so far, so asking again brings different ones.
    var shown by remember { mutableStateOf<List<String>>(emptyList()) }
    fun ask() {
        message = null
        job = vm.projectSteps(projectName, existing = existing.filter { it.isNotBlank() }, shown = shown, hint = hint) { list, error ->
            job = null
            if (list.isNotEmpty()) {
                suggestions = list
                picked = list.indices.toSet()
                shown = shown + list
            } else {
                message = error?.let { "$it You can add the steps by hand above." }
                    ?: "No steps came out for that. Try a fuller name, like \"plan a birthday party\", or add a hint."
            }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("ai-steps")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ Suggest steps", style = MaterialTheme.typography.titleSmall)
            if (status?.usable != true) {
                Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (suggestions.isEmpty()) {
                Text(
                    "The AI suggests steps from the $what's name. You choose which to keep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                suggestions.forEachIndexed { index, step ->
                    TickBoxRow(
                        checked = index in picked,
                        tag = "ai-step:$index",
                        modifier = Modifier.fillMaxWidth(),
                        onChange = { on -> picked = if (on) picked + index else picked - index },
                    ) { Text(step, style = MaterialTheme.typography.bodyMedium) }
                }
                Row {
                    Button(
                        onClick = {
                            onSteps(suggestions.filterIndexed { i, _ -> i in picked })
                            suggestions = emptyList()
                            message = "Added to the steps above. Change or remove any of them there."
                        },
                        enabled = picked.isNotEmpty() && !thinking,
                        modifier = Modifier.testTag("ai-add-steps"),
                    ) { Text("Add these") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { suggestions = emptyList() }, enabled = !thinking) { Text("Not these") }
                }
            }
            OutlinedTextField(
                value = hint,
                onValueChange = { hint = it.take(ProjectSteps.MAX_HINT) },
                label = { Text("Anything to add? (optional)") },
                placeholder = { Text("Like: it's for 20 people, outdoors") },
                singleLine = true,
                enabled = !thinking,
                modifier = Modifier.fillMaxWidth().testTag("ai-steps-hint"),
            )
            if (suggestions.isEmpty()) {
                Button(onClick = ::ask, enabled = projectName.isNotBlank() && !thinking, modifier = Modifier.testTag("ai-suggest-steps")) {
                    Text(if (shown.isEmpty()) "Suggest steps" else "Suggest different ones")
                }
            } else {
                OutlinedButton(onClick = ::ask, enabled = projectName.isNotBlank() && !thinking, modifier = Modifier.testTag("ai-steps-again")) {
                    Text("Suggest different ones")
                }
            }
            if (thinking) {
                AiWaiting(onStop = {
                    job?.cancel()
                    job = null
                    message = "Stopped waiting. You can add the steps by hand above."
                })
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai-steps-message")) }
        }
    }
}

/**
 * In the review of a week: a note on the week. The app writes a short one itself from the
 * week's numbers, with one suggestion it picked from the user's record and a button that opens
 * that goal's editor filled in, to check and save. With the AI helper on and ready, "Write a
 * note" has the AI put the same facts into a few sentences; that note is kept for its week
 * (in this app only), so it is still here when the screen is opened again. A note written with
 * the reasons for misses stays hidden while they are locked.
 */
@Composable
fun AiCoachCard(vm: MainViewModel, summary: ReviewSummary, hideReasons: Boolean, onAdvice: (AreaIdea) -> Unit = {}) {
    val features by vm.features.collectAsState()
    val aiOn = Feature.AI in features
    val status by vm.aiStatus.collectAsState()
    val notes by vm.coachNotes.collectAsState()
    // What else the app knows: the week before, the streak, the target, new badges.
    val lastWeek by vm.lastWeek.collectAsState()
    val thisWeek by vm.thisWeek.collectAsState()
    val target by vm.weekProgress.collectAsState()
    val badges by vm.badges.collectAsState()
    val check by vm.balanceCheck.collectAsState()
    LaunchedEffect(aiOn) {
        if (aiOn) {
            vm.refreshAi()
            vm.warmAi()
        }
    }
    val more = remember(summary, lastWeek, thisWeek, target, badges, check, features) { vm.weekMore(summary) }
    val plain = remember(summary, more) { CoachNote.plain(summary, more) }
    // A week with nothing due has nothing to note: the card only stays for the AI helper's status.
    if (plain == null && !aiOn) return
    // A goal made private after a note was written isn't named by it while private goals are locked.
    val settings by vm.settings.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val goals by vm.currentGoals.collectAsState()
    val hiddenNames = if (settings.locks.privateGoals && !unlocked) goals.filter { it.private }.map { it.name } else emptyList()
    val kept = CoachNotes.shown(notes, summary.from, hideReasons, hiddenNames)
    val lockedNote = kept == null && notes.any { it.week == summary.from }
    var job by remember { mutableStateOf<Job?>(null) }
    val thinking = job != null
    var message by remember(summary.from) { mutableStateOf<String?>(null) }
    val usable = aiOn && status?.usable == true
    Card(Modifier.fillMaxWidth().testTag("ai-coach")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (aiOn) "✨ A note on your week" else "A note on your week", style = MaterialTheme.typography.titleSmall)
            Text(
                kept?.text ?: plain ?: "Nothing was due this week, so there's nothing to note yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("ai-coach-note"),
            )
            more.advice?.let { advice ->
                if (kept == null) Text(advice.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ai-coach-advice-text"))
                val idea = advice.idea
                val button = advice.button
                if (button != null && idea != null) {
                    FilledTonalButton(onClick = { onAdvice(idea) }, modifier = Modifier.testTag("ai-coach-advice")) { Text(button) }
                }
            }
            if (lockedNote) {
                Text(
                    "The note written for this week holds something that is locked now, so the app's own note shows instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (aiOn && !usable) {
                Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (usable) {
                Button(
                    onClick = {
                        message = null
                        job = vm.coachNote(summary, hideReasons, more) { text, error ->
                            job = null
                            if (text == null) message = (error ?: "The AI's note didn't come out right this time.") + " The app's own note is above."
                        }
                    },
                    enabled = !thinking && plain != null,
                    modifier = Modifier.testTag("ai-coach-write"),
                ) { Text(if (kept == null) "Write a note" else "Another note") }
            }
            if (thinking) {
                AiWaiting(onStop = {
                    job?.cancel()
                    job = null
                    message = "Stopped waiting. The app's own note is above."
                })
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai-coach-message")) }
            Text(
                if (kept != null) {
                    "Written by the AI on this phone, from your own numbers, and kept here. It can get things wrong. The suggestion is picked by the app."
                } else {
                    "Written by the app from this week's numbers."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What was heard by voice, or typed, to confirm before anything happens. When no goal was
 * recognised, today's open goals show as buttons, and [onType] opens the box to type instead.
 */
@Composable
fun VoiceDialog(command: VoiceCommand, onTick: (Long, Boolean) -> Unit, onAdd: (String) -> Unit, onDismiss: () -> Unit, onType: (() -> Unit)? = null) {
    when (command) {
        is VoiceCommand.Tick -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tick it?") },
            text = { Text("“${command.name}”, as done" + if (command.todo) "." else " today.") },
            confirmButton = { TextButton(onClick = { onTick(command.goalId, command.todo); onDismiss() }, modifier = Modifier.testTag("voice-tick")) { Text("Tick") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is VoiceCommand.Add -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add a to-do?") },
            text = { Text("“${command.todo}”") },
            confirmButton = { TextButton(onClick = { onAdd(command.todo); onDismiss() }, modifier = Modifier.testTag("voice-add")) { Text("Add") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        // A question is answered on the Ask page, which the caller opens.
        is VoiceCommand.Question -> Unit
        is VoiceCommand.NotUnderstood -> {
            // Picked from the list: asked once more before it is ticked.
            var picked by remember(command) { mutableStateOf<Pair<Long, String>?>(null) }
            val choice = picked
            if (choice != null) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text("Tick it?") },
                    text = { Text("“${choice.second}”, as done.") },
                    confirmButton = { TextButton(onClick = { onTick(choice.first, false); onDismiss() }, modifier = Modifier.testTag("voice-tick")) { Text("Tick") } },
                    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text("Didn't catch that") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (command.closest.isNotEmpty()) {
                                    // Said or typed: the words as they came, and the goals still open.
                                    "“${command.heard}”: which one did you mean?"
                                } else {
                                    (if (command.heard.isNotBlank()) "Heard: “${command.heard}”. " else "") +
                                        "Say “done” and a goal due today, like “done reading”, “add” and a to-do, like “add buy milk”, or ask “what's next”."
                                },
                            )
                            command.closest.forEach { (id, name) ->
                                OutlinedButton(onClick = { picked = id to name }, modifier = Modifier.fillMaxWidth().testTag("voice-pick:$id")) { Text(name) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
                    dismissButton = onType?.let { type -> { TextButton(onClick = { onDismiss(); type() }, modifier = Modifier.testTag("voice-type")) { Text("Type instead") } } },
                )
            }
        }
    }
}
