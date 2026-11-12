@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import org.jetbrains.compose.resources.decodeToImageBitmap

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import io.github.randomtools3377.checklist.core.SavingFor
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

const val GOAL_ICON = "⭐"
const val REWARD_ICON = "🎁"

/** For Today's cards: days without for goals to quit, challenge progress, "I slipped" and "Take back the skip". */
class TodayExtras(
    val quitDays: Map<Long, Int> = emptyMap(),
    val challenges: Map<Long, io.github.randomtools3377.checklist.core.ChallengeProgress> = emptyMap(),
    val onSlip: (Long) -> Unit = {},
    val onTakeBackSkip: (Long) -> Unit = {},
    /** Opens a goal's own page, from "History" on its card; null where there is no page to open. */
    val onHistory: ((Long) -> Unit)? = null,
    /** The goals that are late with nudges still to come today, and "No more nudges today" for one. */
    val nudging: Set<Long> = emptySet(),
    val onStopNudges: (Long) -> Unit = {},
)

val LocalTodayExtras = androidx.compose.runtime.compositionLocalOf { TodayExtras() }

/** The goal boosted this week, which pays 50% more (see Boost in the shared code). */
val LocalBoosted = androidx.compose.runtime.compositionLocalOf<Long?> { null }

/** Today's streak bonus, as a percentage of what goals pay (see StreakBonus in the shared code). */
val LocalStreakPercent = androidx.compose.runtime.compositionLocalOf { 100 }

/** What [points] of [goalId] pay now, with today's difficulty, the boost and the streak bonus. */
@androidx.compose.runtime.Composable
fun payNow(points: Int, goalId: Long): Int =
    io.github.randomtools3377.checklist.core.payFor(points, LocalPayRate.current, LocalBoosted.current == goalId, LocalStreakPercent.current)

/** Today's difficulty, which sets what goals pay; screens show points at this rate. */
val LocalPayRate = androidx.compose.runtime.compositionLocalOf { io.github.randomtools3377.checklist.core.Difficulty.NORMAL }

/** What points are called (Settings → Appearance). Reading it in a screen redraws the screen when it changes. */
object PointsLabel {
    var name by androidx.compose.runtime.mutableStateOf(io.github.randomtools3377.checklist.core.PointsName.DEFAULT)
}

/** "126 points", or "126 coins 🪙" with points renamed. */
fun pointsText(points: Int): String = PointsLabel.name.amount(points)

/** "points", or what the user calls them. */
fun pointsWord(n: Int = 2): String = PointsLabel.name.word(n)

/** "Points", or what the user calls them, for headings. */
fun pointsTitle(): String = PointsLabel.name.title

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
fun BalanceCard(
    balance: Int,
    modifier: Modifier = Modifier,
    note: String? = null,
    /** The reward the user starred to save for, shown under the balance; [onShop] opens the shop. */
    saving: SavingFor? = null,
    onShop: () -> Unit = {},
) {
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
            if (saving != null) SavingForPart(saving, onShop)
        }
    }
}

/**
 * The starred reward on Today's balance card: its icon and name, a bar of the balance against
 * its price, and how many more days it takes at the user's usual pace, or "You can buy it now"
 * with a button to the shop.
 */
@Composable
private fun SavingForPart(saving: SavingFor, onShop: () -> Unit) {
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("saving-for")) {
        Text("★ Saving for ${saving.reward.icon} ${saving.reward.name}", style = MaterialTheme.typography.titleSmall, color = on, modifier = Modifier.testTag("saving-for-name"))
        LinearProgressIndicator(
            progress = { saving.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            trackColor = MaterialTheme.colorScheme.surface,
        )
        if (saving.canBuy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("You can buy it now", style = MaterialTheme.typography.bodyMedium, color = on, modifier = Modifier.weight(1f).testTag("saving-for-line"))
                FilledTonalButton(onClick = onShop, modifier = Modifier.testTag("saving-for-shop")) { Text("Go to the shop") }
            }
        } else {
            Text(
                saving.count(PointsLabel.name) + (saving.pace?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = on,
                modifier = Modifier.testTag("saving-for-line"),
            )
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

/**
 * A row showing a time, or [emptyText] when there is none. Tapping it opens a clock to pick one,
 * at the time set, or at [pickFrom] while there is none.
 */
@Composable
fun TimeSetting(
    label: String,
    time: LocalTime?,
    onChange: (LocalTime?) -> Unit,
    tag: String,
    emptyText: String = "Not set",
    hint: String? = null,
    pickFrom: LocalTime = LocalTime(18, 0),
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
        val start = time ?: pickFrom
        val state = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = is24HourClock(),
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
                    state.selectedDateMillis?.let { onChange(it.floorDiv(86_400_000L).toDay()) }
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

/** A photo's bytes as an image to draw. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap = bytes.decodeToImageBitmap()
