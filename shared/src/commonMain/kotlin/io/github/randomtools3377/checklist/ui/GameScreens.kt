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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import io.github.randomtools3377.checklist.core.Path
import io.github.randomtools3377.checklist.core.Paths
import io.github.randomtools3377.checklist.core.Title
import io.github.randomtools3377.checklist.core.Titles
import io.github.randomtools3377.checklist.core.QuestProgress
import io.github.randomtools3377.checklist.core.BossView
import androidx.compose.material3.TextButton
import io.github.randomtools3377.checklist.core.Bosses
import io.github.randomtools3377.checklist.core.Item
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
fun CharacterFigure(
    look: Look,
    outfit: Outfit,
    modifier: Modifier = Modifier,
    expression: io.github.randomtools3377.checklist.core.Expression = io.github.randomtools3377.checklist.core.Expression.CALM,
    jump: Boolean = false,
    /** Changes to hop again, like each new win on a hard day. */
    jumpKey: Any? = null,
) {
    val marks = remember(look, outfit, expression) { Figure.marks(look, outfit, expression) }
    val paths = remember(marks) {
        marks.filterIsInstance<Mark.Path>().associateWith { PathParser().parsePathString(it.d).toPath() }
    }
    val wearing = outfit.gear.joinToString { it.label }.ifEmpty { "everyday clothes" }
    // Jumping for joy: three little hops, each time it starts.
    val lift = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(jump, jumpKey) {
        if (jump) {
            repeat(3) {
                lift.animateTo(-0.14f, androidx.compose.animation.core.tween(160))
                lift.animateTo(0f, androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
    }
    val feeling = expression.name.lowercase()
    Canvas(
        modifier
            .aspectRatio(Figure.WIDTH / Figure.HEIGHT)
            .graphicsLayer { translationY = lift.value * size.height }
            .semantics { contentDescription = "Your character, looking $feeling, wearing $wearing" },
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
fun LevelCard(
    sheet: CharacterSheet,
    look: Look,
    onOpen: () -> Unit,
    title: String? = null,
    companion: CompanionView? = null,
    onMood: (Int) -> Unit = {},
    onMoodLater: () -> Unit = {},
    onSuggestion: (io.github.randomtools3377.checklist.core.HabitRow, Offset) -> Unit = { _, _ -> },
    onCheered: () -> Unit = {},
    onIdeas: (SkillArea) -> Unit = {},
) {
    val say = companion?.say
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("level-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        CharacterFigure(
            look,
            remember(sheet) { Outfit.of(sheet) },
            Modifier.height(56.dp).testTag("companion-face:${(say?.expression ?: io.github.randomtools3377.checklist.core.Expression.CALM).name}"),
            expression = say?.expression ?: io.github.randomtools3377.checklist.core.Expression.CALM,
            jump = say?.jumps == true,
            jumpKey = say?.wins,
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⭐ Level ${sheet.level.level}" + (title?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).testTag("level-title"),
                )
                Text(
                    "${sheet.level.toNext} to level ${sheet.level.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            LinearProgressIndicator(progress = { sheet.level.share }, modifier = Modifier.fillMaxWidth())
        }
        }
        // With check-ins on: what the character says, and what it suggests.
        if (companion != null && say != null && companion.talks) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(say.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp).testTag("companion-line"))
                }
                // On a hard day, each thing done shows as a small win, so the progress is there to see.
                if (say.hardDay && say.wins > 0) {
                    Text(
                        "🌱".repeat(say.wins.coerceAtMost(10)) + "  " + if (say.wins == 1) "1 small win today" else "${say.wins} small wins today",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.testTag("companion-wins"),
                    )
                }
                if (say.askMood) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        io.github.randomtools3377.checklist.core.Moods.FACES.forEachIndexed { index, face ->
                            Text(
                                face,
                                fontSize = 26.sp,
                                modifier = Modifier
                                    .clickable { onMood(index + 1) }
                                    .semantics { contentDescription = "Mood ${index + 1} of 5" }
                                    .padding(4.dp)
                                    .testTag("companion-mood:${index + 1}"),
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = onMoodLater, modifier = Modifier.testTag("companion-mood-later")) { Text("Not today") }
                    }
                }
                companion.suggestion?.let { row ->
                    var spot by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
                    val counted = row.goal.target > 1 && !row.isRoutine
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onSuggestion(row, spot) },
                        modifier = Modifier
                            .onGloballyPositioned { spot = it.boundsInRoot().center }
                            .testTag("companion-do"),
                    ) {
                        Text(
                            (if (counted) "+1  " else "✓ Done:  ") + "${row.goal.icon} ${row.goal.name}",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                if (say.levelUp) {
                    androidx.compose.material3.TextButton(onClick = onCheered, modifier = Modifier.testTag("companion-cheered")) { Text("🎉 Yay!") }
                }
                // On a day that's done, or free: something to grow next.
                companion.ideaArea?.let { area ->
                    androidx.compose.material3.TextButton(onClick = { onIdeas(area) }, modifier = Modifier.testTag("companion-ideas")) {
                        Text("💡 Ideas to level up ${area.icon} ${area.label}")
                    }
                }
            }
        }
        }
    }
}

