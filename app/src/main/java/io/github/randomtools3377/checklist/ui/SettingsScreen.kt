@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.BuildConfig
import io.github.randomtools3377.checklist.core.Days

fun hourText(hour: Int): String = when (hour) {
    0 -> "Midnight"
    12 -> "Noon"
    else -> "$hour am"
}

@Composable
fun SettingsScreen(dayEndHour: Int, onDayEndHour: (Int) -> Unit, onBack: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { choosing = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("My day ends at", style = MaterialTheme.typography.titleMedium)
                Text(hourText(dayEndHour), color = MaterialTheme.colorScheme.primary)
                Text(
                    "Ticks before this time count for the day before, so a late night still counts as that day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Private and offline", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This app has no internet access and is left out of phone backups. Everything stays on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("My day ends at") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items((0..Days.MAX_DAY_END_HOUR).toList()) { hour ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDayEndHour(hour)
                                    choosing = false
                                },
                        ) {
                            RadioButton(selected = hour == dayEndHour, onClick = null)
                            Text(hourText(hour), modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Close") } },
        )
    }
}
