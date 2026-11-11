package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BossesTest {
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun aBossForEachWeekNeverTheSameTwiceRunning() {
        assertEquals(Bosses.of(monday), Bosses.of(monday))
        (0 until 30).forEach { week ->
            assertNotEquals(Bosses.of(monday.plusDays(7 * week)), Bosses.of(monday.plusDays(7 * (week + 1))))
        }
        assertTrue((0 until 30).map { Bosses.of(monday.plusDays(7 * it)) }.toSet().size >= 6, "most bosses turn up")
        assertEquals(monday, Bosses.monday(monday.plusDays(4)))
    }

    @Test
    fun weakAreasHitTwiceAsHard() {
        val couch = Boss.COUCH_LUMP
        assertEquals(20, Bosses.hit(Item.BOLT, couch))
        assertEquals(10, Bosses.hit(Item.SCROLL, couch))
        assertEquals(3 * 20 + 2 * 10, Bosses.damage(mapOf(Item.BOLT to 3, Item.SCROLL to 2), couch))
    }

    @Test
    fun healthIsSetForAGoodWeek() {
        val daily = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 5, repeat = Repeat.everyDay(monday.minusDays(30)))
        val twice = daily.copy(id = 2, name = "Water", target = 2)
        assertEquals(21, Bosses.expectedTicks(listOf(daily, twice), monday))
        assertEquals(21 * 10 * 7 / 10, Bosses.health(21))
        assertEquals(50, Bosses.health(1), "never too easy")
        val fight = BossFight(monday, Boss.FOG, 100, 120)
        assertTrue(fight.beaten)
        assertEquals(0, fight.left)
    }

    @Test
    fun aMonthsTimesAreSpreadOverItsWeeks() {
        // 31 times in December: one a day, so the week holding the 1st asks for 7, not 31.
        val december = Goal(id = 1, kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 5, repeat = Repeat.timesPer(Period.MONTHLY, LocalDate(2026, 11, 1)), target = 31)
        assertEquals(7, Bosses.expectedTicks(listOf(december), LocalDate(2026, 11, 30)))
        assertEquals(7, Bosses.expectedTicks(listOf(december), LocalDate(2026, 12, 7)))
        val weekly = december.copy(id = 2, repeat = Repeat.timesPer(Period.WEEKLY, monday), target = 3)
        assertEquals(3, Bosses.expectedTicks(listOf(weekly), monday))
    }

    @Test
    fun theHealthIsKeptWithTheFight() {
        val kept = Bosses.encodeFight(monday, 40, 147)
        assertEquals(40, Bosses.damageIn(kept, monday))
        assertEquals(147, Bosses.healthIn(kept, monday))
        assertEquals(null, Bosses.healthIn(kept, monday.plusDays(7)), "another week")
        assertEquals(null, Bosses.healthIn("2026-11-09:40", monday), "kept before health was")
        assertEquals(0, Bosses.damageIn(kept, monday.plusDays(7)))
    }

    @Test
    fun everyTickEarnsAnItemFromItsSkill() {
        val run = Goal(id = 1, kind = GoalKind.HABIT, name = "Morning run", icon = "🏃", points = 5, repeat = null)
        val read = Goal(id = 2, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = null)
        fun tick(goalId: Long, at: Long) = Tick(goalId = goalId, stepId = null, at = at, day = monday, period = Period.DAILY, periodStart = monday)
        val ticks = listOf(tick(1, 50), tick(1, 150), tick(1, 160), tick(2, 170))
        // Before items started, ticks don't count; used ones are gone.
        assertEquals(mapOf(Item.BOLT to 1, Item.SCROLL to 1), Bosses.inventory(ticks, listOf(run, read), since = 100, used = mapOf(Item.BOLT to 1)))
        val used = mapOf(Item.BOLT to 3, Item.PAINT to 1)
        assertEquals(used, Bosses.decodeUsed(Bosses.encodeUsed(used)))
        assertEquals(40, Bosses.damageIn("2026-11-09:40", monday))
        assertEquals(0, Bosses.damageIn("2026-11-02:40", monday))
    }

    @Test
    fun aboutOneTickInTenIsLuckyAndLuckNeverTakesAway() {
        val lucks = (0L until 2000L).mapNotNull { Lucky.of(it, 10) }
        assertTrue(lucks.size in 120..280, "about one in ten: ${lucks.size}")
        assertTrue(lucks.all {
            when (it) {
                is Lucky.Luck.Double -> it.points == 10
                is Lucky.Luck.Chest -> it.points in 5..15
            }
        })
        assertEquals(Lucky.of(42, 10), Lucky.of(42, 10), "the same tick is always as lucky")
        assertNotEquals(luckSeed(1, monday, 0), luckSeed(1, monday, 1))
    }
}
