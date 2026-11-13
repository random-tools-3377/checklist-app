package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The character on other screens: kind on the hard ones, a buddy on the timer, a face on the widget, a line on its page. */
class CharacterPlacesTest {
    private val day = LocalDate(2026, 11, 13)
    private val days = (0..6).map { day.plusDays(it) }

    private fun sheet(vararg xp: Pair<SkillArea, Long>) =
        CharacterSheet(xp.sumOf { it.second }, Levels.of(xp.sumOf { it.second }, Levels.CHARACTER_STEP), xp.toMap())

    @Test
    fun theHardScreensAreKindAndNeverBlame() {
        val blame = listOf("should", "fail", "again?", "your fault", "lazy", "bad", "disappoint", "you missed", "you skipped")
        for (scene in listOf(Scene.MISSES, Scene.PENALTY, Scene.GAP, Scene.PUT_RIGHT, Scene.PENALTY_DONE)) {
            for (d in days) {
                val say = Checkins.at(scene, d)
                assertTrue(say.expression in setOf(Expression.CALM, Expression.HAPPY), "${scene.name}: ${say.expression}")
                assertFalse(blame.any { it in say.line.lowercase() }, say.line)
            }
        }
        assertEquals(Expression.CALM, Checkins.at(Scene.MISSES, day).expression)
        assertEquals(Expression.CALM, Checkins.at(Scene.PENALTY, day).expression)
    }

    @Test
    fun itBrightensOnceAMissIsPutRightOrThePenaltyTaskIsDone() {
        for (scene in listOf(Scene.PUT_RIGHT, Scene.PENALTY_DONE)) {
            val say = Checkins.at(scene, day)
            assertEquals(Expression.HAPPY, say.expression)
            assertNotEquals(CharacterMove.NONE, say.move)
        }
        assertNotEquals(Checkins.at(Scene.MISSES, day).line, Checkins.at(Scene.PUT_RIGHT, day).line)
        assertNotEquals(Checkins.at(Scene.PENALTY, day).line, Checkins.at(Scene.PENALTY_DONE, day).line)
    }

    @Test
    fun theFocusBuddyFollowsTheTimer() {
        assertEquals(Scene.FOCUS, Checkins.focusScene(running = true))
        assertEquals(Scene.FOCUS_PAUSED, Checkins.focusScene(running = false))
        assertEquals(Expression.DETERMINED, Checkins.at(Scene.FOCUS, day).expression)
        assertEquals(Expression.CALM, Checkins.at(Scene.FOCUS_PAUSED, day).expression)
        assertEquals(CharacterMove.STRETCH, Checkins.at(Scene.BREAK, day).move)
        assertEquals(Expression.CHEERING, Checkins.at(Scene.FOCUS_DONE, day).expression)
    }

    @Test
    fun aTapOnItsPageSaysHowFarToTheNextLevelThenTheNextGear() {
        // 40 experience: level 1, 10 short of level 2; Fitness has 40 of the 500 its trainers need.
        val sheet = sheet(SkillArea.FITNESS to 40L)
        val toLevel = sheet.level.toNext
        val first = Checkins.pageLine(sheet, 0)
        assertTrue("$toLevel more experience" in first && "level ${sheet.level.level + 1}" in first, first)
        val near = Checkins.gearNear(sheet)!!
        val second = Checkins.pageLine(sheet, 1)
        assertTrue(near.gear.area.label in second && "${near.left}" in second && near.gear.label.lowercase() in second, second)
        // It goes round again.
        assertEquals(first, Checkins.pageLine(sheet, 2))
    }

    @Test
    fun withAllTheGearEarnedItSaysSo() {
        val all = sheet(*SkillArea.entries.map { it to Levels.needed(20, Levels.SKILL_STEP) }.toTypedArray())
        assertEquals(null, Checkins.gearNear(all))
        assertTrue("gear" in Checkins.pageLine(all, 1))
    }

    @Test
    fun theWidgetFaceGoesOnlyByHowMuchIsDone() {
        assertEquals(Expression.CALM, Checkins.widgetFace(done = 0, due = 4))
        assertEquals(Expression.HAPPY, Checkins.widgetFace(done = 1, due = 4))
        assertEquals(Expression.PROUD, Checkins.widgetFace(done = 4, due = 4))
        // Nothing due: calm, never proud of an empty day.
        assertEquals(Expression.CALM, Checkins.widgetFace(done = 0, due = 0))
    }

    @Test
    fun theHeadFitsItsSquareWithAnyHairAndHat() {
        val right = Figure.HEAD_LEFT + Figure.HEAD_SIZE
        val bottom = Figure.HEAD_TOP + Figure.HEAD_SIZE
        val hats = listOf(null, Gear.HEADBAND, Gear.CAP, Gear.BERET)
        for (hair in HairStyle.entries) for (hat in hats) for (face in listOf(Expression.CALM, Expression.HAPPY, Expression.PROUD)) {
            val marks = Figure.head(Look(hair = hair), Outfit(listOfNotNull(hat, Gear.GLASSES, Gear.GLOW), null), face)
            assertTrue(marks.isNotEmpty())
            for (mark in marks) {
                val (l, t, r, b) = when (mark) {
                    is Mark.Circle -> listOf(mark.cx - mark.r, mark.cy - mark.r, mark.cx + mark.r, mark.cy + mark.r)
                    is Mark.Oval -> listOf(mark.cx - mark.rx, mark.cy - mark.ry, mark.cx + mark.rx, mark.cy + mark.ry)
                    // Long hair hangs down behind the shoulders: only its top is in the picture.
                    is Mark.Box -> listOf(mark.x, mark.y, mark.x + mark.w, mark.y)
                    is Mark.Path -> continue
                }
                assertTrue(l >= Figure.HEAD_LEFT && r <= right && t >= Figure.HEAD_TOP && b <= bottom, "$hair $hat $mark")
            }
        }
        // The head is the face and its hair, without the body.
        assertTrue(Figure.head(Look(), Outfit(emptyList(), null)).size < Figure.marks(Look(), Outfit(emptyList(), null)).size)
    }
}
