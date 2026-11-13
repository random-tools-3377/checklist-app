package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A goal's "Why it's good": reminders lead with it and never say what the goal pays. */
class WhyItsGoodTest {
    private val day = LocalDate(2026, 11, 13)
    private val time: (LocalTime) -> String = { "${it.hour}:${it.minute.toString().padStart(2, '0')}" }

    private fun goal(why: String? = "so I sleep better", points: Int = 25, target: Int = 1, minutes: Int? = null, private: Boolean = false) = Goal(
        id = 1, kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = points, repeat = Repeat.everyDay(day.minusDays(3)),
        target = target, minutes = minutes, at = LocalTime(21, 0), why = why, private = private,
    )

    private fun alert(kind: AlertKind = AlertKind.ON_TIME, number: Int = 0, minutesBefore: Int = 0) =
        Alert(1, day, LocalDateTime(day, LocalTime(21, 0)), kind, number = number, minutesBefore = minutesBefore)

    @Test
    fun theReminderLeadsWithTheReasonThenWhenHowLongAndHowMany() {
        assertEquals("so I sleep better · Now", Alerts.goalWords(goal(), alert(), 0, time))
        assertEquals(
            "so I sleep better · Now · the 2nd of 3 · 10 min · 1 of 3",
            Alerts.goalWords(goal(target = 3, minutes = 10), alert(number = 2), 1, time),
        )
        assertEquals("so I sleep better · In 10 min, at 21:00", Alerts.goalWords(goal(), alert(AlertKind.BEFORE, minutesBefore = 10), 0, time))
        // What is at stake comes last.
        assertEquals("so I sleep better · Now · Last one for today", Alerts.goalWords(goal(), alert(), 0, time, stake = "Last one for today"))
    }

    @Test
    fun theReminderNeverSaysWhatTheGoalPays() {
        for (g in listOf(goal(), goal(why = null), goal(target = 3, minutes = 10))) {
            for (kind in listOf(AlertKind.ON_TIME, AlertKind.BEFORE, AlertKind.REMINDER, AlertKind.AGAIN, AlertKind.USUAL, AlertKind.NEXT)) {
                val text = Alerts.goalWords(g, alert(kind, minutesBefore = 5), 1, time)
                assertFalse("+" in text || "25" in text || "point" in text, text)
            }
        }
    }

    @Test
    fun aGoalWithNoReasonReadsAsBeforeWithoutThePoints() {
        assertEquals("Now", Alerts.goalWords(goal(why = null), alert(), 0, time))
        assertEquals("Now · 10 min · 0 of 2", Alerts.goalWords(goal(why = null, target = 2, minutes = 10), alert(), 0, time))
        assertEquals("Still to do · Last one for today", Alerts.goalWords(goal(why = " \n "), alert(AlertKind.AGAIN), 0, time, stake = "Last one for today"))
    }

    @Test
    fun aLockedPrivateGoalsReminderSaysOnlyWhen() {
        val g = goal(why = "so my head is clearer", private = true, minutes = 20)
        val text = Alerts.goalWords(g, alert(), 0, time, stake = "Last one for today", hidden = true)
        assertEquals("Due now", text)
        assertEquals("In 10 min", Alerts.goalWords(g, alert(AlertKind.BEFORE, minutesBefore = 10), 0, time, hidden = true))
        assertFalse("head" in text || "Stretch" in text || "Last one" in text)
    }

    @Test
    fun aProjectsDeadlineReminderLeadsWithItsReasonUnlessLocked() {
        val project = Goal(id = 2, kind = GoalKind.PROJECT, name = "Shelf", icon = "🔨", points = 0, repeat = null, deadline = day, why = "so the books have a home", private = true)
        val step = Step(id = 10, goalId = 2, name = "Buy wood", points = 5, position = 0)
        val from = LocalDateTime(day, LocalTime(0, 0))
        val to = LocalDateTime(day.plusDays(1), LocalTime(0, 0))
        val open = Alerts.deadlineReminders(listOf(project), listOf(step), emptyList(), from, to, LocalTime(8, 0), 4).single()
        assertEquals("so the books have a home · Due today, 1 step left", open.text)
        val locked = Alerts.deadlineReminders(listOf(project), listOf(step), emptyList(), from, to, LocalTime(8, 0), 4, hidePrivate = true).single()
        assertEquals("Due today", locked.text)
        assertFalse("books" in locked.title + locked.text)
    }

    @Test
    fun theReasonIsTheUsersOwnWordsTidied() {
        assertEquals("so I sleep better", Why.tidy("  so I\n  sleep   better "))
        assertNull(Why.tidy("   "))
        assertNull(Why.tidy(null))
        assertEquals(Why.LENGTH, Why.tidy("a".repeat(500))!!.length)
        // Its case and punctuation stay as written; a full stop is added only to end a sentence.
        assertEquals("so I sleep better.", Why.sentence("so I sleep better"))
        assertEquals("To feel calm!", Why.sentence("To feel calm!"))
        assertEquals("For the kids 😊", Why.sentence("For the kids 😊"))
    }

    @Test
    fun theReasonGoesInTheBackupAndAnOlderFileImportsWithoutOne() {
        val backup = Backup(
            exportedAt = 1, goals = listOf(goal()), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
            rewards = emptyList(), purchases = emptyList(), settings = emptyMap(),
        )
        val text = Backup.encode(backup)
        assertTrue("\"why\": \"so I sleep better\"" in text, text)
        assertEquals(backup, Backup.decode(text))
        // A file from 0.71.0 (format 29) has no "why" at all: its goals come in without one.
        val older = text.replace(Regex(",\\s*\"why\": \"so I sleep better\""), "").replace("\"format\": ${Backup.FORMAT}", "\"format\": 29")
        assertFalse("why" in older)
        val read = Backup.decode(older)
        assertEquals(29, read.format)
        assertNull(read.goals.single().why)
        assertEquals("Stretch", read.goals.single().name)
    }
}
