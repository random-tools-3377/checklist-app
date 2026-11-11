package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalMood
import io.github.randomtools3377.checklist.core.MoodFindings
import io.github.randomtools3377.checklist.core.MoodInsights
import io.github.randomtools3377.checklist.core.Way
import io.github.randomtools3377.checklist.core.WayKind

/**
 * Mood and your goals: from the mood check-ins over the last 90 days, the goals that go with
 * feeling better, and the ones that go with feeling worse, each with kind ways into it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodGoalsScreen(vm: MainViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val findings by vm.moodFindings.collectAsStateWithLifecycle()
    val goals by vm.currentGoals.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood and your goals") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        MoodGoalsList(
            findings = findings,
            goals = goals,
            chainsOn = Feature.CHAINS in features,
            padding = padding,
            onEdit = onEdit,
            onChain = { goal, after -> vm.setGoalAfter(goal.id, after.id) },
            onPay = { goal, points -> vm.setGoalPoints(goal.id, points) },
        )
    }
}

@Composable
private fun MoodGoalsList(
    findings: MoodFindings,
    goals: List<Goal>,
    chainsOn: Boolean,
    padding: PaddingValues,
    onEdit: (Long) -> Unit,
    onChain: (Goal, Goal) -> Unit,
    onPay: (Goal, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("mood-goals"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "From your mood check-ins over the last ${MoodInsights.DAYS} days: how you feel on days you do each goal, " +
                    "next to days it was due and not done. Moods go from 1 (😞) to 5 (😄).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            !findings.enough -> item {
                Text(MoodInsights.NOT_ENOUGH, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp).testTag("mood-goals-empty"))
            }
            findings.lifts.isEmpty() && findings.weighs.isEmpty() -> item {
                Text(MoodInsights.NOTHING_STANDS_OUT, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp).testTag("mood-goals-none"))
            }
        }
        if (findings.lifts.isNotEmpty()) {
            item { SectionTitle("What lifts you") }
            items(findings.lifts, key = { "lift-${it.goal.id}" }) { m -> LiftRow(m) }
        }
        if (findings.weighs.isNotEmpty()) {
            item { SectionTitle("What weighs on you") }
            item {
                Text(
                    "Not a judgement: some goals are just hard, or fall on hard days. Here are a few ways to make them lighter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(findings.weighs, key = { "weigh-${it.goal.id}" }) { m ->
                WeighCard(
                    m,
                    MoodInsights.ways(m, findings, goals),
                    chainsOn = chainsOn,
                    onEdit = { onEdit(m.goal.id) },
                    onChain = { after -> onChain(m.goal, after) },
                    onPay = { points -> onPay(m.goal, points) },
                )
            }
        }
        if (findings.enough) {
            item {
                Text(
                    "These show what tends to go together, not what causes what. They change as you keep checking in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LiftRow(m: GoalMood) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("mood-lift:${m.goal.name}")) {
        Text(m.goal.icon, fontSize = 26.sp, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(m.goal.name, style = MaterialTheme.typography.bodyLarge)
            Text(MoodInsights.describe(m), style = MaterialTheme.typography.bodyMedium, color = goodColor())
            Text(MoodInsights.basis(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A goal that weighs on the user, with up to three ways into it. */
@Composable
private fun WeighCard(m: GoalMood, ways: List<Way>, chainsOn: Boolean, onEdit: () -> Unit, onChain: (Goal) -> Unit, onPay: (Int) -> Unit) {
    val name = m.goal.name
    Card(Modifier.fillMaxWidth().testTag("mood-weigh:$name")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.goal.icon, fontSize = 26.sp, modifier = Modifier.width(40.dp))
                Text(name, style = MaterialTheme.typography.titleSmall)
            }
            Text(MoodInsights.describe(m), style = MaterialTheme.typography.bodyMedium)
            Text(MoodInsights.basis(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Ways in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ways.forEach { way ->
                Text("• " + way.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("way:${way.kind.name}:$name"))
                when (way.kind) {
                    WayKind.AFTER -> {
                        val after = way.after
                        if (after != null && way.canChain && chainsOn) {
                            TextButton(onClick = { onChain(after) }, modifier = Modifier.testTag("way-chain:$name")) {
                                Text("Make it come after ${after.icon} ${after.name}")
                            }
                        }
                    }
                    WayKind.SMALLER -> TextButton(onClick = onEdit, modifier = Modifier.testTag("way-smaller:$name")) { Text("Edit it") }
                    WayKind.PAY_MORE -> way.points?.let { points ->
                        TextButton(onClick = { onPay(points) }, modifier = Modifier.testTag("way-pay:$name")) { Text("Pay $points ${pointsWord(points)}") }
                    }
                    WayKind.EARLY -> {}
                }
            }
        }
    }
}
