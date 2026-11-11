@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.CharacterSheet
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Level
import io.github.randomtools3377.checklist.core.Levels
import io.github.randomtools3377.checklist.core.SkillArea

/*
 * The character and its skills (an extra): the level card on Today, the character page, and the
 * skill area on a goal.
 */

/**
 * A goal's skill area, with the character on: the six areas to pick from, with the guess from
 * its name and category marked until one is picked. [skill] is the picked area's name.
 */
@Composable
fun SkillField(skill: String?, onSkill: (String?) -> Unit, name: String, category: String, vm: MainViewModel) {
    val features by vm.features.collectAsState()
    if (Feature.CHARACTER !in features) return
    val guess = SkillArea.guess(name, category.ifBlank { null })
    val chosen = skill?.let { s -> SkillArea.entries.firstOrNull { it.name == s } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Skill", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkillArea.entries.forEach { area ->
                FilterChip(
                    selected = (chosen ?: guess) == area,
                    onClick = { onSkill(if (chosen == area) null else area.name) },
                    label = { Text("${area.icon} ${area.label}") },
                    modifier = Modifier.testTag("skill:${area.name}"),
                )
            }
        }
        Text(
            if (chosen == null) "Guessed from the name: ${guess.label}. Pick another to change it." else "Its points level up ${chosen.label}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** On Today, with the character on: its level and how far to the next. Tap for the character page. */
@Composable
fun LevelCard(sheet: CharacterSheet, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("level-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐ Level ${sheet.level.level}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${sheet.level.toNext} to level ${sheet.level.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            LinearProgressIndicator(progress = { sheet.level.share }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The character page: its level, and each skill's level and medal. */
@Composable
fun CharacterScreen(sheet: CharacterSheet?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Character") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("character"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sheet == null) {
                Text("Turn on \"Character and skills\" in Settings → Features.")
                return@Column
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⭐ Level ${sheet.level.level}", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("character-level"))
                    LinearProgressIndicator(progress = { sheet.level.share }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${sheet.xp} experience in all · ${sheet.level.toNext} to level ${sheet.level.level + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text("Skills", style = MaterialTheme.typography.titleMedium)
            SkillArea.entries.forEach { area -> SkillRow(area, sheet.skillLevel(area), sheet.skills[area] ?: 0) }
            Text(
                "Every point a goal pays is experience for your character and for the goal's skill. Spending points and " +
                    "penalties never take experience away. A skill earns bronze at level 5, silver at 10 and gold at 20. " +
                    "Change a goal's skill in its editor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkillRow(area: SkillArea, level: Level, xp: Long) {
    Card(Modifier.fillMaxWidth().testTag("skill-row:${area.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(area.icon, fontSize = 28.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(area.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Levels.skillTier(level.level)?.let { tier -> Text("${tier.medal} ", style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "Level ${level.level}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("skill-level:${area.name}"),
                    )
                }
                LinearProgressIndicator(progress = { level.share }, modifier = Modifier.fillMaxWidth())
                Text(
                    "$xp experience · ${level.toNext} to level ${level.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
