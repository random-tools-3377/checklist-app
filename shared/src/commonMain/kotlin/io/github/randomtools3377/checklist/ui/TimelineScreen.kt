@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.DayHours
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.Retimes
import io.github.randomtools3377.checklist.core.TodayView
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/** How tall an hour is on the timeline. */
private val HOUR = 64.dp

/**
 * A goal on the timeline at [minute] (see [DayHours.minuteOf]). [number] is which of its times,
 * for a goal done several times a day: it keeps those times, so it can't be moved here.
 */
private class Block(val row: HabitRow, val minute: Int, val number: Int = 0) {
    val movable: Boolean get() = number == 0
    val done: Boolean get() = row.done || (number > 0 && row.count >= number)

    /** Nothing left of it to do, done or skipped: it fades on the timeline. */
    val settled: Boolean get() = done || row.settled

    /** " ✓" once done; a skipped one is never done, and says so. */
    val mark: String get() = if (done) " ✓" else if (row.settled) " · skipped" else ""
}

/**
 * A day as a timeline of hours, to the end of the user's day (past midnight when the day ends
 * later): goals with a clock time sit at it, the rest wait above under "Any time". Hold a goal and
 * drag it to another time, or tap it to pick one; either changes its time for this day only.
 * Times snap to quarter hours. A goal done several times a day sits at each of its times, set or
 * spread over the day, and keeps them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(
    view: TodayView?,
    title: String,
    firstHour: Int,
    onRetime: (goalId: Long, minute: Int?) -> Unit,
    onBack: () -> Unit,
    events: List<io.github.randomtools3377.checklist.core.Busy> = emptyList(),
    pace: PaceSettings = PaceSettings(dayEndHour = 0, defaultFinish = null),
) {
    var picking by remember { mutableStateOf<HabitRow?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        if (view == null) return@Scaffold
        val rows = view.dueToday
        val dayEnd = pace.dayEndHour
        val several = rows.filter { Retimes.keepsTimes(it.goal) }
            .map { row -> row to Pace.slots(row.goal, view.day, pace).map { DayHours.minuteOf(it.at.time, dayEnd) } }
        val blocks = rows.filter { !Retimes.keepsTimes(it.goal) && it.goal.at != null }.map { Block(it, DayHours.minuteOf(it.goal.at!!, dayEnd)) } +
            several.flatMap { (row, minutes) -> minutes.mapIndexed { i, minute -> Block(row, minute, number = i + 1) } }
        val anyTime = rows.filter { !Retimes.keepsTimes(it.goal) && it.goal.at == null }
        // Done several times a day with no times to show (not set, and not spread without a day start and finish).
        val severalAnyTime = several.filter { it.second.isEmpty() }.map { it.first }
        val startHour = DayHours.startHour(firstHour, blocks.map { it.minute }, dayEnd)
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("timeline"),
        ) {
            if (rows.isEmpty()) {
                Text("Nothing due this day.", modifier = Modifier.padding(top = 24.dp))
                return@Column
            }
            if (anyTime.isNotEmpty() || severalAnyTime.isNotEmpty()) {
                Text("Any time", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (anyTime.isNotEmpty()) {
                    Text(
                        "Tap one to give it a time today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    anyTime.forEach { row ->
                        AssistChip(
                            onClick = { picking = row },
                            label = { Text("${row.goal.icon} ${row.goal.name}" + if (row.done) " ✓" else if (row.settled) " · skipped" else "") },
                            modifier = Modifier.testTag("anytime:${row.goal.name}"),
                        )
                    }
                    severalAnyTime.forEach { row ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${row.goal.icon} ${row.goal.name} · ${row.count} of ${row.goal.target}" + if (row.done) " ✓" else if (row.settled) " · skipped" else "") },
                            modifier = Modifier.testTag("anytime:${row.goal.name}"),
                        )
                    }
                }
                HorizontalDivider()
            }
            Text(
                "Hold a goal and drag it to another time, or tap it. It changes this day only." +
                    if (several.isNotEmpty()) " Goals done several times a day keep their times." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            events.filter { it.allDay }.forEach { EventLine(it) }
            Hours(
                view.day, startHour, dayEnd, blocks, events.filter { !it.allDay },
                onTap = { picking = it },
                onDrop = { row, minute -> onRetime(row.goal.id, minute) },
            )
        }
    }
    picking?.let { row ->
        RetimeDialog(row, onPick = { minute -> onRetime(row.goal.id, minute); picking = null }, onDismiss = { picking = null })
    }
}

/** "7:15 am" for [minute] as from [DayHours.minuteOf], past midnight too. */
private fun minuteText(minute: Int): String = (minute.mod(24 * 60)).let { timeText(LocalTime(it / 60, it % 60)) }

