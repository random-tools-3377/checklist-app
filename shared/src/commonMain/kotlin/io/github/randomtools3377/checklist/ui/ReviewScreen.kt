@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.ColourBlind
import io.github.randomtools3377.checklist.core.Badge
import io.github.randomtools3377.checklist.core.DayRecord
import io.github.randomtools3377.checklist.core.Gap
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.ReviewSummary
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.weekdayShort
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToInt

/**
 * The review: a day, a week or a month at a time. Done, skipped and missed for each goal, the
 * reasons that come up most, points earned, spent and lost, the longest runs, gaps marked on the
 * calendar with what was written, and stats.
 */
@Composable
fun ReviewScreen(
    summary: ReviewSummary?,
    period: Period,
    streaks: Streaks?,
    hideReasons: Boolean,
    hideGapNotes: Boolean,
    onPeriod: (Period) -> Unit,
    onStep: (Int) -> Unit,
    badges: Int,
    onBadges: () -> Unit,
    padding: PaddingValues,
    moods: List<io.github.randomtools3377.checklist.core.Mood> = emptyList(),
    /** A note on the week from the optional AI, shown in the week view. */
    coachCard: @Composable (ReviewSummary) -> Unit = {},
    /** Whether the character is on, which adds the level badges. */
    character: Boolean = false,
    /** Opens "Mood and your goals", and the mood journal, from the mood part. */
    onMoodGoals: () -> Unit = {},
    onMoodJournal: () -> Unit = {},
    /** "See the notes" on the mood summary: the journal on the days from the first date to the second. */
    onMoodNotes: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    /** Unlocks what the privacy lock holds, from the locked Mood card. */
    onUnlock: () -> Unit = {},
    /** With no goals yet: adds a first one, for the hint at the top. */
    onAddGoal: (() -> Unit)? = null,
    /** The misses in the stretch that still stand, and what opens one to put it right (see [PastMissDialog]). */
    misses: List<io.github.randomtools3377.checklist.core.Lapse> = emptyList(),
    onMiss: (Long) -> Unit = {},
    /** The best run of each streak ever, shown beside today's numbers. */
    best: Streaks? = null,
    /** Every day that was a comeback (see Comebacks), for the count in the stats. */
    comebacks: List<LocalDate> = emptyList(),
    /** The main streak's target, to find the longest streak within a past stretch. */
    streakSettings: io.github.randomtools3377.checklist.core.StreakSettings = io.github.randomtools3377.checklist.core.StreakSettings(),
    /** The user's day today: a stretch that ended before it is a past one. */
    today: LocalDate? = null,
    /** The time spent with the focus timer in the stretch, for the week and month views. */
    focus: io.github.randomtools3377.checklist.core.FocusTotals? = null,
    /** How many badges there are to earn with the extras that are on; null for the usual ones. */
    badgeTotal: Int? = null,
    /** Opens a day in the Day view, from a tap on the month calendar or a day chart. */
    onDay: (LocalDate) -> Unit = {},
    /** Opens a goal's own page, from its row. */
    onGoal: (Long) -> Unit = {},
    /** What was finished in the stretch (see StretchDoneList), and what opens the Done list. */
    done: io.github.randomtools3377.checklist.core.StretchDone? = null,
    onDoneList: () -> Unit = {},
) {
    // Stepping to another stretch keeps the one on screen, dimmed, until the new one is ready.
    var waitingOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<ReviewSummary?>(null) }
    val waiting = waitingOn != null && waitingOn === summary
    androidx.compose.runtime.LaunchedEffect(waitingOn) {
        // Nothing new came (the same stretch was asked for again): back to full strength.
        if (waitingOn != null) {
            kotlinx.coroutines.delay(1500)
            waitingOn = null
        }
    }
    val strength = if (LocalCalm.current) (if (waiting) 0.5f else 1f) else {
        val moving by androidx.compose.animation.core.animateFloatAsState(if (waiting) 0.5f else 1f, androidx.compose.animation.core.tween(MotionTimes.ROLL), label = "review-dim")
        moving
    }
    // A goal with several misses in the stretch: the box that asks which one to open.
    val choosing = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    choosing.value?.let { goalId ->
        val mine = misses.filter { it.goalId == goalId }
        val name = summary?.goals?.firstOrNull { it.goal.id == goalId }?.goal?.let { "${it.icon} ${it.name}" }
        if (mine.isEmpty() || name == null) {
            androidx.compose.runtime.LaunchedEffect(goalId) { choosing.value = null }
        } else {
            GoalMissesDialog(name, mine, hideReasons, onMiss = { choosing.value = null; onMiss(it) }, onDone = { choosing.value = null })
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = strength }.testTag("review"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Period.DAILY to "Day", Period.WEEKLY to "Week", Period.MONTHLY to "Month", Period.YEARLY to "Year").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = period == value,
                        onClick = {
                            if (value != period) waitingOn = summary
                            onPeriod(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                        modifier = Modifier.testTag("review:$label"),
                    ) { Text(label) }
                }
            }
        }
        onAddGoal?.let { add ->
            item(key = "review-empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing to look back on yet. Add a goal and tick it on Today.", textAlign = TextAlign.Center, modifier = Modifier.testTag("review-empty"))
                    androidx.compose.material3.Button(onClick = add, modifier = Modifier.padding(top = 8.dp).testTag("review-empty-add")) { Text("Add a goal") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onBadges).testTag("review:badges")) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("🏅", fontSize = 24.sp, modifier = Modifier.width(40.dp))
                    Text("Badges", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("$badges of ${badgeTotal ?: Badge.shown(character).size} · See all", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (summary == null) {
            // The split second before the first stretch is read: faint card shapes.
            item { PlaceholderCards() }
            return@LazyColumn
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { waitingOn = summary; onStep(-1) }, modifier = Modifier.testTag("review:back")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier")
                }
                Text(
                    if (summary.from == summary.to) longDate(summary.from) else "${shortDate(summary.from)} to ${shortDate(summary.to)}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).testTag("review:range"),
                )
                IconButton(onClick = { waitingOn = summary; onStep(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later")
                }
            }
        }
        item { PointsCard(summary) }
        if (period == Period.WEEKLY) item { coachCard(summary) }
        // A day still to come has nothing to open.
        val openDay: (LocalDate) -> Unit = { day -> if (today == null || day <= today) onDay(day) }
        if (period == Period.WEEKLY || period == Period.MONTHLY) item { DayCharts(summary, period, openDay) }
        if (period == Period.YEARLY) item { YearCard(summary, moods, hideMoods = hideReasons) }
        if (period == Period.MONTHLY) {
            // Each day's mood as a small face, unless moods are behind the lock.
            item { MonthCalendar(summary, today, openDay, faces = if (hideReasons) emptyMap() else moodFacesByDay(moods.filter { it.day >= summary.from && it.day <= summary.to })) }
        }
        val shownMisses = misses.filter { it.day >= summary.from && it.day <= summary.to }
        item {
            GoalsCard(summary, shownMisses, onGoal) { goal ->
                val mine = shownMisses.filter { it.goalId == goal.goal.id }
                if (mine.size == 1) onMiss(mine.single().id) else choosing.value = goal.goal.id
            }
        }
        // What was finished: only where the stretch shown is the one it was worked out for.
        done?.takeIf { !it.isEmpty }?.let { finished -> item(key = "review-finished") { FinishedCard(finished, onGoal, onDoneList) } }
        // Focus time for the stretch belongs here (see the focus timer).
        // The day view lists what was missed; each opens to change its reason or say it was a forgotten tick.
        if (period == Period.DAILY && shownMisses.isNotEmpty()) item { MissedCard(shownMisses, hideReasons, onMiss) }
        if (summary.categories.isNotEmpty()) item { CategoriesCard(summary) }
        if (focus != null && focus.any && (period == Period.WEEKLY || period == Period.MONTHLY)) item { FocusTimeCard(focus) }
        item { ReasonsCard(summary, hideReasons) }
        val shownMoods = moods.filter { it.day >= summary.from && it.day <= summary.to }
        when {
            shownMoods.isEmpty() -> {}
            // Behind the lock it says so, with a way in, rather than going missing.
            hideReasons -> item { LockedMoodCard(onUnlock) }
            // A day keeps the full detail; a week, month or year is summed up, with the notes a tap away.
            period == Period.DAILY -> item { MoodCard(shownMoods, summary, onMoodGoals, onMoodJournal) }
            else -> item {
                MoodSummaryCard(moods, summary.from, summary.to, period, onNotes = { onMoodNotes(summary.from, summary.to) }, onMoodGoals = onMoodGoals, onMoodJournal = onMoodJournal)
            }
        }
        if (summary.gaps.isNotEmpty()) item { GapsCard(summary.gaps, hideGapNotes) }
        item { StatsCard(summary, streaks, best, streakSettings, today, comebacks) }
    }
}

