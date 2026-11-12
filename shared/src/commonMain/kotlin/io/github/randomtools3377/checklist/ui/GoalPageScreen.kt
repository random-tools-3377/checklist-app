@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.ColourBlind
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalDayMark
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.GoalPage
import io.github.randomtools3377.checklist.core.GoalPageView
import io.github.randomtools3377.checklist.core.GoalRate
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.reasonLabel
import io.github.randomtools3377.checklist.core.weekdayShort
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * A goal's own page: a month calendar of its done, skipped and missed days, its runs, how often
 * it got done lately, what it earned, and its miss reasons and tick notes. Opened from the Goals
 * list, from "History" on its Today card, and from its row in the review. With [hideReasons],
 * while reasons and notes are locked, those are hidden behind an Unlock button.
 */
@Composable
fun GoalPageScreen(
    goalId: Long,
    vm: MainViewModel,
    hideReasons: Boolean,
    onUnlock: () -> Unit,
    onEdit: (Goal) -> Unit,
    /** Opens a skip or miss to change its reason or say it was a forgotten tick. */
    onMiss: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val view by remember(goalId) { vm.goalPage(goalId) }.collectAsStateWithLifecycle(null)
    val shown = view
    // A goal removed from its editor has no page: back to where it was opened from.
    LaunchedEffect(shown?.goal?.archived) { if (shown?.goal?.archived == true) onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shown?.goal?.let { "${it.icon} ${it.name}" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("goal-page-title")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    shown?.goal?.takeIf { !it.archived }?.let { goal ->
                        TextButton(onClick = { onEdit(goal) }, modifier = Modifier.testTag("goal-page-edit")) { Text("Edit") }
                    }
                    HelpAction()
                },
            )
        },
    ) { padding ->
        if (shown == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("goal-page"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calendar") { GoalCalendar(shown) }
            item(key = "numbers") { GoalNumbers(shown) }
            if (shown.goal.kind == GoalKind.PROJECT) item(key = "steps") { GoalSteps(shown) }
            if (hideReasons) {
                item(key = "locked") {
                    PageCard("Reasons and notes") {
                        Text("🔒 Behind the privacy lock", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onUnlock, modifier = Modifier.testTag("goal-page-unlock")) { Text("Unlock") }
                    }
                }
            } else {
                item(key = "lapses") { GoalLapses(shown, onMiss) }
                item(key = "notes") { GoalNotes(shown) }
            }
        }
    }
}

