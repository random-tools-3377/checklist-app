package io.github.randomtools3377.checklist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.reminders.FocusState
import kotlinx.coroutines.delay

/** "12:05" for minutes and seconds left. */
private fun clockText(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * The focus timer, filling the screen: a countdown for a goal with a length. When it runs out
 * the goal is ticked. It can be paused, finished early, or stopped without ticking.
 */
@Composable
fun FocusScreen(state: FocusState, goal: Goal?, onPause: () -> Unit, onResume: () -> Unit, onDone: () -> Unit, onStop: () -> Unit) {
    BackHandler(onBack = onStop)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            if (state.running && state.left(now) <= 0) {
                onDone()
                break
            }
            delay(250)
        }
    }
    val left = state.left(now)
    val progress = if (state.totalMillis <= 0) 0f else (1f - left.toFloat() / state.totalMillis).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp).testTag("focus"),
    ) {
        Text(goal?.icon ?: "⏱", fontSize = 56.sp)
        Text(
            goal?.name ?: "Focus",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, strokeWidth = 10.dp, modifier = Modifier.size(240.dp))
            Text(clockText(left), fontSize = 56.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("focus-time"))
        }
        Spacer(Modifier.height(8.dp))
        Text(if (state.running) "Ticked for you when time's up." else "Paused", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.running) {
                FilledTonalButton(onClick = onPause, modifier = Modifier.testTag("focus-pause")) { Text("Pause") }
            } else {
                FilledTonalButton(onClick = onResume, modifier = Modifier.testTag("focus-resume")) { Text("Carry on") }
            }
            Button(onClick = onDone, modifier = Modifier.testTag("focus-done")) { Text("Done now") }
        }
        TextButton(onClick = onStop, modifier = Modifier.padding(top = 8.dp).testTag("focus-stop")) { Text("Stop without ticking") }
    }
}
