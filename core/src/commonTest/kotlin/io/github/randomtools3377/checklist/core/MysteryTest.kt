package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MysteryTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }

    private suspend fun earn(points: Int) {
        val goal = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = points, repeat = Repeat.everyDay(kotlinx.datetime.LocalDate(2026, 1, 1))))
        book.tickHabit(goal.id)
    }

    @Test
    fun prizesAreTidied() {
        // The first spelling of a repeat is kept.
        assertEquals(listOf("Coffee out", "film night"), Mystery.tidy(listOf("  Coffee   out ", "", "film night", "Film night", "coffee out")))
        assertEquals(Mystery.MAX_PRIZES, Mystery.tidy((1..30).map { "Prize $it" }).size)
        assertNull(Mystery.encode(listOf(" ", "")))
        assertEquals(listOf("A", "B"), Mystery.decode(Mystery.encode(listOf("A", "B"))))
        assertTrue(Mystery.decode(null).isEmpty())
    }

    @Test
    fun buyingAMysteryRewardDrawsAPrizeForTheInventory() = runTest {
        earn(50)
        val box = store.addReward(Reward(name = "Mystery box", icon = "🎁", price = 20, prizes = listOf("Coffee out", "Film night", "New book")))

        val result = book.buy(box.id, Random(7)) as BuyResult.Bought
        val prize = result.prize!!
        assertTrue(prize in box.prizes)
        val purchase = store.purchases.single()
        assertEquals(prize, purchase.name)
        assertEquals(20, purchase.price)
        assertEquals("Mystery box: $prize", store.entries.last().label)
        assertEquals(30, book.balance())

        // Returned, it gives back the box's price.
        assertTrue(book.returnPurchase(purchase.id))
        assertEquals(50, book.balance())
    }

    @Test
    fun overManyDrawsEveryPrizeComesUp() = runTest {
        val random = Random(1)
        val prizes = listOf("A", "B", "C")
        val seen = (1..60).map { Mystery.draw(prizes, random) }.toSet()
        assertEquals(prizes.toSet(), seen)
    }

    @Test
    fun aPlainRewardHasNoPrize() = runTest {
        earn(50)
        val coffee = store.addReward(Reward(name = "Coffee", icon = "☕", price = 5))
        assertNull((book.buy(coffee.id) as BuyResult.Bought).prize)
        assertEquals("Coffee", store.purchases.single().name)
    }
}
