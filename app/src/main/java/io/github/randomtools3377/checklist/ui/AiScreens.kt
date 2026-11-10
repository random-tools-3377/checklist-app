package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.randomtools3377.checklist.BuildConfig
import io.github.randomtools3377.checklist.core.AiFile
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.GoalFromSentence
import io.github.randomtools3377.checklist.core.GoalIdea
import io.github.randomtools3377.checklist.data.AiClient
import io.github.randomtools3377.checklist.data.AiStatus

/** One line on how the AI stands, for the Features page and the goal editor. */
fun AiStatus?.describe(): String = when {
    this == null -> "Checking Checklist AI…"
    !installed -> "Checklist AI isn't installed. It's a separate app, on the same Releases page as this one: " +
        "checklist-ai-${BuildConfig.VERSION_NAME}.apk. Keeping the AI separate keeps this app small for anyone who doesn't use it."
    problem != null -> "Checklist AI didn't answer ($problem). Try opening it once."
    !ready -> "Checklist AI is installed. Open it to download and add its AI file (${AiFile.sizeText}, once)."
    outOfStep -> "Ready, but Checklist AI is version $version and this app is ${BuildConfig.VERSION_NAME}. " +
        "Install both from the same Release so they stay in step."
    else -> "Ready: Checklist AI $version, with its AI file."
}

/** Under the AI helper's switch: how it stands, and the way to the next step. */
@Composable
fun AiStatusPanel(vm: MainViewModel) {
    val status by vm.aiStatus.collectAsState()
    val context = LocalContext.current
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
                TextButton(onClick = { AiClient.openReleases(context) }, modifier = Modifier.testTag("ai-get")) { Text("Get Checklist AI") }
            } else if (status != null) {
                TextButton(onClick = { AiClient.open(context) }, modifier = Modifier.testTag("ai-open")) { Text("Open Checklist AI") }
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
