package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodSummaries
import io.github.randomtools3377.checklist.core.Moods
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Review
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.weekdayShort
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/*
 * The review's mood part for a week, a month or a year: a short summary in place of the list of
 * days, with the days and their notes a tap away in the mood journal. The day view keeps the
 * full detail (see MoodCard in ReviewScreen).
 */

/**
 * The stretch from [from] to [to] summed up: the average mood, how it compares with the week,
 * month or year before, and the things noted most. A week also shows a face for each day (the
 * month's are on its calendar, the year's on its months). [moods] reach back over the stretch
 * before. "See the notes" opens the journal on these days.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodSummaryCard(
    moods: List<Mood>,
    from: LocalDate,
    to: LocalDate,
    period: Period,
    onNotes: () -> Unit,
    onMoodGoals: () -> Unit,
    onMoodJournal: () -> Unit,
) {
    val (beforeFrom, beforeTo) = Review.range(from.minusDays(1), period)
    val summary = MoodSummaries.of(moods, from, to, beforeFrom, beforeTo)
    ReviewCard("Mood") {
        Column(Modifier.fillMaxWidth().testTag("review-mood-summary"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MoodSummaries.averageLine(summary)?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("review-mood-average"))
            }
            MoodSummaries.compareLine(summary, MoodSummaries.stretch(period))?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("review-mood-compare"))
            }
            MoodSummaries.topLine(summary)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("review-mood-top"))
            }
            if (period == Period.WEEKLY) WeekFaces(moods, from)
        }
        FlowRow(verticalArrangement = Arrangement.Center) {
            TextButton(onClick = onNotes, modifier = Modifier.testTag("review-mood-notes")) { Text("See the notes") }
            TextButton(onClick = onMoodJournal, modifier = Modifier.testTag("review-mood-journal")) { Text("Mood journal") }
            TextButton(onClick = onMoodGoals, modifier = Modifier.testTag("review-mood-goals")) { Text("Mood and your goals") }
        }
    }
}

/** The week's seven days, each with its mood's face, or a dash on a day without one. */
@Composable
private fun WeekFaces(moods: List<Mood>, monday: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("review-mood-week")) {
        (0..6).forEach { index ->
            val day = monday.plusDays(index)
            val mood = moods.firstOrNull { it.day == day }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = weekdayShort(day.dayOfWeek.isoDayNumber) + ": " + (mood?.let { Moods.spoken(it.last) } ?: "no mood")
                },
            ) {
                Text(weekdayShort(day.dayOfWeek.isoDayNumber).take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(mood?.let { Moods.face(it.last) } ?: "–", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The mood part while moods are behind the lock: it says so, with a way to unlock, instead of going missing. */
@Composable
internal fun LockedMoodCard(onUnlock: () -> Unit) {
    ReviewCard("Mood") {
        Column(Modifier.fillMaxWidth().testTag("review-moods-locked"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🔒 Your moods are locked.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onUnlock, modifier = Modifier.testTag("review-mood-unlock")) { Text("Unlock") }
        }
    }
}

/** Each day's mood as a face, for the month calendar: the later one when given. */
internal fun moodFacesByDay(moods: List<Mood>): Map<LocalDate, String> = moods.associate { it.day to Moods.face(it.last) }
