@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.TodayView
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/** How tall an hour is on the timeline. */
private val HOUR = 64.dp

/**
 * A day as a timeline of hours: goals with a clock time sit at it, the rest wait above under
 * "Any time". Hold a goal and drag it to another time, or tap it to pick one; either changes its
 * time for this day only. Times snap to quarter hours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(view: TodayView?, title: String, firstHour: Int, onRetime: (goalId: Long, minute: Int?) -> Unit, onBack: () -> Unit) {
    var picking by remember { mutableStateOf<HabitRow?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (view == null) return@Scaffold
        val rows = view.dueToday
        val timed = rows.filter { it.goal.at != null }
        val anyTime = rows.filter { it.goal.at == null }
        val earliest = timed.minOfOrNull { it.goal.at!!.hour } ?: firstHour
        val startHour = minOf(firstHour, earliest).coerceIn(0, 23)
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
            if (anyTime.isNotEmpty()) {
                Text("Any time", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Tap one to give it a time today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    anyTime.forEach { row ->
                        AssistChip(
                            onClick = { picking = row },
                            label = { Text("${row.goal.icon} ${row.goal.name}" + if (row.done) " ✓" else "") },
                            modifier = Modifier.testTag("anytime:${row.goal.name}"),
                        )
                    }
                }
                HorizontalDivider()
            }
            Text(
                "Hold a goal and drag it to another time, or tap it. It changes this day only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Hours(startHour, timed, onTap = { picking = it }, onDrop = { row, minute -> onRetime(row.goal.id, minute) })
        }
    }
    picking?.let { row ->
        RetimeDialog(row, onPick = { minute -> onRetime(row.goal.id, minute); picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun Hours(startHour: Int, timed: List<HabitRow>, onTap: (HabitRow) -> Unit, onDrop: (HabitRow, Int) -> Unit) {
    val hours = 24 - startHour
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR.toPx() }
    Box(Modifier.fillMaxWidth().height(HOUR * hours)) {
        // The hour lines and labels.
        for (i in 0 until hours) {
            Row(Modifier.offset(y = HOUR * i).fillMaxWidth()) {
                Text(
                    timeText(LocalTime(startHour + i, 0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
        // Goals at their time; ones at the same time sit side by side.
        val placed = timed.sortedBy { it.goal.at }.groupBy { it.goal.at!!.hour * 60 + it.goal.at!!.minute }
        placed.forEach { (minute, sameTime) ->
            sameTime.forEachIndexed { index, row ->
                key(row.goal.id) {
                    var dragY by remember { mutableFloatStateOf(0f) }
                    val baseY = (minute - startHour * 60) / 60f * hourPx
                    Box(
                        Modifier
                            .offset { IntOffset(with(density) { (64 + index * 110).dp.roundToPx() }, (baseY + dragY).roundToInt()) }
                            .width(150.dp)
                            .background(
                                if (row.done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp),
                            )
                            .pointerInput(row.goal.id) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragY += delta.y
                                    },
                                    onDragEnd = {
                                        val moved = minute + (dragY / hourPx * 60).roundToInt()
                                        val snapped = ((moved / 15f).roundToInt() * 15).coerceIn(startHour * 60, 24 * 60 - 15)
                                        dragY = 0f
                                        if (snapped != minute) onDrop(row, snapped)
                                    },
                                    onDragCancel = { dragY = 0f },
                                )
                            }
                            .clickable { onTap(row) }
                            .semantics { contentDescription = "${row.goal.name} at ${timeText(row.goal.at!!)}" }
                            .testTag("timeline-goal:${row.goal.name}")
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Column {
                            Text(
                                "${row.goal.icon} ${row.goal.name}" + if (row.done) " ✓" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val extra = (dragY / hourPx * 60).roundToInt()
                            Text(
                                timeText(LocalTime(((minute + extra).coerceIn(0, 24 * 60 - 1)) / 60, ((minute + extra).coerceIn(0, 24 * 60 - 1)) % 60)),
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
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
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
