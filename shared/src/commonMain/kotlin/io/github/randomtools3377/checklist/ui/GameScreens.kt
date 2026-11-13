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
import io.github.randomtools3377.checklist.core.CharacterMove
import io.github.randomtools3377.checklist.core.CharacterMoves
import io.github.randomtools3377.checklist.core.CharacterSheet
import io.github.randomtools3377.checklist.core.Expression
import io.github.randomtools3377.checklist.core.Motion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.withTransform
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

/**
 * The character, drawn: [look] as the user styled it, wearing [outfit], with the face for
 * [expression]. It breathes and blinks while [alive], and plays [move] once each time [move]
 * or [moveKey] changes (a move that has started plays to its end). With [still], which follows
 * the device's "remove animations" setting unless given, nothing moves at all.
 *
 * Its size comes from [modifier]: give it a height (or a width) and it keeps its shape.
 * See [Character] for one that takes a size, and [CharacterFor] for one fed from the view model.
 */
@Composable
fun CharacterFigure(
    look: Look,
    outfit: Outfit,
    modifier: Modifier = Modifier,
    expression: Expression = Expression.CALM,
    move: CharacterMove = CharacterMove.NONE,
    /** Changes to play [move] again, like each new win on a hard day. */
    moveKey: Any? = null,
    alive: Boolean = true,
    still: Boolean = LocalPhone.current.reducedMotion(),
) {
    val open = remember(look, outfit, expression) { Figure.parts(look, outfit, expression) }
    val shut = remember(look, outfit, expression) { Figure.parts(look, outfit, expression, blink = true) }
    val rest = remember(expression) { Figure.rest(expression) }
    val paths = remember(open, shut) {
        (open.all + shut.head).filterIsInstance<Mark.Path>().distinct().associateWith { PathParser().parsePathString(it.d).toPath() }
    }
    val wearing = outfit.gear.joinToString { it.label }.ifEmpty { "everyday clothes" }
    // The move being played and how far in it is; -1 when none. Only the drawing reads these, so a frame redraws and nothing more.
    var playing by remember { mutableStateOf(CharacterMove.NONE) }
    var request by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var moveMs by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var idleMs by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }
    LaunchedEffect(move, moveKey, still) {
        if (move != CharacterMove.NONE && !still) {
            playing = move
            request++
        }
    }
    LaunchedEffect(request, still) {
        if (request == 0 || still) {
            moveMs = -1
            return@LaunchedEffect
        }
        val length = CharacterMoves.duration(playing)
        val start = androidx.compose.runtime.withFrameMillis { it }
        try {
            do {
                moveMs = (androidx.compose.runtime.withFrameMillis { it } - start).toInt()
            } while (moveMs < length)
        } finally {
            moveMs = -1
        }
    }
    // Breathing and blinking, for as long as it is on screen. Like every endless animation, it stops by itself in UI tests.
    LaunchedEffect(alive, still) {
        idleMs = -1L
        if (alive && !still) {
            val start = androidx.compose.animation.core.withInfiniteAnimationFrameMillis { it }
            try {
                while (true) idleMs = androidx.compose.animation.core.withInfiniteAnimationFrameMillis { it } - start
            } finally {
                idleMs = -1L
            }
        }
    }
    val feeling = expression.name.lowercase()
    Canvas(
        modifier
            .aspectRatio(Figure.WIDTH / Figure.HEIGHT)
            .semantics { contentDescription = "Your character, looking $feeling, wearing $wearing" },
    ) {
        val pose = CharacterMoves.pose(rest, playing, moveMs, idleMs.takeIf { it >= 0 })
        val s = size.width / Figure.WIDTH
        scale(scale = s, pivot = Offset.Zero) {
            Figure.layers(if (pose.blink) shut else open, pose).forEach { layer ->
                withTransform({
                    layer.motions.forEach { motion ->
                        when (motion) {
                            is Motion.Shift -> translate(motion.dx, motion.dy)
                            is Motion.Turn -> rotate(motion.degrees, Offset(motion.px, motion.py))
                        }
                    }
                }) {
                    drawMarks(layer.marks, paths)
                }
            }
        }
    }
}

/**
 * The character at a given [size] (its height), for any screen: its face is [expression], and
 * it plays [move] once whenever [move] or [moveKey] changes. Pick the face and move with the
 * core's pure functions (Checkins.say, Checkins.at).
 */
@Composable
fun Character(
    look: Look,
    outfit: Outfit,
    expression: Expression,
    move: CharacterMove,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    moveKey: Any? = null,
    alive: Boolean = true,
) {
    CharacterFigure(look, outfit, modifier.height(size), expression, move, moveKey, alive)
}

/**
 * The user's own character (its look and the gear its skills have earned), at [size], for a
 * screen that only has the view model. Draws nothing while "Character and skills" is off.
 */