@Composable
private fun Hours(
    day: LocalDate,
    startHour: Int,
    dayEnd: Int,
    blocks: List<Block>,
    events: List<io.github.randomtools3377.checklist.core.Busy>,
    onTap: (HabitRow) -> Unit,
    onDrop: (HabitRow, Int) -> Unit,
) {
    val hours = DayHours.endHour(dayEnd) - startHour
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR.toPx() }
    Box(Modifier.fillMaxWidth().height(HOUR * hours)) {
        // The hour lines and labels.
        for (i in 0 until hours) {
            Row(Modifier.offset(y = HOUR * i).fillMaxWidth()) {
                Text(
                    timeText(LocalTime((startHour + i) % 24, 0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
        // Events from the phone's calendar, as grey blocks on the right.
        val midnight = LocalDateTime(day, LocalTime(0, 0))
        events.forEach { event ->
            val from = (Pace.minutesBetween(midnight, event.start).toInt() - startHour * 60).coerceIn(0, hours * 60 - 15)
            val to = (Pace.minutesBetween(midnight, event.end).toInt() - startHour * 60).coerceAtMost(hours * 60).coerceAtLeast(from + 15)
            Box(
                Modifier
                    .offset(x = 220.dp, y = HOUR * (from / 60f))
                    .width(88.dp)
                    .height(HOUR * ((to - from) / 60f))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(4.dp)
                    .testTag("timeline-event:${event.title}"),
            ) {
                Text("📅 ${event.title}", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Goals at their time; ones at the same time sit side by side.
        val placed = blocks.sortedBy { it.minute }.groupBy { it.minute }
        val last = DayHours.endHour(dayEnd) * 60 - 1
        placed.forEach { (minute, sameTime) ->
            sameTime.forEachIndexed { index, block ->
                val row = block.row
                key(row.goal.id, block.number) {
                    var dragY by remember { mutableFloatStateOf(0f) }
                    val baseY = (minute - startHour * 60) / 60f * hourPx
                    val of = if (block.movable) "" else " · ${block.number} of ${row.goal.target}"
                    Box(
                        Modifier
                            .offset { IntOffset(with(density) { (64 + index * 110).dp.roundToPx() }, (baseY + dragY).roundToInt()) }
                            .width(146.dp)
                            .background(
                                if (block.settled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp),
                            )
                            .then(
                                if (!block.movable) {
                                    Modifier
                                } else {
                                    Modifier
                                        .pointerInput(row.goal.id, minute, startHour, dayEnd) {
                                            detectDragGesturesAfterLongPress(
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    dragY += delta.y
                                                },
                                                onDragEnd = {
                                                    val moved = minute + (dragY / hourPx * 60).roundToInt()
                                                    val snapped = DayHours.drop(moved, startHour, dayEnd)
                                                    dragY = 0f
                                                    if (snapped != minute % (24 * 60)) onDrop(row, snapped)
                                                },
                                                onDragCancel = { dragY = 0f },
                                            )
                                        }
                                        .clickable { onTap(row) }
                                },
                            )
                            .semantics { contentDescription = "${row.goal.name}$of at ${minuteText(minute)}" }
                            .testTag(if (block.movable) "timeline-goal:${row.goal.name}" else "timeline-goal:${row.goal.name}:${block.number}")
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Column {
                            Text(
                                "${row.goal.icon} ${row.goal.name}" + block.mark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val extra = (dragY / hourPx * 60).roundToInt()
                            Text(
                                minuteText((minute + extra).coerceIn(0, last)) + of,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Picks a time for this day only, or puts back the usual one. */
@Composable
private fun RetimeDialog(row: HabitRow, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val start = row.goal.at ?: LocalTime(9, 0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = is24HourClock())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${row.goal.icon} ${row.goal.name}, this day") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }, modifier = Modifier.testTag("retime-ok")) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("retime-usual")) { Text("Usual time") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
