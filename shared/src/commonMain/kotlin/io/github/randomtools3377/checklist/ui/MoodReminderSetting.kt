@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.randomtools3377.checklist.core.MoodReminder
import kotlinx.datetime.LocalTime

/** What "Mood reminder" says under its time in Settings. */
internal fun moodReminderHint(time: LocalTime?): String =
    if (time == null) {
        "One notification a day that only asks \"${MoodReminder.TITLE}\". It's off until you pick a time."
    } else {
        "At this time, one notification asks \"${MoodReminder.TITLE}\". " +
            "It never shows a mood or a note, and nothing comes once that day's mood is in."
    }

/**
 * "Mood reminder" in Settings → Notifications: off until a time is picked. The clock opens at
 * 9 am, or at the time set.
 */
@Composable
internal fun MoodReminderSetting(time: LocalTime?, onChange: (LocalTime?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Mood reminder", style = MaterialTheme.typography.bodyLarge)
                Text(time?.let { timeText(it) } ?: "Off", color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("mood-reminder-time"))
            }
            TextButton(onClick = { open = true }, modifier = Modifier.testTag("setting:mood-reminder")) { Text(if (time == null) "Set" else "Change") }
            if (time != null) TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("setting:mood-reminder:off")) { Text("Turn off") }
        }
        Text(
            moodReminderHint(time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("mood-reminder-hint"),
        )
    }
    if (open) {
        val start = time ?: MoodReminder.SUGGESTED
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = is24HourClock())
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = { onChange(LocalTime(state.hour, state.minute)); open = false },
                    modifier = Modifier.testTag("setting:mood-reminder:ok"),
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