/** The character page: its level, and each skill's level and medal. */
@Composable
fun CharacterScreen(
    sheet: CharacterSheet?,
    look: Look,
    onLook: (Look) -> Unit,
    onBack: () -> Unit,
    titles: CharacterTitles? = null,
    onPath: (Path) -> Unit = {},
    onTitle: (String) -> Unit = {},
    onIdeas: (SkillArea) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Character") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
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
                    titles?.shown?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("character-title")) }
                    LinearProgressIndicator(progress = { sheet.level.share }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${sheet.xp} experience in all · ${sheet.level.toNext} to level ${sheet.level.level + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            LevelUpNextCard(sheet, onIdeas)
            Text("Skills", style = MaterialTheme.typography.titleMedium)
            SkillArea.entries.forEach { area -> SkillRow(area, sheet.skillLevel(area), sheet.skills[area] ?: 0, onIdeas = { onIdeas(area) }) }
            PathCard(sheet, titles?.path, onPath)
            TitlesCard(titles, onTitle)
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
private fun SkillRow(area: SkillArea, level: Level, xp: Long, onIdeas: () -> Unit) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$xp experience · ${level.toNext} to level ${level.level + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(onClick = onIdeas, modifier = Modifier.testTag("skill-ideas:${area.name}")) { Text("💡 Ideas") }
                }
            }
        }
    }
}

