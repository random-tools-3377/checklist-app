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
 * At the top of a new habit, with the AI helper on: describe the goal in a sentence and the AI
 * fills in the fields below, for the user to check and save.
 */
@Composable
fun AiDescribeCard(vm: MainViewModel, onIdea: (GoalIdea) -> Unit) {
    val features by vm.features.collectAsState()
    if (Feature.AI !in features) return
    val status by vm.aiStatus.collectAsState()
    LaunchedEffect(Unit) { vm.refreshAi() }
    var sentence by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("ai-describe")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ Describe it", style = MaterialTheme.typography.titleSmall)
            if (status?.usable != true) {
                Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            OutlinedTextField(
                value = sentence,
                onValueChange = { sentence = it.take(GoalFromSentence.MAX_SENTENCE) },
                placeholder = { Text("Like: read 20 pages every weekday at 9pm") },
                enabled = !thinking,
                modifier = Modifier.fillMaxWidth().testTag("ai-sentence"),
            )
            Button(
                onClick = {
                    thinking = true
                    message = null
                    vm.goalFromSentence(sentence) { idea, error ->
                        thinking = false
                        if (idea != null) {
                            onIdea(idea)
                            message = "Filled in below. Check it all, set the ${pointsWord()}, and save."
                        } else {
                            message = error ?: "The AI couldn't make a goal of that. Try saying it another way."
                        }
                    }
                },
                enabled = sentence.isNotBlank() && !thinking,
                modifier = Modifier.testTag("ai-fill"),
            ) { Text("Fill in") }
            if (thinking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Thinking, on this phone. The first time takes longer while the AI loads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai-message")) }
        }
    }
}

/**
 * In a project, with the AI helper on: steps suggested from the project's name. The user ticks
 * the ones to keep, and they go into the steps above, to change or remove like any other.
 */
@Composable
fun AiStepsCard(vm: MainViewModel, projectName: String, onSteps: (List<String>) -> Unit) {
    val features by vm.features.collectAsState()
    if (Feature.AI !in features) return
    val status by vm.aiStatus.collectAsState()
    LaunchedEffect(Unit) { vm.refreshAi() }
    var thinking by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var message by remember { mutableStateOf<String?>(null) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("ai-steps")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ Suggest steps", style = MaterialTheme.typography.titleSmall)
            if (status?.usable != true) {
                Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (suggestions.isEmpty()) {
                Text(
                    "The AI suggests steps from the project's name. You choose which to keep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        thinking = true
                        message = null
                        vm.projectSteps(projectName) { list, error ->
                            thinking = false
                            if (list.isNotEmpty()) {
                                suggestions = list
                                picked = list.indices.toSet()
                            } else {
                                message = error ?: "The AI couldn't suggest steps for that. Try a fuller name, like \"plan a birthday party\"."
                            }
                        }
                    },
                    enabled = projectName.isNotBlank() && !thinking,
                    modifier = Modifier.testTag("ai-suggest-steps"),
                ) { Text("Suggest steps") }
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
                        enabled = picked.isNotEmpty(),
                        modifier = Modifier.testTag("ai-add-steps"),
                    ) { Text("Add these") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { suggestions = emptyList() }) { Text("Not these") }
                }
            }
            if (thinking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Thinking, on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/**
 * In the review of a week, with the AI helper on: a few sentences about the week, written on the
 * phone from its own numbers, with one small change to try.
 */
@Composable
fun AiCoachCard(vm: MainViewModel, summary: ReviewSummary, hideReasons: Boolean) {
    val features by vm.features.collectAsState()
    if (Feature.AI !in features) return
    val status by vm.aiStatus.collectAsState()
    LaunchedEffect(Unit) { vm.refreshAi() }
    var note by remember(summary.from) { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var message by remember(summary.from) { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth().testTag("ai-coach")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ A note on your week", style = MaterialTheme.typography.titleSmall)
            if (status?.usable != true) {
                Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ai-coach-note")) }
            Button(
                onClick = {
                    thinking = true
                    message = null
                    vm.coachNote(summary, hideReasons) { text, error ->
                        thinking = false
                        if (text != null) note = text else message = error ?: "The AI couldn't write a note this time. Try again."
                    }
                },
                enabled = !thinking && summary.goals.any { it.due > 0 },
                modifier = Modifier.testTag("ai-coach-write"),
            ) { Text(if (note == null) "Write a note" else "Another note") }
            if (thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Written by the AI on this phone, from this week's numbers only. It can get things wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What was heard by voice, to confirm before anything happens. */
@Composable
fun VoiceDialog(command: VoiceCommand, onTick: (Long) -> Unit, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    when (command) {
        is VoiceCommand.Tick -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tick it?") },
            text = { Text("\u201c${command.name}\u201d, as done today.") },
            confirmButton = { TextButton(onClick = { onTick(command.goalId); onDismiss() }, modifier = Modifier.testTag("voice-tick")) { Text("Tick") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is VoiceCommand.Add -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add a to-do?") },
            text = { Text("\u201c${command.todo}\u201d") },
            confirmButton = { TextButton(onClick = { onAdd(command.todo); onDismiss() }, modifier = Modifier.testTag("voice-add")) { Text("Add") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is VoiceCommand.NotUnderstood -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Didn't catch that") },
            text = {
                Text(
                    (if (command.heard.isNotBlank()) "Heard: \u201c${command.heard}\u201d. " else "") +
                        "Say \u201cdone\u201d and a goal due today, like \u201cdone reading\u201d, or \u201cadd\u201d and a to-do, like \u201cadd buy milk\u201d.",
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
