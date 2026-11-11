@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.toInstant
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Gap
import io.github.randomtools3377.checklist.data.toLocalDateTime

private fun kindText(kind: EntryKind): String = when (kind) {
    EntryKind.TICK -> "Ticked"
    EntryKind.BONUS -> "Bonus for finishing"
    EntryKind.PURCHASE -> "Bought"
    EntryKind.RETURN -> "Returned"
    EntryKind.PENALTY -> "Over the limit"
    EntryKind.REFUND -> "Penalty given back"
    EntryKind.INTEREST -> "Savings interest"
    EntryKind.PERFECT_DAY -> "Perfect-day bonus"
    EntryKind.CHALLENGE -> "Challenge finished"
    EntryKind.BET -> "Bet on yourself"
}

/** Every change to the balance, newest first, grouped by day. */
@Composable
fun HistoryScreen(
    entries: List<Entry>?,
    gaps: List<Gap>,
    locked: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    notes: Map<Long, String> = emptyMap(),
    vm: MainViewModel? = null,
) {
    var editing by remember { mutableStateOf<Long?>(null) }
    if (vm != null) editing?.let { id -> EditTickDialog(id, vm) { editing = null } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pointsTitle()} history") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (locked) {
            LockedPanel("Your ${pointsWord()} history", onUnlock, Modifier.padding(padding))
            return@Scaffold
        }
        if (entries == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("history"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "No ${pointsWord()} yet. Tick a goal and it shows up here.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            entries.groupBy { it.day }.forEach { (day, onDay) ->
                // A gap is marked where it ends, with what the user wrote at the check-in.
                gaps.filter { it.to == day }.forEach { gap ->
                    item(key = "gap-${gap.id}") { GapMark(gap) }
                }
                item(key = "day-$day") {
                    val total = onDay.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(longDate(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(signed(total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
                items(onDay, key = { "entry-${it.id}" }) { entry ->
                    // A tick can be fixed: when it was done, and a note about it.
                    val tickId = entry.tickId?.takeIf { entry.kind == EntryKind.TICK }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (tickId != null && vm != null) Modifier.clickable { editing = tickId } else Modifier)
                            .padding(vertical = 8.dp)
                            .testTag("entry:${entry.label}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                            tickId?.let { notes[it] }?.let { note ->
                                Text("“$note”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "${kindText(entry.kind)} · ${timeText(entry.at.toLocalDateTime().time)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            signed(entry.amount),
                            fontWeight = FontWeight.Bold,
                            color = if (entry.amount < 0) MaterialTheme.colorScheme.error else goodColor(),
                        )
                    }
                }
            }
        }
    }
}

private fun signed(amount: Int): String = if (amount > 0) "+$amount" else "$amount"

/**
 * Fixes a tick: the time it was really done, on the same day, and a note about it, like
 * "ran 5 km". The points stay as they were.
 */
@Composable
fun EditTickDialog(tickId: Long, vm: MainViewModel, onDone: () -> Unit) {
    val tick by produceState<io.github.randomtools3377.checklist.core.Tick?>(null, tickId) { value = vm.loadTick(tickId) }
    val loaded = tick ?: return
    val start = remember(loaded.id) { loaded.at.toLocalDateTime() }
    var time by remember(loaded.id) { mutableStateOf(start.time) }
    var note by remember(loaded.id) { mutableStateOf(loaded.note ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("This tick") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                TimeSetting("Done at", time, { it?.let { picked -> time = picked } }, "edit-tick-time")
                androidx.compose.material3.OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(io.github.randomtools3377.checklist.core.Book.NOTE_LENGTH) },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Like \"ran 5 km\"") },
                    modifier = Modifier.fillMaxWidth().testTag("edit-tick-note"),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val at = kotlinx.datetime.LocalDateTime(start.date, time)
                        .toInstant(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    vm.editTick(loaded.id, at.takeIf { time != start.time }, note)
                    onDone()
                },
                modifier = Modifier.testTag("edit-tick-save"),
            ) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** A gap, marked in the history. */
@Composable
fun GapMark(gap: Gap) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("gap-mark"),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Gap: " + if (gap.from == gap.to) shortDate(gap.from) else "${shortDate(gap.from)} to ${shortDate(gap.to)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            val said = listOfNotNull(io.github.randomtools3377.checklist.core.reasonLabel(gap.reason, gap.reasonText), gap.note).joinToString(" · ")
            if (said.isNotEmpty()) {
                Text(said, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