@Composable
private fun PageCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** One month of the goal's days, with arrows to earlier months, back to its first day. */
@Composable
private fun GoalCalendar(view: GoalPageView) {
    // How many months back from this one.
    var back by rememberSaveable(view.goal.id) { mutableStateOf(0) }
    val thisMonth = Budget.monthOf(view.today)
    val first = thisMonth.minus(back, DateTimeUnit.MONTH)
    val colourBlind = LocalColourBlind.current
    val brightness = LocalBrightness.current
    PageCard("Calendar") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { back++ }, enabled = first > Budget.monthOf(view.firstDay), modifier = Modifier.testTag("goal-page-earlier")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Month before")
            }
            Text(
                "${MONTHS[first.monthNumber - 1]} ${first.year}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("goal-page-month"),
            )
            IconButton(onClick = { back-- }, enabled = back > 0, modifier = Modifier.testTag("goal-page-later")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Month after")
            }
        }
        Row {
            (1..7).forEach { Text(weekdayShort(it).take(1), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)) }
        }
        GoalPage.monthCells(first).chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val mark = view.marks[day]
                            val background = when (mark) {
                                GoalDayMark.DONE -> if (colourBlind) Color(ColourBlind.good(brightness)) else MaterialTheme.colorScheme.primary
                                GoalDayMark.SKIPPED -> MaterialTheme.colorScheme.tertiaryContainer
                                GoalDayMark.MISSED -> MaterialTheme.colorScheme.errorContainer
                                null -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val text = when (mark) {
                                GoalDayMark.DONE -> if (colourBlind) Color(ColourBlind.onGood(brightness)) else MaterialTheme.colorScheme.onPrimary
                                GoalDayMark.SKIPPED -> MaterialTheme.colorScheme.onTertiaryContainer
                                GoalDayMark.MISSED -> MaterialTheme.colorScheme.onErrorContainer
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Column(
                                Modifier.fillMaxSize().background(background, RoundedCornerShape(8.dp))
                                    .semantics(mergeDescendants = true) { contentDescription = shortDate(day) + (mark?.let { ": ${it.word}" } ?: "") }
                                    .testTag("goal-day:${day.dayOfMonth}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = text)
                                // A mark as well as a colour, so the days tell apart without colour.
                                if (mark != null) {
                                    Text(mark.mark, style = MaterialTheme.typography.labelSmall, color = text, modifier = Modifier.testTag("goal-day-mark:${day.dayOfMonth}"))
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            GoalDayMark.entries.joinToString(" · ") { "${it.mark} ${it.word}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("goal-page-key"),
        )
    }
}

private fun inARow(times: Int): String = if (times == 1) "1 time" else "$times times in a row"

private fun rateText(rate: GoalRate): String =
    rate.percent?.let { "${rate.done} of ${rate.due} · $it%" } ?: "nothing due"

@Composable
private fun NumberRow(label: String, value: String, tag: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.testTag(tag))
    }
}

/** Runs, how often it got done, and what it has earned. */
@Composable
private fun GoalNumbers(view: GoalPageView) {
    val goal = view.goal
    PageCard("In numbers") {
        val lines = buildList {
            if (goal.kind == GoalKind.HABIT) {
                view.currentRun?.let { add(Triple("Current run", inARow(it), "goal-page:run")) }
                view.bestRun?.let { add(Triple("Best run", inARow(it), "goal-page:best")) }
                view.last30?.let { add(Triple("Done in the last 30 days", rateText(it), "goal-page:30")) }
                view.last90?.let { add(Triple("Done in the last 90 days", rateText(it), "goal-page:90")) }
            }
            add(
                Triple(
                    if (goal.kind == GoalKind.PROJECT) "Steps ticked" else if (goal.atMost) "Days at or under the limit" else "Ticks in all",
                    "${view.ticks}", "goal-page:ticks",
                ),
            )
            add(Triple("${pointsTitle()} earned", "+${view.points}", "goal-page:points"))
            view.amount?.let { amount ->
                add(Triple(if (goal.atMost) "Added in all" else "In all", GoalPage.amountText(amount, goal.unit), "goal-page:amount"))
                if (view.seconds > 0) add(Triple("Timed in all", GoalPage.timeText(view.seconds), "goal-page:time"))
            }
        }
        lines.forEachIndexed { index, (label, value, tag) ->
            if (index > 0) HorizontalDivider()
            NumberRow(label, value, tag)
        }
        if (goal.kind == GoalKind.HABIT) {
            Text(
                "A skipped time is never counted as done, and today counts once it's done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A project's steps, each with the day it was done. */
@Composable
private fun GoalSteps(view: GoalPageView) {
    PageCard("Steps") {
        if (view.steps.isEmpty()) Text("No steps yet. Add some with Edit.", style = MaterialTheme.typography.bodyMedium)
        view.steps.forEach { done ->
            Row(Modifier.testTag("goal-page-step:${done.step.name}")) {
                Text(if (done.day != null) "✓" else "○", modifier = Modifier.padding(end = 8.dp))
                Text(done.step.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    done.day?.let { shortDate(it) } ?: "to do",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val finished = view.finishedOn
        val deadline = view.goal.deadline
        if (finished != null) {
            Text("Finished ${shortDate(finished)}.", fontWeight = FontWeight.Medium, color = goodColor(), modifier = Modifier.testTag("goal-page-finished"))
        } else if (deadline != null) {
            Text("Deadline ${shortDate(deadline)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Its skips and misses, newest first, each with its reason. A tap opens one to put it right. */
@Composable
private fun GoalLapses(view: GoalPageView, onMiss: (Long) -> Unit) {
    PageCard("Skips and misses") {
        if (view.lapses.isEmpty()) Text("None. Nothing skipped or missed.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("goal-page-no-lapses"))
        view.lapses.forEach { lapse ->
            Column(Modifier.fillMaxWidth().clickable { onMiss(lapse.id) }.padding(vertical = 4.dp).testTag("goal-page-lapse:${lapse.day}")) {
                Row {
                    Text(shortDate(lapse.day), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(if (lapse.kind == LapseKind.SKIP) "Skipped" else "Missed", reasonLabel(lapse.reason, lapse.reasonText)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                lapse.note?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (view.lapses.isNotEmpty()) {
            Text(
                "Tap one to change its reason, or to say you forgot to tick it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The notes written on its ticks, newest first. */
@Composable
private fun GoalNotes(view: GoalPageView) {
    PageCard("Notes") {
        if (view.notes.isEmpty()) {
            Text("No notes yet. Add one to a tick with \"Add a note\" on Today.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("goal-page-no-notes"))
        }
        view.notes.forEach { tick ->
            Row(Modifier.padding(vertical = 2.dp).testTag("goal-page-note:${tick.day}")) {
                Text(shortDate(tick.day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                Text("“${tick.note}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
