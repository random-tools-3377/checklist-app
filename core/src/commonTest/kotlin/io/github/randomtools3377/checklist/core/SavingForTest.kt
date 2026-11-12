package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one days-of-earning figure, and the reward starred to save for. */
class SavingForTest {
    private val bike = Reward(id = 3, name = "Bike", icon = "🚲", price = 300)
    private val film = Reward(id = 4, name = "Film", icon = "🎬", price = 50)

    @Test
    fun oneFigureSaysHowManyDaysAPriceTakes() {
        assertEquals(4, Usual.days(80, 20.0))
        assertEquals(1, Usual.days(18, 20.0))
        assertEquals(0, Usual.days(10, 20.0)) // less than a day
        assertEquals(3, Usual.days(50, 20.0)) // 2.5 rounds up
        assertNull(Usual.days(80, null))
        assertNull(Usual.days(80, 0.0))
        assertNull(Usual.days(0, 20.0))
    }

    @Test
    fun everyWordingOfTheSamePriceShowsTheSameNumber() {
        for (price in listOf(1, 10, 15, 29, 30, 31, 50, 70, 299, 1000)) {
            val days = Usual.days(price, 20.0)!!
            val number = if (days == 0) "less than a day" else if (days == 1) "1 day" else "$days days"
            assertEquals(true, Usual.describe(price, 20.0)!!.contains(number), "describe $price")
            assertEquals(true, Usual.short(price, 20.0)!!.contains(number), "short $price")
        }
        assertEquals("≈ 4 days", Usual.short(80, 20.0))
        assertEquals("≈ 1 day", Usual.short(20, 20.0))
        assertEquals("less than a day", Usual.short(5, 20.0))
        assertNull(Usual.short(80, null))
    }

    @Test
    fun whatIsStillMissingIsSaidInMoreDays() {
        assertEquals("about 9 more days", Usual.more(180, 20.0))
        assertEquals("about 1 more day", Usual.more(20, 20.0))
        assertEquals("less than a day more", Usual.more(5, 20.0))
        assertNull(Usual.more(0, 20.0))
        assertNull(Usual.more(180, null))
    }

    @Test
    fun theStarredRewardIsFoundFromTheSetting() {
        val rewards = listOf(bike, film)
        assertEquals(bike, SavingFor.of("3", rewards, 120, 20.0)?.reward)
        assertEquals(film, SavingFor.of("4", rewards, 120, 20.0)?.reward)
        assertNull(SavingFor.of(null, rewards, 120, 20.0))
        assertNull(SavingFor.of("", rewards, 120, 20.0))
        assertNull(SavingFor.of("9", rewards, 120, 20.0)) // it left the shop
        assertNull(SavingFor.of("3", listOf(bike.copy(archived = true)), 120, 20.0))
    }

    @Test
    fun savingShowsHowFarAlongAndHowManyMoreDays() {
        val saving = SavingFor(bike, balance = 120, perDay = 20.0)
        assertFalse(saving.canBuy)
        assertEquals(120, saving.have)
        assertEquals(180, saving.left)
        assertEquals(0.4f, saving.progress, 0.001f)
        assertEquals("about 9 more days", saving.more)
        assertEquals("about 9 more days at your usual pace", saving.pace)
        assertEquals("120 of 300 points", saving.count(PointsName.DEFAULT))
        assertEquals("120 of 300 coins 🪙", saving.count(PointsName("coin", "coins", "🪙")))
        assertEquals("Saving for 🚲 Bike: 120 of 300 points, about 9 more days", saving.morningLine(PointsName.DEFAULT))
        // The days still to go are the shop's figure for the same amount.
        assertEquals(Usual.days(180, 20.0), 9)
    }

    @Test
    fun withNoUsualYetThereIsNoPace() {
        val saving = SavingFor(bike, balance = 120, perDay = null)
        assertNull(saving.pace)
        assertEquals("Saving for 🚲 Bike: 120 of 300 points", saving.morningLine(PointsName.DEFAULT))
    }

    @Test
    fun onceTheBalanceCoversItItCanBeBought() {
        val saving = SavingFor(film, balance = 50, perDay = 20.0)
        assertTrue(saving.canBuy)
        assertEquals(1f, saving.progress)
        assertEquals(0, saving.left)
        assertNull(saving.pace)
        assertEquals("🎬 Film: you can buy it now", saving.morningLine(PointsName.DEFAULT))
    }

    @Test
    fun belowZeroNothingCountsTowardItAndMoreIsMissing() {
        val saving = SavingFor(film, balance = -30, perDay = 20.0)
        assertEquals(0, saving.have)
        assertEquals(80, saving.left)
        assertEquals(0f, saving.progress)
        assertEquals("about 4 more days", saving.more)
    }
}
