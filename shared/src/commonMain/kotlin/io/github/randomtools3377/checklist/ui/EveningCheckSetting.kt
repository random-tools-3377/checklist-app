@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.EveningCheck
import kotlinx.datetime.LocalTime

/** When the user's day ends, in a sentence: "midnight" or "4 am". */
internal fun dayEndWords(dayEndHour: Int): String = hourText(dayEndHour).lowercase()

/** What "Evening check" says under its time in Settings. */
internal fun eveningCheckHint(time: LocalTime?, dayEndHour: Int): String = when {
    time == null ->
        "One notification in the evening lists what is still open today, so you can do it before it counts as a miss. It's off until you pick a time."
    !EveningCheck.allowed(time, dayEndHour) ->
        "Your day now ends at ${dayEndWords(dayEndHour)}, before this time, so it doesn't come. Pick an earlier time."
    else -> "At this time, one notification lists what is still open today. Nothing comes on a day when everything is done."
}

/** What the clock says above it: the time must be before the day ends. */
internal fun eveningCheckRule(dayEndHour: Int): String =
    "Pick a time from noon on, before your day ends at ${dayEndWords(dayEndHour)}."

/** What the clock says under it while the time picked can't be taken. */
internal fun eveningCheckNotAllowed(picked: LocalTime, dayEndHour: Int): String =
    "${timeText(picked)} isn't between noon and ${dayEndWords(dayEndHour)}, when your day ends."

/**
 * "Evening check" in Settings: off until a time is picked. The clock opens at 9 pm, says the
 * time must be before the day ends, and only takes one that is.
 */
@Composable
internal fun EveningCheckSetting(time: LocalTime?, dayEndHour: Int, onChange: (LocalTime?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val comes = time == null || EveningCheck.allowed(time, dayEndHour)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Evening check", style = MaterialTheme.typography.bodyLarge)
                Text(
                    time?.let { timeText(it) } ?: "Off",
                    color = if (comes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("evening-check-time"),
                )
            }
            TextButton(onClick = { open = true }, modifier = Modifier.testTag("setting:evening-check")) { Text(if (time == null) "Set" else "Change") }
            if (time != null) TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("setting:evening-check:off")) { Text("Turn off") }
        }
        // Under the row, across its whole width, so the two buttons don't squeeze it.
        Text(
            eveningCheckHint(time, dayEndHour),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("evening-check-hint"),
        )
    }
    if (open) {
        // At the time set, or 9 pm; a time set before the day's end was moved starts at 9 pm again.
        val start = time?.takeIf { EveningCheck.allowed(it, dayEndHour) } ?: EveningCheck.SUGGESTED
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = is24HourClock())
        val picked = LocalTime(state.hour, state.minute)
        val allowed = EveningCheck.allowed(picked, dayEndHour)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = { onChange(picked); open = false },
                    enabled = allowed,
                    modifier = Modifier.testTag("setting:evening-check:ok"),
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(eveningCheckRule(dayEndHour), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().testTag("evening-check-rule"))
                    TimePicker(state = state)
                    if (!allowed) {
                        Text(
                            eveningCheckNotAllowed(picked, dayEndHour),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().testTag("evening-check-not-allowed"),
                        )
                    }
                }
            },
        )
    }
}