/** The skill furthest behind, with ideas to level it up. */
@Composable
private fun LevelUpNextCard(sheet: CharacterSheet, onIdeas: (SkillArea) -> Unit) {
    val area = io.github.randomtools3377.checklist.core.Ideas.weakest(sheet)
    Card(
        Modifier.fillMaxWidth().testTag("level-up-next"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💡 Level up next: ${area.icon} ${area.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${area.label} is the skill furthest behind. See activities to grow it, ideas to build on what you already do, and more whenever you want them.",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.material3.FilledTonalButton(onClick = { onIdeas(area) }, modifier = Modifier.testTag("level-up-next-ideas")) { Text("See ideas") }
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

/**
 * On Today, with items and bosses on: this week's boss, what it is weaker to, its health, the
 * items in hand, and a button to use them all on it.
 */
@Composable
fun BossCard(view: BossView, onAttack: ((String) -> Unit) -> Unit) {
    val fight = view.fight
    var said by remember(fight.week) { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth().testTag("boss")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fight.boss.icon, fontSize = 34.sp, modifier = Modifier.width(52.dp))
                Column(Modifier.weight(1f)) {
                    Text(fight.boss.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("boss-name"))
                    Text(
                        "Weak to " + fight.boss.weakTo.sortedBy { it.ordinal }.joinToString(" and ") { "${it.icon} ${it.label}" } +
                            ": their items hit ${io.github.randomtools3377.checklist.core.Rules.current.bossWeakPercent}% as hard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (fight.beaten) {
                Text("🏆 Beaten this week! A new boss comes on Monday.", style = MaterialTheme.typography.bodyMedium, color = goodColor(), modifier = Modifier.testTag("boss-beaten"))
            } else {
                LinearProgressIndicator(progress = { fight.share }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
                Text("${fight.left} of ${fight.health} health left · it leaves on Sunday night", style = MaterialTheme.typography.bodySmall)
                if (view.items.isEmpty()) {
                    Text(
                        "Every tick earns an item from its goal's skill.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Item.entries.filter { (view.items[it] ?: 0) > 0 }.forEach { item ->
                            val strong = item.area in fight.boss.weakTo
                            Text(
                                "${item.icon} ${view.items[item]}" + if (strong) " ×2" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.semantics { contentDescription = "${view.items[item]} ${item.label}" + if (strong) ", hits twice as hard" else "" },
                            )
                        }
                    }
                    Button(
                        onClick = { onAttack { said = it } },
                        modifier = Modifier.testTag("boss-attack"),
                    ) { Text("Use ${view.itemCount} items: ${view.wouldDeal} damage") }
                }
            }
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("boss-said")) }
        }
    }
}

/** What to say after an attack. */
fun attackText(dealt: Int, beaten: Boolean, paid: Int): String = when {
    beaten && paid > 0 -> "💥 $dealt damage, and it's beaten! +${pointsText(paid)}"
    beaten -> "💥 $dealt damage, and it's beaten!"
    else -> "💥 $dealt damage!"
}

/** Choosing a path, from level 10: a title and a little more experience in its skill. */
@Composable
private fun PathCard(sheet: CharacterSheet, path: Path?, onPath: (Path) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("path")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Path", style = MaterialTheme.typography.titleSmall)
            if (sheet.level.level < Paths.AT_LEVEL) {
                Text(
                    "At level ${Paths.AT_LEVEL} you choose a path, like Athlete or Scholar: a title, and ${Paths.BONUS_PERCENT}% more experience in its skill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                if (path == null) "Choose your path. Its skill gets ${Paths.BONUS_PERCENT}% more experience. You can change it later."
                else "${path.icon} ${path.title}: ${path.area.icon} ${path.area.label} gets ${Paths.BONUS_PERCENT}% more experience.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Path.entries.forEach { choice ->
                    FilterChip(
                        selected = choice == path,
                        onClick = { onPath(choice) },
                        label = { Text("${choice.icon} ${choice.title}") },
                        modifier = Modifier.testTag("path:${choice.name}"),
                    )
                }
            }
        }
    }
}

/** The titles earned, the one shown picked from them, and those still to earn. */
@Composable
private fun TitlesCard(titles: CharacterTitles?, onTitle: (String) -> Unit) {
    if (titles == null) return
    Card(Modifier.fillMaxWidth().testTag("titles")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Titles", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                titles.path?.let { path ->
                    FilterChip(
                        selected = titles.setting == null || titles.setting == Titles.FROM_PATH,
                        onClick = { onTitle(Titles.FROM_PATH) },
                        label = { Text("${path.icon} ${path.title}") },
                    )
                }
                Title.entries.filter { it in titles.earned }.forEach { title ->
                    FilterChip(
                        selected = titles.setting == title.name,
                        onClick = { onTitle(title.name) },
                        label = { Text("${title.icon} ${title.label}") },
                        modifier = Modifier.testTag("title:${title.name}"),
                    )
                }
                FilterChip(selected = titles.setting == "", onClick = { onTitle("") }, label = { Text("None") })
            }
            Title.entries.filter { it !in titles.earned }.forEach { title ->
                Text(
                    "${title.icon} ${title.label}: ${title.how}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
