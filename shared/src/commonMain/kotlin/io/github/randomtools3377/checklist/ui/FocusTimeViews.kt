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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.FocusTime
import io.github.randomtools3377.checklist.core.FocusTotals
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.reminders.FocusState
import kotlinx.coroutines.delay

/** What the button that starts a break says: "Start a 5 min break". */
internal fun startBreakWords(minutes: Int): String = "Start a $minutes min break"

/** What a choice for "Break after a focus session" is called: "Off" or "5 min". */
internal fun breakChoiceWords(minutes: Int): String = if (minutes <= 0) "Off" else "$minutes min"

/** What "Break after a focus session" says under its choices. */
internal fun breakSettingHint(minutes: Int): String =
    if (minutes <= 0) "Off. Pick a length and the focus timer offers a break each time a session ends."
    else "When a focus session ends, the timer offers a $minutes min break. A break isn't counted as focus time."

/** The top line of the review's "Focus time" card: "1 h 5 min in 3 sessions". */
internal fun focusTotalWords(totals: FocusTotals): String =
    "${minutesText(totals.minutes)} in ${totals.sessions} ${if (totals.sessions == 1) "session" else "sessions"}"

/**
 * The review's "Focus time" card, in the week and month views: the time really spent with the
 * focus timer, all of it and each goal's. Only shown when there is any.
 */
@Composable
internal fun FocusTimeCard(totals: FocusTotals) {
    Card(Modifier.fillMaxWidth().testTag("review-focus")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Focus time", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(focusTotalWords(totals), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("review-focus:total"))
            totals.goals.forEach { each ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${each.goal.icon} ${each.goal.name}", maxLines = 1, modifier = Modifier.weight(1f))
                    Text(minutesText(each.minutes), fontWeight = FontWeight.Medium, modifier = Modifier.testTag("review-focus:${each.goal.name}"))
                }
            }
            Text(
                "Time on the focus timer. Pauses and breaks aren't counted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Break after a focus session" in Settings: off, 5, 10 or 15 minutes. */
@Composable
internal fun FocusBreakSetting(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("Break after a focus session", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusTime.BREAK_CHOICES.forEach { choice ->
                FilterChip(
                    selected = minutes == choice,
                    onClick = { onChange(choice) },
                    label = { Text(breakChoiceWords(choice)) },
                    modifier = Modifier.testTag("focus-break:$choice"),
                )
            }
        }
        Text(breakSettingHint(minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A focus session just ended, and breaks are on: the offer of one, filling the screen. */
@Composable
fun BreakOfferScreen(minutes: Int, onStart: () -> Unit, onSkip: () -> Unit) {
    BackButton(onBack = onSkip)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp).testTag("break-offer"),
    ) {
        // Time's up: with the character on, it cheers here.
        if (LocalCharacter.current != null) {
            SceneCharacter(io.github.randomtools3377.checklist.core.Scene.FOCUS_DONE, if (isShort()) 72.dp else 112.dp)
        } else {
            Text("☕", fontSize = 56.sp)
        }
        Text("Nice work", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("That session is done. Time for a break?", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.testTag("break-start")) { Text(startBreakWords(minutes)) }
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = 8.dp).testTag("break-skip")) { Text("Skip break") }
    }
}

/**
 * The break, filling the screen: a plain countdown that buzzes when it ends. It can't be paused,
 * ticks nothing, and is never counted as focus time.
 */
@Composable
fun BreakScreen(state: FocusState, onDone: () -> Unit, onSkip: () -> Unit) {
    BackButton(onBack = onSkip)
    val haptics = LocalHapticFeedback.current
    val canBuzz = LocalPhone.current.canBuzz
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = nowMillis()
            if (state.left(now) <= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDone()
                break
            }
            delay(250)
        }
    }
    val left = state.left(now)
    val progress = if (state.totalMillis <= 0) 0f else (1f - left.toFloat() / state.totalMillis).coerceIn(0f, 1f)
    // On a break the character has a stretch, while it is on and there is room.
    val buddy = focusBuddy()
    val ring = focusRing(buddy)
    val clock: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, strokeWidth = 10.dp, modifier = Modifier.size(ring))
            Text(focusClockText(left), fontSize = if (ring < 200.dp) 44.sp else 56.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("break-time"))
        }
    }
    val rest: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (buddy) FocusBuddy(io.github.randomtools3377.checklist.core.Scene.BREAK)
            Text("☕ Break", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            // A computer can't buzz: there the notification says so.
            Text(if (canBuzz) "A buzz tells you when it's over." else "You'll be told when it's over.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 8.dp).testTag("break-end")) { Text("Skip break") }
        }
    }
    val screen = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp).testTag("break")
    if (isShort()) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = screen) {
            clock()
            rest()
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = screen) {
            clock()
            Spacer(Modifier.height(24.dp))
            rest()
        }
    }
}
