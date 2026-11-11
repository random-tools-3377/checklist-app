package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.MoneyPrices
import io.github.randomtools3377.checklist.core.Repeat
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rewards whose price follows money, kept in step with the goals in the database. */
class MoneyPricesDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val today get() = nowLocal().date

    @AfterTest
    fun close() = db.close()

    private suspend fun addGoal(name: String, points: Int, target: Int = 1, bonus: Int = 0) = repository.saveHabit(
        HabitDraft(
            id = 0, name = name, icon = "⭐", points = points, repeat = Repeat.everyDay(today), target = target, bonus = bonus,
            minutes = null, at = null, finishBy = null, finishDay = null,
        ),
    )

    private suspend fun price(name: String) = dao.activeRewards().single { it.name == name }.price

    @Test
    fun pricesFollowMoneyAndKeepInStepWithTheGoals() = runTest {
        addGoal("Read", points = 5, target = 2, bonus = 3) // 13 a day, 91 a week
        repository.setSetting(SETTING_TREAT_BUDGET, "13.0") // so 1 of money is 7 points
        repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 1, value = 10.0, followsMoney = true))
        repository.saveReward(RewardDraft(id = 0, name = "Book", icon = "📚", price = 40, value = 10.0))
        repository.keepMoneyPricesInStep(today)
        assertEquals(70, price("Film"))
        assertEquals(40, price("Book")) // its price was set by hand
        assertEquals(7.0, MoneyPrices.decode(dao.setting(MoneyPrices.RATE))!!.pointsPerUnit, 0.0001)

        // A little more to earn: within a tenth, so prices stay put.
        addGoal("Water", points = 1)
        repository.keepMoneyPricesInStep(today)
        assertEquals(70, price("Film"))

        // More than a tenth more: prices move up with it.
        addGoal("Run", points = 2) // 16 a day
        repository.keepMoneyPricesInStep(today)
        assertEquals(85, price("Film")) // 10 × 16 × 7 ÷ 13 = 86.2, rounded to 5
        assertEquals(40, price("Book"))

        // A bigger budget makes each point worth more money.
        repository.setSetting(SETTING_TREAT_BUDGET, "32.0")
        repository.keepMoneyPricesInStep(today)
        assertEquals(35, price("Film"))
    }

    @Test
    fun withoutABudgetPricesStayAsTheyAre() = runTest {
        addGoal("Read", points = 5)
        repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 33, value = 10.0, followsMoney = true))
        repository.keepMoneyPricesInStep(today)
        assertEquals(33, price("Film"))
        assertEquals(null, dao.setting(MoneyPrices.RATE))
    }

    @Test
    fun followingNeedsAMoneyValueAndGoesInTheBackup() = runTest {
        repository.saveReward(RewardDraft(id = 0, name = "Walk", icon = "🌳", price = 10, followsMoney = true))
        assertFalse(dao.activeRewards().single().followsMoney)
        repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 50, value = 10.0, followsMoney = true))
        val text = repository.exportBackup()
        assertTrue(io.github.randomtools3377.checklist.core.Backup.decode(text).rewards.single { it.name == "Film" }.followsMoney)
    }
}
