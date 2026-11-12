package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.SavingFor
import io.github.randomtools3377.checklist.core.Usual
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The reward starred to save for, kept in a setting and read as the shop reads it. */
class SavingForDataTest {
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

    private suspend fun id(name: String) = dao.activeRewards().single { it.name == name }.id
    private suspend fun starred() = SavingFor.starred(dao.setting(SavingFor.SETTING))

    /** 20 points earned on each of the last 4 days: a usual of 20 a day, and a balance of 80. */
    private suspend fun earn() {
        for (back in 1..4) {
            val day = today.minusDays(back)
            dao.insertEntry(
                EntryEntity(at = nowMillis(), day = day.toEpochDay(), kind = "TICK", amount = 20, goalId = null, tickId = null, rewardId = null, purchaseId = null, label = "Read"),
            )
        }
    }

    @Test
    fun thereIsOneStarAndStarringAnotherMovesIt() = runTest {
        repository.saveReward(RewardDraft(id = 0, name = "Bike", icon = "🚲", price = 300))
        repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 50))
        assertNull(starred())
        repository.setSavingFor(id("Bike"), true)
        assertEquals(id("Bike"), starred())
        repository.setSavingFor(id("Film"), true)
        assertEquals(id("Film"), starred())
        // Taking the star off a reward that doesn't have it changes nothing.
        repository.setSavingFor(id("Bike"), false)
        assertEquals(id("Film"), starred())
        repository.setSavingFor(id("Film"), false)
        assertNull(starred())
    }

    @Test
    fun theEditorStarsANewRewardAndKeepsOrTakesOffTheStarOfAnOldOne() = runTest {
        repository.saveReward(RewardDraft(id = 0, name = "Bike", icon = "🚲", price = 300, savingFor = true))
        val bike = id("Bike")
        assertEquals(bike, starred())
        // Saved from somewhere that doesn't say: the star stays.
        repository.saveReward(RewardDraft(id = bike, name = "Bike", icon = "🚲", price = 320))
        assertEquals(bike, starred())
        // A new reward that isn't starred leaves the star alone.
        repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 50, savingFor = false))
        assertEquals(bike, starred())
        repository.saveReward(RewardDraft(id = bike, name = "Bike", icon = "🚲", price = 320, savingFor = false))
        assertNull(starred())
    }

    @Test
    fun theMorningPlanGetsOneShortLineFromTheShopsOwnFigures() = runTest {
        earn()
        repository.saveReward(RewardDraft(id = 0, name = "Bike", icon = "🚲", price = 300, savingFor = true))
        assertEquals(20.0, SavingFors.usualPerDay(dao, today)!!, 0.001)
        val saving = SavingFors.load(dao, today, 0)!!
        assertEquals(80, saving.balance)
        assertEquals(300, saving.reward.price)
        // 220 to go at 20 a day: the same figure the shop shows for that amount.
        assertEquals("about ${Usual.days(220, 20.0)} more days", saving.more)
        assertEquals("Saving for 🚲 Bike: 80 of 300 points, about 11 more days", SavingFors.morningLine(dao, today, 0))
        // In the user's own word for points.
        repository.setSetting(SETTING_POINTS_NAME, "coin|coins|🪙")
        assertEquals("Saving for 🚲 Bike: 80 of 300 coins 🪙, about 11 more days", SavingFors.morningLine(dao, today, 0))
    }

    @Test
    fun aRewardTheBalanceCoversCanBeBoughtNow() = runTest {
        earn()
        repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 50, savingFor = true))
        assertEquals("🎬 Film: you can buy it now", SavingFors.morningLine(dao, today, 0))
    }

    @Test
    fun nothingIsSaidWithNoStarBehindTheLockOrOnceTheRewardIsRemoved() = runTest {
        earn()
        repository.saveReward(RewardDraft(id = 0, name = "Bike", icon = "🚲", price = 300))
        assertNull(SavingFors.morningLine(dao, today, 0))
        repository.setSavingFor(id("Bike"), true)
        repository.setSetting(LOCK_SHOP, "1")
        assertNull(SavingFors.load(dao, today, 0))
        assertNull(SavingFors.morningLine(dao, today, 0))
        repository.setSetting(LOCK_SHOP, "0")
        val bike = id("Bike")
        repository.removeReward(bike)
        assertNull(SavingFors.load(dao, today, 0))
    }
}
