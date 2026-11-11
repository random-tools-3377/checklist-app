@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Badge
import kotlinx.datetime.LocalDate

/** Every badge: the ones earned, with the day, and the ones still to earn, with how. */
@Composable
fun BadgesScreen(earned: Map<Badge, LocalDate>, onSeen: () -> Unit, onBack: () -> Unit, character: Boolean = false) {
    LaunchedEffect(earned.size) { onSeen() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badges") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("badges"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "${earned.size} of ${Badge.shown(character).size} earned. Once earned, a badge is yours to keep. Badges in a series go bronze, silver, then gold.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val (got, toGo) = Badge.shown(character).partition { it in earned }
            items(got.sortedByDescending { earned[it] }, key = { it.name }) { badge -> BadgeRow(badge, earned[badge]) }
            items(toGo, key = { it.name }) { badge -> BadgeRow(badge, null) }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, on: LocalDate?) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(if (on != null) "badge:${badge.name}" else "badge-to-go:${badge.name}"),
        colors = if (on != null) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(badge.icon, fontSize = 32.sp, modifier = Modifier.width(52.dp).alpha(if (on != null) 1f else 0.35f))
            Column(Modifier.weight(1f)) {
                Text(
                    (badge.tier?.let { "${it.medal} " } ?: "") + badge.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (on != null) "Earned ${shortDate(on)}" else badge.how,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
