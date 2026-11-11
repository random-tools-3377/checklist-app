package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Bosses
import io.github.randomtools3377.checklist.core.Quests
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.core.TickResult
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The game extras on the real database: lucky ticks, the boss's health and the day's quests. */
class GameDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val book = Book(RoomStore(db))

    @AfterTest
    fun close() {
        Rules.current = Rules()
        db.close()
    }

    private suspend fun addHabit(name: String, target: Int = 1): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(Bosses.monday(nowLocal().date)), target = target, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null,
            ),
        )
        return dao.activeGoals().first { it.name == name }.id
    }

    @Test
    fun takingALuckyTickBackTakesItsExtraToo() = runTest {
        // Every tick lucky, with a chest of 12.
        dao.putSetting(SettingEntity(Rules.SETTING, Rules.encode(Rules(luckyOneIn = 1, luckyChestMin = 12, luckyChestMax = 12))))
        val id = addHabit("Read")
        assertTrue(book.tickHabit(id) is TickResult.Ticked)
        val luck = assertNotNull(repository.luckyTick(id, book.today(), 5))
        assertEquals(5 + luck.points, dao.balance())
        // Taken back from the card, not the Undo bar: the extra goes with it.
        book.undoHabitTick(id)
        assertEquals(0, dao.balance())
        // Ticked again: the same luck, paid once.
        book.tickHabit(id)
        repository.luckyTick(id, book.today(), 5)
        assertEquals(5 + luck.points, dao.balance())
    }

    @Test
    fun theBossKeepsItsHealthForTheWeek() = runTest {
        addHabit("Run")
        val second = addHabit("Stretch")
        val day = nowLocal().date
        val first = repository.bossView(day).fight.health
        // A goal removed mid-week doesn't make the boss weaker.
        repository.removeGoal(second)
        assertEquals(first, repository.bossView(day).fight.health)
        assertEquals(first, Bosses.healthIn(dao.setting(Bosses.FIGHT), Bosses.monday(day)))
    }

    @Test
    fun aBossBeatenButNeverPaidPaysWhenSeen() = runTest {
        addHabit("Run")
        val day = nowLocal().date
        val monday = Bosses.monday(day)
        dao.putSetting(SettingEntity(Bosses.ITEMS_FROM, "0"))
        // Kept with more damage than health, as could happen before the health was kept.
        dao.putSetting(SettingEntity(Bosses.FIGHT, Bosses.encodeFight(monday, 120, 98)))
        assertTrue(repository.bossView(day).fight.beaten)
        assertEquals(Bosses.REWARD, dao.balance())
        repository.bossView(day)
        assertEquals(Bosses.REWARD, dao.balance(), "paid once")
    }

    @Test
    fun theDaysQuestsStayTheSameWhenGoalsChange() = runTest {
        val day = nowLocal().date
        val goals = listOf(addHabit("Run"), addHabit("Read"), addHabit("Water"), addHabit("Gym")).map { dao.goal(it)!!.toCore() }
        val morning = repository.questsOf(day, goals)
        assertEquals(3, morning.size)
        // A goal added, and the list in another order: the same quests.
        val more = goals.reversed() + dao.goal(addHabit("Walk"))!!.toCore()
        assertEquals(morning, repository.questsOf(day, more))
        assertEquals(morning, Quests.decodeDay(dao.setting(Quests.SETTING_DAY), day))
    }
}
