@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Badge
import io.github.randomtools3377.checklist.core.BadgeProgress
import io.github.randomtools3377.checklist.core.Badges
import kotlinx.datetime.LocalDate

/**
 * Every badge: the one closest to being earned first, as "Next up"; then the ones earned, with
 * the day; then the ones still to earn, with how, and a bar for how far along each is.
 */
@Composable
fun BadgesScreen(
    earned: Map<Badge, LocalDate>,
    onSeen: () -> Unit,
    onBack: () -> Unit,
    /** The extras turned on: the character's, the Budget's and the mood check-in's badges show only with theirs. */
    on: Set<io.github.randomtools3377.checklist.core.Feature> = emptySet(),
    /** How far along the badges with something to count are (see Badges.progress). */
    progress: Map<Badge, BadgeProgress> = emptyMap(),
) {
    LaunchedEffect(earned.size) { onSeen() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badges") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("badges"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shown = Badge.shown(on)
            item {
                Text(
                    // A badge kept after its extra is turned off isn't listed, so isn't counted.
                    "${shown.count { it in earned }} of ${shown.size} earned. Once earned, a badge is yours to keep. Badges in a series go bronze, silver, then gold.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val (got, toGo) = shown.partition { it in earned }
            val next = Badges.nextUp(shown, earned.keys, progress)
            if (next != null) item(key = next.name) { BadgeRow(next, null, progress[next], nextUp = true) }
            items(got.sortedByDescending { earned[it] }, key = { it.name }) { badge -> BadgeRow(badge, earned[badge]) }
            items(toGo.filter { it != next }, key = { it.name }) { badge -> BadgeRow(badge, null, progress[badge]) }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, on: LocalDate?, progress: BadgeProgress? = null, nextUp: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(if (on != null) "badge:${badge.name}" else "badge-to-go:${badge.name}"),
        colors = if (on != null) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        border = if (nextUp) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(badge.icon, fontSize = 32.sp, modifier = Modifier.width(52.dp).alpha(if (on != null) 1f else 0.35f))
            Column(Modifier.weight(1f)) {
                if (nextUp) {
                    Text(
                        "Next up",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("badge-next-up"),
                    )
                }
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
                // Still to earn, with something to count: how far along it is.
                if (on == null && progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.share },
                        // The words below say the same, so a screen reader reads it once.
                        modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {},
                    )
                    Text(
                        progress.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp).testTag("badge-progress:${badge.name}"),
                    )
                }
            }
        }
    }
}
