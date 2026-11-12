@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.data.EXAMPLE_GOALS
import io.github.randomtools3377.checklist.data.EXAMPLE_REWARDS
import io.github.randomtools3377.checklist.data.EXAMPLE_TODOS

/** One page of the guide. */
private class GuidePage(val emoji: String, val title: String, val text: () -> String)

private val pages = listOf(
    GuidePage("👋", "Welcome") {
        "Set goals, tick them off and earn ${pointsWord()}. Then spend them in a shop of real-life rewards that you stock and price yourself."
    },
    GuidePage("🎯", "Three kinds of goal") {
        "Habits repeat: every day, on chosen days, 3 times a week and more. Projects are done once, step by step. " +
            "To-dos are quick one-off tasks, with a date or without."
    },
    GuidePage("✅", "Tick to earn") {
        "Tap Done on Today to earn a goal's ${pointsWord()}. Tapped by mistake? Tap it again to take it back. " +
            "Today can also show a good order to do things in, and how fast to go to finish on time."
    },
    GuidePage("🎁", "The shop") {
        "Add rewards with your own prices, like a coffee out or a film night, and buy one when you have the ${pointsWord()}. " +
            "It waits with your bought rewards until you use it, and can be returned until then."
    },
    GuidePage("🙈", "Skips and misses") {
        "Each goal can be skipped or missed once a week for free, or as often as you set. Past that, it costs " +
            "${penaltyShare()} its ${pointsWord()} and a penalty task, done right then. A miss always asks why."
    },
    GuidePage("🧩", "Extras when you want them") {
        "To-dos, a mood check-in, a budget, a game and more start off, to keep things simple. " +
            "Turn on any of them in Settings → Features."
    },
    GuidePage("🔒", "It stays on your phone") {
        "No internet and no account. Back up to a file from Settings, and put private things behind your phone's own lock " +
            "in Settings → Privacy."
    },
)

/** How much of a goal's points going over the limit costs, as set in Points and rules: "half", or "30% of". */
internal fun penaltyShare(percent: Int = Rules.current.penaltyPercent): String = if (percent == 50) "half" else "$percent% of"

/** What adding the examples does, said before adding them to someone who has goals already. */
internal fun examplesText(): String =
    "This adds $EXAMPLE_GOALS example goals, ${if (EXAMPLE_TODOS == 1) "a to-do" else "$EXAMPLE_TODOS to-dos"} and $EXAMPLE_REWARDS rewards to your lists."

/**
 * A short guide to the app, a page at a time, ending with the choice of example goals or none.
 * Someone who [hasGoals] already is asked before the examples are added, and ends with "Done".
 */
@Composable
fun GuideScreen(hasGoals: Boolean, onExamples: () -> Unit, onScratch: () -> Unit, onBack: () -> Unit) {
    var index by rememberSaveable { mutableStateOf(0) }
    var asking by rememberSaveable { mutableStateOf(false) }
    val page = pages[index]
    val last = index == pages.lastIndex
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Add the examples?") },
            text = { Text(examplesText() + " Change or remove them any time.") },
            confirmButton = {
                TextButton(onClick = { asking = false; onExamples() }, modifier = Modifier.testTag("guide-examples-ok")) { Text("Add them") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How it works") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("guide"),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(page.emoji, fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.testTag("guide-title"))
            Spacer(Modifier.height(12.dp))
            Text(page.text(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (last) {
                Button(
                    onClick = { if (hasGoals) asking = true else onExamples() },
                    modifier = Modifier.fillMaxWidth().testTag("guide-examples"),
                ) { Text("Add some examples to try") }
                Text(
                    "A few habits, a project, a to-do and two rewards. Change or remove them any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                OutlinedButton(onClick = if (hasGoals) onBack else onScratch, modifier = Modifier.fillMaxWidth().testTag("guide-scratch")) {
                    Text(if (hasGoals) "Done" else "Start from scratch")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (index > 0) TextButton(onClick = { index-- }, modifier = Modifier.testTag("guide-back")) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (!last) Button(onClick = { index++ }, modifier = Modifier.testTag("guide-next")) { Text("Next") }
            }
        }
    }
}