@Composable
fun CharacterFor(
    vm: MainViewModel,
    expression: Expression,
    move: CharacterMove,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    moveKey: Any? = null,
) {
    val sheet by vm.character.collectAsState()
    val look by vm.look.collectAsState()
    sheet?.let { Character(look, remember(it) { Outfit.of(it) }, expression, move, size, modifier, moveKey) }
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
    /** On a low morning: a goal still to do that often goes with a better mood, named on this card. */
    moodAlso: io.github.randomtools3377.checklist.core.HabitRow? = null,
    /** On a low morning: one gentle line naming a reward to look forward to. */
    lookForward: String? = null,
    onCheered: () -> Unit = {},
    onIdeas: (SkillArea) -> Unit = {},
    /** The character has waved hello for today. */
    onGreeted: () -> Unit = {},
) {
    val say = companion?.say
    // The first look of the day: a wave, once. Told back once it has had time to play.
    val greet = companion?.greet == true
    LaunchedEffect(greet) {
        if (greet) {
            kotlinx.coroutines.delay(CharacterMoves.duration(CharacterMove.WAVE).toLong())
            onGreeted()
        }
    }
    val move = say?.move?.takeIf { it != CharacterMove.NONE } ?: if (greet) CharacterMove.WAVE else CharacterMove.NONE
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("level-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        CharacterFigure(
            look,
            remember(sheet) { Outfit.of(sheet) },
            Modifier.height(56.dp).testTag("companion-face:${(say?.expression ?: Expression.CALM).name}"),
            expression = say?.expression ?: Expression.CALM,
            move = move,
            // Each new line with a move plays it again: a nod for each tick, a hop for each win.
            moveKey = say?.line,
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
                GuideButton(HelpTopic.CHARACTER)
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
                                    .semantics { contentDescription = io.github.randomtools3377.checklist.core.Moods.spoken(index + 1) }
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
                    // Why this one, from what the app knows.
                    say.reason?.let { reason ->
                        Text(
                            "Why this one: $reason.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.testTag("companion-reason"),
                        )
                    }
                }
                // A low morning: the one suggestion is on this card. The goal that often goes with a
                // better mood is named here too, with its own button unless it is the quick goal above.
                if (say.hardDay) {
                    moodAlso?.let { row ->
                        Text(
                            io.github.randomtools3377.checklist.core.MoodInsights.alsoLine(row.goal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.testTag("companion-mood-goal"),
                        )
                        // A goal with an amount or a checklist is done from its own card, where that is entered.
                        if (row.goal.id != companion.suggestion?.goal?.id && !row.goal.hasAmount && !row.isRoutine) {
                            var spot by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
                            val counted = row.goal.target > 1 && !row.isRoutine
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { onSuggestion(row, spot) },
                                modifier = Modifier
                                    .onGloballyPositioned { spot = it.boundsInRoot().center }
                                    .testTag("companion-mood-do"),
                            ) {
                                Text(
                                    (if (counted) "+1  " else "✓ Done:  ") + "${row.goal.icon} ${row.goal.name}",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    lookForward?.let { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.testTag("companion-look-forward"),
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
    /** Today's face, as on its card on Today. */
    expression: Expression = Expression.CALM,
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
            // It waves when the page opens; a tap gets a hop and a line: how far to the next level, or the next gear.
            var taps by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
            // On a short window, like a phone on its side, it is smaller, so the level card still shows under it.
            val tall = minOf(240.dp, screenHeight() * 0.45f).coerceAtLeast(120.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CharacterFigure(
                    look,
                    outfit,
                    Modifier
                        .height(tall)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "hear how far to the next level",
                        ) { taps++ }
                        .testTag("character-figure"),
                    expression = expression,
                    move = if (taps == 0) CharacterMove.WAVE else CharacterMove.HOP,
                    moveKey = taps,
                )
                if (taps > 0) {
                    SpeechBubble(
                        io.github.randomtools3377.checklist.core.Checkins.pageLine(sheet, taps - 1),
                        Modifier.testTag("character-says"),
                    )
                } else {
                    Text(
                        "Tap your character to hear how far it is to the next level.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    "${gear.area.icon} ${gear.area.label} level ${gear.level}: ${gear.label} · ${Outfit.left(sheet, gear)} experience to go",
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
internal fun StyleCard(look: Look, onLook: (Look) -> Unit, title: String = "Style", preview: Outfit? = null) {
    Card(Modifier.fillMaxWidth().testTag("style")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                // Away from the big figure, like in the setup: a small one here shows each choice as it is made.
                preview?.let { CharacterFigure(look, it, Modifier.height(64.dp).testTag("style-preview"), expression = Expression.HAPPY) }
            }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🗺️ Today's quests", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GuideButton(HelpTopic.QUESTS)
            }
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
fun RaceCard(race: RaceStanding, catchUp: io.github.randomtools3377.checklist.core.CatchUp? = null) {
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
                // Behind or level: a way into the lead from today's list.
                catchUp?.let {
                    Text(it.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("race-catch-up"))
                }
            }
            GuideButton(HelpTopic.RACE)
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
                GuideButton(HelpTopic.BOSSES)
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
                                "${item.icon} ${view.items[item]}" + if (strong) " ×${io.github.randomtools3377.checklist.core.RuleWords.times(io.github.randomtools3377.checklist.core.Rules.current.bossWeakPercent)}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.semantics { contentDescription = "${view.items[item]} ${item.label}" + if (strong) ", ${io.github.randomtools3377.checklist.core.RuleWords.weakHit()}" else "" },
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
