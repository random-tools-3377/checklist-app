@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

const val GOAL_ICON = "⭐"
const val REWARD_ICON = "🎁"

fun pointsText(points: Int): String = if (points == 1 || points == -1) "$points point" else "$points points"

private val ShortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val LongDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

private val ShortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun shortDate(date: LocalDate): String = date.toJavaLocalDate().format(ShortDate)
fun longDate(date: LocalDate): String = date.toJavaLocalDate().format(LongDate)

/** "6:00 PM" or "18:00", as the phone is set. */
fun timeText(time: LocalTime): String = time.toJavaLocalTime().format(ShortTime)

/** A time on [today], with the day added when it falls on another one. */
fun momentText(moment: LocalDateTime, today: LocalDate): String =
    if (moment.date == today) timeText(moment.time) else "${shortDate(moment.date)} ${timeText(moment.time)}"

/** "45 min", "2 h", "1 h 20 min". */
fun minutesText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** The balance, big. It turns red below zero. */
@Composable
fun BalanceCard(balance: Int, modifier: Modifier = Modifier, note: String? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = pointsText(balance),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("balance"),
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** A text field for a whole number. Keeps only digits. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
) {
    val support = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (support != null) ({ Text(support) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** Shows the chosen emoji. Tapping it opens the full emoji set to pick another. */
@Composable
fun IconPicker(icon: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { open = true }
                .testTag("icon"),
        ) {
            Text(icon, fontSize = 34.sp)
        }
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = { open = true }) { Text("Choose icon") }
    }
    if (open) {
        EmojiPickerDialog(
            current = icon,
            onPick = {
                onPick(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** A short line of grey text under a heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
    )
}

/** A row showing a time, or [emptyText] when there is none. Tapping it opens a clock to pick one. */
@Composable
fun TimeSetting(
    label: String,
    time: LocalTime?,
    onChange: (LocalTime?) -> Unit,
    tag: String,
    emptyText: String = "Not set",
    hint: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(time?.let { timeText(it) } ?: emptyText, color = MaterialTheme.colorScheme.primary)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) { Text(if (time == null) "Set" else "Change") }
        if (time != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (open) {
        val start = time ?: LocalTime(18, 0)
        val state = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(LocalTime(state.hour, state.minute)); open = false }, modifier = Modifier.testTag("$tag:ok")) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

/** A row showing a date, or [emptyText]. Tapping it opens a calendar to pick one. */
@Composable
fun DateSetting(
    label: String,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    tag: String,
    emptyText: String = "Not set",
    canClear: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(date?.let { longDate(it) } ?: emptyText, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) { Text(if (date == null) "Set" else "Change") }
        if (date != null && canClear) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: nowLocal().date).toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Math.floorDiv(it, 86_400_000L).toDay()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** A goal's own colour as a strip down the left of its card, inside the card's rounded corners. */
@Composable
fun Modifier.goalStrip(color: Int?): Modifier {
    if (color == null) return this
    val strip = androidx.compose.ui.graphics.Color(io.github.randomtools3377.checklist.core.Schemes.goalStrip(color, LocalBrightness.current))
    return this.drawBehind { drawRect(strip, size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height)) }
}
