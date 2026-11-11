package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.AreaIdea
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.IdeaKind
import io.github.randomtools3377.checklist.core.Ideas
import io.github.randomtools3377.checklist.core.SkillArea

/**
 * Ideas to level up a skill area: stepping up the user's own goals, new activities suited to the
 * skill's level, and, with the AI ready, new ones from the AI. "More ideas" always brings more.
 * Choosing one opens the goal editor filled in, to check and save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(vm: MainViewModel, start: SkillArea, onAdd: (AreaIdea) -> Unit, onBack: () -> Unit) {
    val sheet by vm.character.collectAsState()
    val goals by vm.currentGoals.collectAsState()
    val hidden by vm.hiddenIdeas.collectAsState()
    val features by vm.features.collectAsState()
    var areaName by rememberSaveable { mutableStateOf(start.name) }
    val area = SkillArea.valueOf(areaName)
    var page by rememberSaveable(areaName) { mutableStateOf(0) }
    // The AI's ideas for this area, and every name shown, so it brings different ones each time.
    var aiIdeas by remember(areaName) { mutableStateOf<List<AreaIdea>>(emptyList()) }
    var shownNames by remember(areaName) { mutableStateOf<List<String>>(emptyList()) }
    var thinking by remember(areaName) { mutableStateOf(false) }
    var message by remember(areaName) { mutableStateOf<String?>(null) }
    val level = sheet?.skillLevel(area)?.level ?: 1
    val steps = remember(goals, area) { Ideas.stepUps(goals, area) }
    val library = remember(goals, hidden, area, level) { Ideas.library(area, level, goals, hidden) }
    val shown = Ideas.page(library, page)
    LaunchedEffect(shown) { shownNames = (shownNames + shown.map { it.goal.name }).distinct() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ideas to level up") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("ideas"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkillArea.entries.forEach { each ->
                    FilterChip(
                        selected = each == area,
                        onClick = { areaName = each.name },
                        label = { Text("${each.icon} ${each.label}") },
                        modifier = Modifier.testTag("ideas-area:${each.name}"),
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${area.icon} ${area.label} · Level $level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("ideas-title"))
                    Text(
                        "Ideas for ${Ideas.stageName(Ideas.stageFor(level))} with ${Ideas.about(area)}. Every point a goal here pays levels up ${area.label}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (steps.isNotEmpty()) {
                Text("Build on what you do", style = MaterialTheme.typography.titleSmall)
                steps.forEach { idea ->
                    IdeaCard(idea, primary = "Step up", onPrimary = { onAdd(idea) }, tag = "step-up:${idea.goalId}")
                }
            }

            Text("Try something new", style = MaterialTheme.typography.titleSmall)
            if (shown.isEmpty()) {
                Text(
                    "That's every idea here for ${area.label}. Bring back the ones you hid, or ask the AI for new ones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.forEach { idea ->
                IdeaCard(
                    idea,
                    primary = "Add",
                    onPrimary = { onAdd(idea) },
                    secondary = "Not for me",
                    onSecondary = { vm.hideIdea(idea.key) },
                    tag = "idea:${idea.key}",
                )
            }
            // There are always more: the list goes round again, and hidden ones can come back.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        // Round the list again; with nothing left to show, the hidden ones come back.
                        if (library.size <= shown.size && hidden.any { it.startsWith(area.name.lowercase() + ":") }) vm.unhideIdeas(area)
                        page++
                    },
                    modifier = Modifier.testTag("ideas-more"),
                ) { Text("More ideas") }
                if (hidden.any { it.startsWith(area.name.lowercase() + ":") }) {
                    TextButton(onClick = { vm.unhideIdeas(area) }, modifier = Modifier.testTag("ideas-unhide")) { Text("Bring back hidden ones") }
                }
            }

            // With the AI helper on: new ideas made up for this area, different each time.
            if (Feature.AI in features) {
                val status by vm.aiStatus.collectAsState()
                LaunchedEffect(Unit) { vm.refreshAi() }
                Text("✨ New ideas from the AI", style = MaterialTheme.typography.titleSmall)
                if (status?.usable != true) {
                    Text(status.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    aiIdeas.forEach { idea ->
                        IdeaCard(idea, primary = "Add", onPrimary = { onAdd(idea) }, tag = "idea:${idea.key}")
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                thinking = true
                                message = null
                                vm.aiIdeas(area, shownNames) { list, error ->
                                    thinking = false
                                    if (list.isNotEmpty()) {
                                        aiIdeas = list
                                        shownNames = (shownNames + list.map { it.goal.name }).distinct()
                                    }
                                    message = error
                                }
                            },
                            enabled = !thinking,
                            modifier = Modifier.testTag("ideas-ai"),
                        ) { Text(if (aiIdeas.isEmpty()) "Make new ideas" else "Make more") }
                        if (thinking) CircularProgressIndicator(Modifier.width(24.dp))
                    }
                }
            }
            Text(
                "Adding an idea opens it in the goal editor, ready to change before you save it. Stepping up changes a goal from now on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One idea: its icon, name and what it asks, with its buttons. */
@Composable
private fun IdeaCard(
    idea: AreaIdea,
    primary: String,
    onPrimary: () -> Unit,
    tag: String,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(idea.goal.icon ?: idea.area.icon, fontSize = 28.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(idea.goal.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (idea.kind == IdeaKind.STEP_UP) "From ${idea.from} to ${stepText(idea)}" else idea.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPrimary, modifier = Modifier.testTag("$tag:use")) { Text(primary) }
                    secondary?.let { TextButton(onClick = onSecondary, modifier = Modifier.testTag("$tag:hide")) { Text(it) } }
                }
            }
        }
    }
}

/** What a step-up asks for now, like "35 min" or "4 times a week". */
private fun stepText(idea: AreaIdea): String = idea.goal.amount?.let { io.github.randomtools3377.checklist.core.Amounts.format(it) + (idea.goal.unit?.let { u -> " $u" } ?: "") }
    ?: idea.goal.minutes?.let { "$it min" }
    ?: idea.goal.timesPerWeek?.let { "$it times a week" }
    ?: ""