@Composable
internal fun ReviewCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun PointsCard(summary: ReviewSummary) {
    ReviewCard(pointsTitle()) {
        Row(Modifier.fillMaxWidth()) {
            PointsFigure("Earned", "+${summary.earned}", goodColor(), Modifier.weight(1f))
            // A reward returned in a later stretch than it was bought gives points back here.
            PointsFigure("Spent", if (summary.spent < 0) "+${-summary.spent}" else "−${summary.spent}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            PointsFigure("Lost", "−${summary.lost}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        if (summary.interest != 0 || summary.bets != 0) {
            Row(Modifier.fillMaxWidth()) {
                if (summary.interest != 0) PointsFigure("Interest", withSign(summary.interest), goodColor(), Modifier.weight(1f))
                if (summary.bets != 0) PointsFigure("Bets", withSign(summary.bets), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
    }
}

private fun withSign(amount: Int): String = if (amount < 0) "−${-amount}" else "+$amount"

@Composable
private fun PointsFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.testTag("review:${label.lowercase()}"))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GoalsCard(
    summary: ReviewSummary,
    /** The misses in the stretch that still stand: a goal's row with any opens them. */
    misses: List<io.github.randomtools3377.checklist.core.Lapse> = emptyList(),
    /** Opens a goal's own page. */
    onGoal: (Long) -> Unit = {},
    onMisses: (io.github.randomtools3377.checklist.core.GoalSummary) -> Unit = {},
) {
    ReviewCard("Each goal") {
        val shown = summary.goals.filter { it.due > 0 || it.ticks > 0 }
        if (shown.isEmpty()) {
            Text("Nothing was due.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row {
                Text("", modifier = Modifier.weight(1f))
                listOf("Done", "Skipped", "Missed", "Run").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                }
            }
            shown.forEach { goal ->
                val opens = misses.any { it.goalId == goal.goal.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onGoal(goal.goal.id) }.testTag("review-goal:${goal.goal.name}"),
                ) {
                    Text("${goal.goal.icon} ${goal.goal.name}", maxLines = 1, modifier = Modifier.weight(1f))
                    Cell("${goal.done}/${goal.due}")
                    Cell("${goal.skipped}")
                    // Its misses open from their number, to change a reason or say it was a forgotten tick.
                    Text(
                        "${goal.missed}",
                        textAlign = TextAlign.Center,
                        color = if (goal.missed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (opens) FontWeight.Bold else null,
                        textDecoration = if (opens) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                        modifier = (if (opens) Modifier.clickable { onMisses(goal) }.testTag("review-goal-misses:${goal.goal.name}") else Modifier).width(56.dp),
                    )
                    Cell("${goal.longestRun}")
                }
            }
            Text("Run: the most times in a row it was done in this stretch. Tap a goal for its own page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (shown.any { goal -> misses.any { it.goalId == goal.goal.id } }) {
                Text(
                    "Tap a goal's misses to change a reason, or to say you forgot to tick it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Cell(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, textAlign = TextAlign.Center, color = color, modifier = Modifier.width(56.dp))
}

/** The year, month by month: how much got done, points earned, and mood. */
@Composable
private fun YearCard(summary: ReviewSummary, moods: List<io.github.randomtools3377.checklist.core.Mood>, hideMoods: Boolean) {
    val today = minOf(summary.to, io.github.randomtools3377.checklist.data.nowLocal().date)
    val months = io.github.randomtools3377.checklist.core.YearReview.months(summary, if (hideMoods) emptyList() else moods, summary.from.year, today)
    ReviewCard("${summary.from.year}, month by month") {
        Column(Modifier.testTag("review-year")) {
            months.forEach { month ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(io.github.randomtools3377.checklist.core.monthShort(month.month), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                    val share = month.share
                    if (share == null) {
                        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    } else {
                        SmoothBar(
                            progress = share.toFloat(),
                            modifier = Modifier.weight(1f).height(8.dp),
                        )
                        Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp).padding(start = 6.dp))
                    }
                    Text(if (month.earned > 0) "+${month.earned}" else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                    Text(month.mood?.let { io.github.randomtools3377.checklist.core.Moods.face(kotlin.math.round(it).toInt()) } ?: "", modifier = Modifier.width(28.dp))
                }
            }
        }
    }
}

/** How the user felt each day, beside how much of what was due got done. */
@Composable
private fun MoodCard(moods: List<io.github.randomtools3377.checklist.core.Mood>, summary: ReviewSummary, onMoodGoals: () -> Unit, onMoodJournal: () -> Unit) {
    ReviewCard("Mood") {
        val wide = moods.any { it.after != null }
        Column(Modifier.testTag("review-moods")) {
            moods.sortedBy { it.day }.forEach { mood ->
                val record = summary.days.firstOrNull { it.day == mood.day }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    // The morning's mood, and how it felt once everything was done, if asked.
                    Text(
                        io.github.randomtools3377.checklist.core.Moods.face(mood.score) +
                            (mood.after?.let { "→" + io.github.randomtools3377.checklist.core.Moods.face(it) } ?: ""),
                        fontSize = 20.sp,
                        modifier = Modifier.width(if (wide) 72.dp else 36.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(shortDate(mood.day), style = MaterialTheme.typography.bodyMedium)
                        if (mood.factors.isNotEmpty()) {
                            Text(mood.factors.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        mood.note?.let { Text("${firstLabel(mood)}: “$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        mood.afterNote?.let { Text("Later: “$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(
                        record?.takeIf { it.due > 0 }?.let { "${it.done} of ${it.due} done" } ?: "nothing due",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row {
            androidx.compose.material3.TextButton(onClick = onMoodJournal, modifier = Modifier.testTag("review-mood-journal")) { Text("Mood journal") }
            androidx.compose.material3.TextButton(onClick = onMoodGoals, modifier = Modifier.testTag("review-mood-goals")) { Text("Mood and your goals") }
        }
    }
}

@Composable
private fun ReasonsCard(summary: ReviewSummary, hidden: Boolean) {
    ReviewCard("Reasons that came up most") {
        when {
            hidden -> Text("🔒 Behind the privacy lock", style = MaterialTheme.typography.bodyMedium)
            // "All clear" only when nothing was skipped or missed: a skip needs no reason.
            summary.reasons.isEmpty() -> Text(
                if (summary.goals.none { it.skipped > 0 || it.missed > 0 }) "All clear: no skips or misses." else "No skips or misses with a reason.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> summary.reasons.forEach { (reason, count) ->
                Row {
                    Text(reason, modifier = Modifier.weight(1f))
                    Text("$count", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GapsCard(gaps: List<Gap>, hideNotes: Boolean) {
    ReviewCard("Gaps") {
        gaps.forEach { gap ->
            Text(
                if (gap.from == gap.to) shortDate(gap.from) else "${shortDate(gap.from)} to ${shortDate(gap.to)}",
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (hideNotes) "🔒 Behind the privacy lock" else listOfNotNull(io.github.randomtools3377.checklist.core.reasonLabel(gap.reason, gap.reasonText), gap.note).joinToString(" · ").ifEmpty { "Not answered yet" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The month as a calendar: how much of each day got done, with gap days marked. */
@Composable
private fun MonthCalendar(summary: ReviewSummary, today: LocalDate? = null, onDay: (LocalDate) -> Unit = {}, faces: Map<LocalDate, String> = emptyMap()) {
    val colourBlind = LocalColourBlind.current
    val brightness = LocalBrightness.current
    val first = summary.from
    val blanks = first.dayOfWeek.isoDayNumber - 1
    val cells: List<DayRecord?> = List(blanks) { null } + summary.days
    ReviewCard("Calendar") {
        Row {
            (1..7).forEach { Text(weekdayShort(it).take(1), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)) }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { record ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (record != null) {
                            val inGap = summary.gaps.any { record.day >= it.from && record.day <= it.to }
                            val share = if (record.due == 0) null else record.done.toFloat() / record.due
                            val all = share != null && share >= 1f && !inGap
                            val background = when {
                                inGap -> MaterialTheme.colorScheme.tertiaryContainer
                                share == null -> MaterialTheme.colorScheme.surfaceVariant
                                share >= 1f -> if (colourBlind) Color(ColourBlind.good(brightness)) else MaterialTheme.colorScheme.primary
                                share > 0f -> if (colourBlind) Color(ColourBlind.goodContainer(brightness)) else MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                            val text = when {
                                all -> if (colourBlind) Color(ColourBlind.onGood(brightness)) else MaterialTheme.colorScheme.onPrimary
                                !inGap && share == 0f -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            // Colour-blind friendly: a mark as well as a colour.
                            val mark = if (!colourBlind) null else when {
                                inGap -> "–"
                                share == null -> null
                                share >= 1f -> "✓"
                                share > 0f -> "◐"
                                else -> "✕"
                            }
                            // A tap opens the day; a day still to come has nothing to open.
                            val opens = today == null || record.day <= today
                            Column(
                                Modifier.fillMaxSize().background(background, RoundedCornerShape(8.dp))
                                    .then(if (opens) Modifier.clickable(onClickLabel = "Open ${shortDate(record.day)}") { onDay(record.day) } else Modifier)
                                    .testTag("review-day:${record.day.dayOfMonth}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val face = faces[record.day]
                                if (face == null) {
                                    Text("${record.day.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = text)
                                } else {
                                    // The day's mood, small, beside its number.
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${record.day.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = text)
                                        Text(face, fontSize = 11.sp, modifier = Modifier.testTag("day-mood:${record.day.dayOfMonth}"))
                                    }
                                }
                                if (mark != null) {
                                    Text(
                                        mark,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = text,
                                        modifier = Modifier.testTag("day-mark:${record.day.dayOfMonth}"),
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            (if (colourBlind) "✓ everything done · ◐ some · ✕ none · – a gap" else "Dark: everything done · light: some · red: none · purple: a gap") + ". Tap a day to open it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("review-calendar-key"),
        )
    }
}

/** One line of the review's stats about a streak, with the tag its value carries. */
internal data class StreakStat(val label: String, val value: String, val tag: String)

private fun daysText(days: Int): String = if (days == 1) "1 day" else "$days days"

/**
 * The streak lines of the review's stats. For the stretch that holds today: the current main
 * streak ([now]) with the best ever beside it, and the best run of each other streak ([best],
 * once it is worked out; never shown below today's number, which streak shields can keep going).
 * For a past week, month or year: the longest each streak ran within that stretch ([within])
 * instead of today's numbers. The main streak always has its line; the others only once they
 * have run at all, so nobody is shown a row of zeros.
 */
internal fun streakStats(now: Streaks?, best: Streaks?, within: Streaks?): List<StreakStat> {
    fun others(runs: Streaks, label: (String) -> String): List<StreakStat> = listOf(
        Triple("miss-free run", runs.noMisses, "review-streak:miss-free"),
        Triple("skip-free run", runs.noSkips, "review-streak:skip-free"),
        Triple("run within the limit", runs.withinLimit, "review-streak:within-limit"),
        Triple("run of perfect days", runs.perfect, "review-streak:perfect"),
    ).filter { it.second > 0 }.map { (name, days, tag) -> StreakStat(label(name), daysText(days), tag) }
    return when {
        within != null ->
            listOf(StreakStat("Longest main streak in this stretch", daysText(within.done), "review-streak:main")) +
                others(within) { "Longest $it in this stretch" }
        now == null -> emptyList()
        best == null -> listOf(StreakStat("Current main streak", daysText(now.done), "review-streak:main"))
        else -> io.github.randomtools3377.checklist.core.BestStreaks.atLeast(best, now).let { top ->
            val main = if (top.done > 0) "${daysText(now.done)} · best ${daysText(top.done)}" else daysText(now.done)
            listOf(StreakStat("Current main streak", main, "review-streak:main")) + others(top) { "Best $it" }
        }
    }
}

@Composable
private fun StatsCard(
    summary: ReviewSummary,
    streaks: Streaks?,
    best: Streaks?,
    streakSettings: io.github.randomtools3377.checklist.core.StreakSettings,
    today: LocalDate?,
    comebacks: List<LocalDate> = emptyList(),
) {
    // A past stretch shows the longest each streak ran within it; a single past day has no streak to show.
    val past = today != null && summary.to < today
    val within = androidx.compose.runtime.remember(summary.days, streakSettings, past) {
        if (past && summary.from != summary.to) io.github.randomtools3377.checklist.core.BestStreaks.of(summary.days, streakSettings) else null
    }
    val streakLines = if (past) streakStats(null, null, within) else streakStats(streaks, best, null)
    ReviewCard("Stats") {
        val lines = buildList {
            summary.mostConsistent?.let { add("Most consistent" to "${it.goal.icon} ${it.goal.name} · ${(it.consistency * 100).roundToInt()}%") }
            summary.mostTicked?.let { add("Most ticked" to "${it.goal.icon} ${it.goal.name} · ${it.ticks}") }
            summary.mostSkipped?.let { add("Most skipped" to "${it.goal.icon} ${it.goal.name} · ${it.skipped}") }
            summary.mostMissed?.let { add("Most missed" to "${it.goal.icon} ${it.goal.name} · ${it.missed}") }
            summary.bestWeekday?.let { add("Best day of the week" to weekdayShort(it)) }
            summary.worstWeekday?.let { add("Hardest day of the week" to weekdayShort(it)) }
            summary.goals.filter { it.points > 0 }.sortedByDescending { it.points }.take(3).forEach {
                add("${pointsTitle()} from ${it.goal.icon} ${it.goal.name}" to "+${it.points}")
            }
            // One goal's times done in a row, which the streaks below (whole days) are not.
            if (streaks != null) add("Longest run of one goal in this stretch" to (summary.goals.maxOfOrNull { g -> g.longestRun }?.toString() ?: "0"))
        }
        val comebackLine = comebackStat(comebacks, summary.from, summary.to)
        if (lines.isEmpty() && streakLines.isEmpty() && comebackLine == null) Text("Not enough yet to say.", style = MaterialTheme.typography.bodyMedium)
        lines.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        streakLines.forEachIndexed { index, stat ->
            if (index > 0 || lines.isNotEmpty()) HorizontalDivider()
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(stat.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(stat.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.testTag(stat.tag))
            }
        }
        // Credit for getting back to it, with no word on the days before.
        comebackLine?.let { value ->
            if (lines.isNotEmpty() || streakLines.isNotEmpty()) HorizontalDivider()
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("Comebacks", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("review-comebacks"))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * The comebacks line of the review's stats: how many of [comebacks] fall in the stretch shown
 * ([from] to [to]), with the number in all beside it when that is more. Null while there has
 * never been one, so nobody is shown a zero.
 */
internal fun comebackStat(comebacks: List<LocalDate>, from: LocalDate, to: LocalDate): String? {
    if (comebacks.isEmpty()) return null
    val here = comebacks.count { it in from..to }
    return if (here == comebacks.size) "$here" else "$here · ${comebacks.size} in all"
}

/** Each day of a week or a month as bars: how much of what was due got done, and the points. */
@Composable
private fun DayCharts(summary: ReviewSummary, period: Period, onDay: (LocalDate) -> Unit = {}) {
    val days = summary.days
    if (days.isEmpty()) return
    val shares = days.map { if (it.due == 0) null else it.done * 100 / it.due }
    val earned = days.map { summary.earnedByDay[it.day] ?: 0 }
    val lost = days.map { summary.lostByDay[it.day] ?: 0 }
    val primary = goodColor()
    val error = MaterialTheme.colorScheme.error
    val faint = MaterialTheme.colorScheme.outlineVariant
    ReviewCard("Each day") {
        Text("Done", style = MaterialTheme.typography.labelLarge)
        BarChart(
            up = shares.map { it ?: 0 },
            top = 100,
            upColor = primary,
            downColor = error,
            marks = shares.map { share -> when (share) { null -> faint; 0 -> error; else -> null } },
            lineColor = faint,
            description = "Done each day: " + days.zip(shares).joinToString("; ") { (record, share) ->
                "${dayName(record.day, period)} " + (share?.let { "$it%" } ?: "nothing due")
            },
            tag = "chart:done",
            onBar = { index -> days.getOrNull(index)?.let { onDay(it.day) } },
        )
        DayLabels(days.map { it.day }, period, onDay)
        Spacer(Modifier.height(6.dp))
        Text(pointsTitle(), style = MaterialTheme.typography.labelLarge)
        BarChart(
            up = earned,
            down = lost,
            upColor = primary,
            downColor = error,
            marks = List(days.size) { null },
            lineColor = faint,
            description = "${pointsTitle()} each day: " + days.indices.joinToString("; ") { i ->
                "${dayName(days[i].day, period)} +${earned[i]}" + if (lost[i] > 0) ", −${lost[i]} lost" else ""
            },
            tag = "chart:points",
            onBar = { index -> days.getOrNull(index)?.let { onDay(it.day) } },
        )
        DayLabels(days.map { it.day }, period)
        val best = days.indices.filter { earned[it] > 0 }.maxByOrNull { earned[it] }
        Text(
            listOfNotNull(
                best?.let { "Most ${pointsWord()}: ${shortDate(days[it].day)}, +${earned[it]}" },
                if (lost.any { it > 0 }) "red below the line: ${pointsWord()} lost" else null,
            ).joinToString(" · ").ifEmpty { "No ${pointsWord()} earned yet in this stretch." } + " Tap a day to open it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayName(day: LocalDate, period: Period): String =
    if (period == Period.WEEKLY) weekdayShort(day.dayOfWeek.isoDayNumber) else shortDate(day)

/** Bars up from a line, and optionally down from it in another colour, one per day. */
@Composable
private fun BarChart(
    up: List<Int>,
    down: List<Int> = emptyList(),
    top: Int? = null,
    upColor: Color,
    downColor: Color,
    marks: List<Color?>,
    lineColor: Color,
    description: String,
    tag: String,
    height: Dp = 88.dp,
    /** A tap on a day's bar, or the space above it, by its place from the left. */
    onBar: ((Int) -> Unit)? = null,
) {
    val upMost = (top ?: up.maxOrNull() ?: 0).coerceAtLeast(1)
    val downMost = down.maxOrNull() ?: 0
    Canvas(
        Modifier.fillMaxWidth().height(if (downMost > 0) height + 28.dp else height)
            .semantics { contentDescription = description }
            .then(
                if (onBar == null || up.isEmpty()) Modifier else Modifier.pointerInput(up.size, onBar) {
                    detectTapGestures { at -> onBar((at.x / (size.width.toFloat() / up.size)).toInt().coerceIn(0, up.size - 1)) }
                },
            )
            .testTag(tag),
    ) {
        val slot = size.width / up.size
        val bar = slot * 0.62f
        val radius = CornerRadius(minOf(bar / 2, 4.dp.toPx()))
        val zero = if (downMost > 0) size.height - 28.dp.toPx() else size.height - 2.dp.toPx()
        val below = size.height - zero
        up.forEachIndexed { i, value ->
            val x = i * slot + (slot - bar) / 2
            val h = zero * value.coerceAtMost(upMost) / upMost
            if (h > 0f) drawRoundRect(upColor, Offset(x, zero - h), Size(bar, h), radius)
            val d = if (downMost == 0) 0f else below * (down.getOrNull(i) ?: 0) / downMost
            if (d > 0f) drawRoundRect(downColor, Offset(x, zero), Size(bar, d), radius)
            marks.getOrNull(i)?.let { drawCircle(it, radius = minOf(bar / 2, 3.dp.toPx()), center = Offset(x + bar / 2, zero - 3.dp.toPx())) }
        }
        drawLine(lineColor, Offset(0f, zero), Offset(size.width, zero), strokeWidth = 1.dp.toPx())
    }
}

/** The days under a chart: each weekday for a week, every 7th date for a month. */
@Composable
private fun DayLabels(days: List<LocalDate>, period: Period, onDay: ((LocalDate) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth()) {
        days.forEach { day ->
            val label = when {
                period == Period.WEEKLY -> weekdayShort(day.dayOfWeek.isoDayNumber).take(1)
                (day.dayOfMonth - 1) % 7 == 0 -> "${day.dayOfMonth}"
                else -> ""
            }
            // Under the first chart each day's place opens that day, for a screen reader or a keyboard too.
            val open = if (onDay == null) Modifier else Modifier
                .clickable(onClickLabel = "Open ${shortDate(day)}") { onDay(day) }
                .semantics { contentDescription = shortDate(day) }
                .testTag("chart-day:$day")
            Box(Modifier.weight(1f).then(open), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/** Each category: how much of what was due got done, and the points its goals earned. */
@Composable
private fun CategoriesCard(summary: ReviewSummary) {
    ReviewCard("By category") {
        summary.categories.forEach { category ->
            val name = category.name ?: "No category"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("review:category:$name")) {
                Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (category.due > 0) "${category.done}/${category.due} done" else "nothing due", textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
                Text("+${category.points}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
            }
            if (category.due > 0) {
                SmoothBar(progress = category.consistency.toFloat(), modifier = Modifier.fillMaxWidth())
            }
        }
        Text("${pointsTitle()} come from ticks and bonuses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * What was finished in the stretch: projects achieved (and a deadline missed, against its
 * project), to-dos done, and what each goal with an amount came to.
 */
@Composable
private fun FinishedCard(done: io.github.randomtools3377.checklist.core.StretchDone, onGoal: (Long) -> Unit, onDoneList: () -> Unit) {
    ReviewCard("Finished") {
        Column(Modifier.testTag("review-finished")) {
            done.projects.forEach { project ->
                val finished = project.finishedOn
                val missed = project.missedDeadline
                Row(Modifier.fillMaxWidth().clickable { onGoal(project.goal.id) }.padding(vertical = 3.dp).testTag("review-project:${project.goal.name}")) {
                    Text("${project.goal.icon} ${project.goal.name}", maxLines = 1, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        if (finished != null) Text("Achieved ${shortDate(finished)}", color = goodColor(), fontWeight = FontWeight.Medium)
                        if (missed != null) Text("Deadline missed ${shortDate(missed)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            done.todos.forEach { todo ->
                Row(Modifier.fillMaxWidth().clickable { onGoal(todo.goal.id) }.padding(vertical = 3.dp).testTag("review-todo:${todo.goal.name}")) {
                    Text("${todo.goal.icon} ${todo.goal.name}", maxLines = 1, modifier = Modifier.weight(1f))
                    Text("Done ${shortDate(todo.day)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            done.amounts.forEach { total ->
                Text(
                    "${total.goal.icon} ${total.text}",
                    modifier = Modifier.fillMaxWidth().clickable { onGoal(total.goal.id) }.padding(vertical = 3.dp).testTag("review-amount:${total.goal.name}"),
                )
            }
        }
        if (done.projects.any { it.finishedOn != null } || done.todos.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = onDoneList, modifier = Modifier.testTag("review-done-list")) { Text("See the Done list") }
        }
    }
}
