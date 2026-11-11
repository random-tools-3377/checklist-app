@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.CharacterSheet
import io.github.randomtools3377.checklist.core.Eyes
import io.github.randomtools3377.checklist.core.Figure
import io.github.randomtools3377.checklist.core.HairStyle
import io.github.randomtools3377.checklist.core.Look
import io.github.randomtools3377.checklist.core.Looks
import io.github.randomtools3377.checklist.core.Mark
import io.github.randomtools3377.checklist.core.Outfit
import io.github.randomtools3377.checklist.core.QuestProgress
import io.github.randomtools3377.checklist.core.RaceStanding
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

/** The character, drawn: [look] as the user styled it, wearing [outfit]. */
@Composable
fun CharacterFigure(look: Look, outfit: Outfit, modifier: Modifier = Modifier) {
    val marks = remember(look, outfit) { Figure.marks(look, outfit) }
    val paths = remember(marks) {
        marks.filterIsInstance<Mark.Path>().associateWith { PathParser().parsePathString(it.d).toPath() }
    }
    val wearing = outfit.gear.joinToString { it.label }.ifEmpty { "everyday clothes" }
    Canvas(
        modifier
            .aspectRatio(Figure.WIDTH / Figure.HEIGHT)
            .semantics { contentDescription = "Your character, wearing $wearing" },
    ) {
        val s = size.width / Figure.WIDTH
        scale(scale = s, pivot = Offset.Zero) {
            marks.forEach { mark ->
                when (mark) {
                    is Mark.Circle -> {
                        mark.fill?.let { drawCircle(Color(it), mark.r, Offset(mark.cx, mark.cy)) }
                        mark.stroke?.let { drawCircle(Color(it), mark.r, Offset(mark.cx, mark.cy), style = Stroke(mark.width)) }
                    }
                    is Mark.Oval -> drawOval(Color(mark.fill), Offset(mark.cx - mark.rx, mark.cy - mark.ry), Size(mark.rx * 2, mark.ry * 2))
                    is Mark.Box -> drawRoundRect(Color(mark.fill), Offset(mark.x, mark.y), Size(mark.w, mark.h), CornerRadius(mark.radius))
                    is Mark.Path -> {
                        val path = paths.getValue(mark)
                        mark.fill?.let { drawPath(path, Color(it)) }
                        mark.stroke?.let { drawPath(path, Color(it), style = Stroke(mark.width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                    }
                }
            }
        }
    }
}

/** On Today, with the character on: its level and how far to the next. Tap for the character page. */
@Composable
fun LevelCard(sheet: CharacterSheet, look: Look, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("level-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        CharacterFigure(look, remember(sheet) { Outfit.of(sheet) }, Modifier.height(56.dp))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

/** The character page: its level, and each skill's level and medal. */
@Composable
fun CharacterScreen(sheet: CharacterSheet?, look: Look, onLook: (Look) -> Unit, onBack: () -> Unit) {
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
            val outfit = remember(sheet) { Outfit.of(sheet) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CharacterFigure(look, outfit, Modifier.height(240.dp).testTag("character-figure"))
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
            GearCard(sheet, outfit)
            StyleCard(look, onLook)
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

/** What the character wears now, and what each skill earns next. */
@Composable
private fun GearCard(sheet: CharacterSheet, outfit: Outfit) {
    Card(Modifier.fillMaxWidth().testTag("gear")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gear", style = MaterialTheme.typography.titleSmall)
            Text(
                if (outfit.gear.isEmpty()) "Nothing yet: each skill earns gear at levels 5, 10 and 20."
                else "Wearing: " + outfit.gear.joinToString { "${it.label} (${it.area.label} ${it.level})" } + ".",
                style = MaterialTheme.typography.bodyMedium,
            )
            Outfit.next(sheet).forEach { gear ->
                Text(
                    "${gear.area.icon} ${gear.area.label} level ${gear.level}: ${gear.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "When two pieces go in the same place, like two hats, the stronger skill's is worn. Your shirt takes the colour of your leading skill.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The user's choices for how the character looks. */
@Composable
private fun StyleCard(look: Look, onLook: (Look) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("style")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Style", style = MaterialTheme.typography.titleSmall)
            Text("Skin", style = MaterialTheme.typography.labelLarge)
            Swatches(Looks.SKINS, look.skin, "skin") { onLook(look.copy(skin = it)) }
            Text("Hair", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HairStyle.entries.forEach { style ->
                    FilterChip(
                        selected = look.hair == style,
                        onClick = { onLook(look.copy(hair = style)) },
                        label = { Text(style.label) },
                        modifier = Modifier.testTag("hair:${style.name}"),
                    )
                }
            }
            Text("Hair colour", style = MaterialTheme.typography.labelLarge)
            Swatches(Looks.HAIR, look.hairColour, "hair-colour") { onLook(look.copy(hairColour = it)) }
            Text("Eyes", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyes.entries.forEach { eyes ->
                    FilterChip(
                        selected = look.eyes == eyes,
                        onClick = { onLook(look.copy(eyes = eyes)) },
                        label = { Text(eyes.label) },
                        modifier = Modifier.testTag("eyes:${eyes.name}"),
                    )
                }
            }
        }
    }
}

/** A row of colours to pick from, the picked one ringed. */
@Composable
private fun Swatches(colours: List<Int>, picked: Int, tag: String, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colours.forEachIndexed { index, colour ->
            Box(
                Modifier
                    .size(36.dp)
                    .then(if (index == picked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .padding(4.dp)
                    .background(Color(colour), CircleShape)
                    .clickable { onPick(index) }
                    .semantics { contentDescription = "Colour ${index + 1}" + if (index == picked) ", chosen" else "" }
                    .testTag("$tag:$index"),
            )
        }
    }
}

/** On Today, with quests on: the day's quests, how far along each is, and what each pays. */
@Composable
fun QuestsCard(quests: List<QuestProgress>) {
    Card(Modifier.fillMaxWidth().testTag("quests")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🗺️ Today's quests", style = MaterialTheme.typography.titleSmall)
            quests.forEach { progress ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("quest:${progress.quest.id}")) {
                    Text(if (progress.complete) "✅" else "▫️", modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(progress.quest.text, style = MaterialTheme.typography.bodyMedium)
                        if (!progress.complete && progress.quest.target > 1) {
                            Text(
                                "${progress.done} of ${progress.quest.target}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "+${pointsText(progress.quest.reward)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (progress.complete) goodColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** On Today, with the race on: this week against last week by the same time. */
@Composable
fun RaceCard(race: RaceStanding) {
    Card(Modifier.fillMaxWidth().testTag("race")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("🏁", fontSize = 24.sp, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        race.ahead > 0 -> "${pointsText(race.ahead)} ahead of last week"
                        race.ahead < 0 -> "${pointsText(-race.ahead)} behind last week"
                        else -> "Level with last week"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (race.ahead >= 0) goodColor() else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("race-standing"),
                )
                Text(
                    "This week ${race.thisWeek} · last week by now ${race.lastWeek}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
