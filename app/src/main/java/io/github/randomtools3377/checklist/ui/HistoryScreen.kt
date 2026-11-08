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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.data.toLocalDateTime

private fun kindText(kind: EntryKind): String = when (kind) {
    EntryKind.TICK -> "Ticked"
    EntryKind.BONUS -> "Bonus for finishing"
    EntryKind.PURCHASE -> "Bought"
    EntryKind.RETURN -> "Returned"
    EntryKind.PENALTY -> "Over the limit"
    EntryKind.REFUND -> "Penalty given back"
}

/** Every change to the balance, newest first, grouped by day. */
@Composable
fun HistoryScreen(entries: List<Entry>?, locked: Boolean, onUnlock: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Points history") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (locked) {
            LockedPanel("Your points history", onUnlock, Modifier.padding(padding))
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
                        "No points yet. Tick a goal and it shows up here.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            entries.groupBy { it.day }.forEach { (day, onDay) ->
                item(key = "day-$day") {
                    val total = onDay.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(longDate(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(signed(total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
                items(onDay, key = { "entry-${it.id}" }) { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${kindText(entry.kind)} · ${timeText(entry.at.toLocalDateTime().time)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            signed(entry.amount),
                            fontWeight = FontWeight.Bold,
                            color = if (entry.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun signed(amount: Int): String = if (amount > 0) "+$amount" else "$amount"
