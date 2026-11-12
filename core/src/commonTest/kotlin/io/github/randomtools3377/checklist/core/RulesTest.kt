package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesTest {
    private val start = LocalDate(2026, 1, 1)

    @AfterTest
    fun putTheUsualRulesBack() {
        Rules.current = Rules()
    }

    private fun habit(store: MemoryStore, points: Int = 10, allowance: Int? = null) = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = points, repeat = Repeat.everyDay(start), allowance = allowance),
    )

    /** Lucky ticks as they were worked out before the numbers could change. */
    private fun luckBefore(seed: Long, points: Int): Lucky.Luck? {
        val random = Random(seed * 7919 + 13)
        if (random.nextInt(10) != 0) return null
        return if (random.nextBoolean() && points > 0) Lucky.Luck.Double(points) else Lucky.Luck.Chest(5 + random.nextInt(11))
    }

    @Test
    fun theUsualRulesAreTheNumbersFromBefore() {
        val lapses = Lapses(MemoryStore(), Book(MemoryStore()))
        assertEquals(5, lapses.cost(Goal(kind = GoalKind.HABIT, name = "a", icon = "a", points = 11, repeat = null)))
        assertEquals(4, lapses.cost(Goal(kind = GoalKind.PROJECT, name = "a", icon = "a", points = 0, repeat = null, bonus = 9)))
        assertEquals(-2, lapses.cost(Goal(kind = GoalKind.HABIT, name = "a", icon = "a", points = -5, repeat = null)))

        assertEquals(listOf(200, 150, 100, 75, 50), Difficulty.entries.map { it.percent })
        assertEquals(listOf(3, 2, 1, 0, 0), Difficulty.entries.map { it.allowed(1) })
        assertEquals(0, Difficulty.VERY_HARD.allowed(999))
        assertEquals(listOf(2, 5, 10, 20), Effort.entries.map { it.points })
        assertEquals(listOf(1, 3, 14, 42), RewardSize.entries.map { it.days })
        assertEquals(13, Suggest.points(Effort.HARD, minutes = 39))

        assertEquals(150, Boost.PERCENT)
        assertEquals(2, Boost.MIN_MISSES)
        assertEquals(listOf(100, 110, 110, 120, 120, 130), listOf(6, 7, 29, 30, 99, 100).map { StreakBonus.percentFor(it) })
        assertEquals(25, RepeatPrice.RISE_PERCENT)
        assertEquals(150, RepeatPrice.now(100, 2))
        assertEquals(22, payFor(10, Difficulty.EASY, boosted = true)) // 10 × 1.5 × 1.5, rounded down

        assertEquals(5, Quests.REWARD)
        assertEquals(10, Quests.BIG_REWARD)
        assertEquals(2, Combos.PER_STEP)
        assertEquals(10, Combos.MOST)
        assertEquals(20, Combos.pay(12))

        assertEquals(10, Bosses.HIT)
        assertEquals(25, Bosses.REWARD)
        assertEquals(50, Bosses.health(0))
        assertEquals(700, Bosses.health(100))
        assertEquals(20, Bosses.hit(Item.BOLT, Boss.COUCH_LUMP))
        assertEquals(10, Bosses.hit(Item.SCROLL, Boss.COUCH_LUMP))
        for (seed in 0L until 3000L) assertEquals(luckBefore(seed, 4), Lucky.of(seed, 4))

        assertEquals(30, Shields.EVERY)
        assertEquals(2, Shields.MOST)
        assertEquals(10, Paths.BONUS_PERCENT)

        assertEquals(listOf(3, 5, 25, 20), listOf(MoneyGame.GOOD_DAY, MoneyGame.NO_SPEND_DAY, MoneyGame.MONTH_BONUS, MoneyGame.SAVED_UP))
        assertEquals(0.10, MoneyPrices.DRIFT)
        assertEquals(0.5, BalanceCheck.CHEAP_DAYS)
        assertEquals(60.0, BalanceCheck.FAR_DAYS)
        assertEquals(0.95, BalanceCheck.EASY_SHARE)
        assertEquals(0.5, BalanceCheck.HARD_SHARE)
    }

    @Test
    fun rulesGoThroughTextAndBack() {
        val changed = Rules(penaltyPercent = 100, veryHardNoFreeSkips = 0, luckyChestMax = 30, shieldsMost = 3, minutesPerPoint = 0)
        assertEquals(changed, Rules.decode(Rules.encode(changed)))
        assertEquals("{}", Rules.encode(Rules()), "only what differs is kept")
        assertEquals(Rules(), Rules.decode(Rules.encode(Rules())))
    }

    @Test
    fun unknownAndMissingFieldsAndBadTextAreHandled() {
        assertEquals(Rules(penaltyPercent = 100), Rules.decode("""{"penaltyPercent":100,"somethingFromLater":5}"""))
        assertEquals(Rules(), Rules.decode(null))
        assertEquals(Rules(), Rules.decode(""))
        assertEquals(Rules(), Rules.decode("not rules"))
        assertEquals(Rules(), Rules.decode("[1, 2]"))
        assertEquals(Rules(), Rules.decode("""{"penaltyPercent":"lots"}"""))
    }

    @Test
    fun theBetNumbersStoredBeforeBetsWereRemovedAreLeftOut() {
        val stored = """{"penaltyPercent":100,"betStake1":5,"betStake2":15,"betStake3":75,"betWinTimes":3,"betsMostOpen":1,"shieldsMost":4}"""
        val rules = Rules.decode(stored)
        assertEquals(Rules(penaltyPercent = 100, shieldsMost = 4), rules)
        assertEquals("""{"penaltyPercent":100,"shieldsMost":4}""", Rules.encode(rules), "saved again without them")
        assertTrue(Rules.FIELDS.none { it.key.startsWith("bet") }, "no bet numbers on the Points and rules page")
        assertEquals(listOf("shieldEveryDays", "shieldsMost"), Rules.FIELDS.filter { it.group == "Streak shields" }.map { it.key })
    }

    @Test
    fun numbersOutOfRangeAreBroughtBack() {
        assertEquals(Rules(), Rules().sane())
        val wild = Rules(
            penaltyPercent = 5000, luckyOneIn = 0, comboPerDay = -3, luckyChestMin = 20, luckyChestMax = 10,
            shieldsMost = -1, shieldEveryDays = 0, veryHardNoFreeSkips = 7,
        ).sane()
        assertEquals(1000, wild.penaltyPercent)
        assertEquals(1, wild.luckyOneIn)
        assertEquals(0, wild.comboPerDay)
        assertEquals(20, wild.luckyChestMin)
        assertEquals(20, wild.luckyChestMax)
        assertEquals(0, wild.shieldsMost)
        assertEquals(1, wild.shieldEveryDays)
        assertEquals(1, wild.veryHardNoFreeSkips)
        assertEquals(1, Rules.decode("""{"luckyOneIn":0}""").luckyOneIn)
    }

    @Test
    fun everyRuleIsOnTheSettingsPageOnceUnderItsOwnName() {
        val names = (0 until Rules.serializer().descriptor.elementsCount).map { Rules.serializer().descriptor.getElementName(it) }
        assertEquals(names.toSet(), Rules.FIELDS.map { it.key }.toSet())
        assertEquals(Rules.FIELDS.size, Rules.FIELDS.map { it.key }.distinct().size)
        for (field in Rules.FIELDS) {
            val usual = field.get(Rules())
            assertTrue(usual in field.min..field.max, field.key)
            assertTrue(field.label.isNotBlank() && field.group.isNotBlank(), field.key)
            val other = if (usual < field.max) usual + 1 else usual - 1
            val changed = field.set(Rules(), other)
            assertEquals(other, field.get(changed), field.key)
            assertEquals("{\"${field.key}\":$other}", Rules.encode(changed), "only ${field.key} changes")
            assertEquals(field.max, field.get(field.set(Rules(), field.max + 1)), "kept in range")
        }
    }

    @Test
    fun aRuleChangedChangesWhatHappens() {
        Rules.current = Rules(normalPayPercent = 300, veryHardNoFreeSkips = 0, goodMoneyDay = 7, streakBonus1Days = 3, streakBonus1Percent = 5)
        assertEquals(15, Difficulty.NORMAL.pay(5))
        assertEquals(4, Difficulty.VERY_HARD.allowed(5))
        assertEquals(105, StreakBonus.percentFor(3))
        assertEquals(7, MoneyGame.GOOD_DAY)
        val paid = MoneyGame.settle(LocalDate(2026, 11, 2), LocalDate(2026, 11, 2), { 100.0 }, listOf(MoneyEntry(kind = MoneyKind.SPENT, cents = 500, day = LocalDate(2026, 11, 2))), emptyList())
        assertEquals(listOf(7), paid.map { it.points })

        Rules.current = Rules(luckyOneIn = 1, luckyChestMin = 7, luckyChestMax = 7)
        assertTrue((0L until 50L).all { Lucky.of(it, 0) == Lucky.Luck.Chest(7) })
    }

    @Test
    fun aMissOverTheLimitCostsWhatTheStoredRuleSays() = runTest {
        val store = MemoryStore()
        store.putSetting(Rules.SETTING, Rules.encode(Rules(penaltyPercent = 100)))
        val clock = TestClock(LocalDateTime(2026, 11, 11, 10, 0))
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val lapses = Lapses(store, book, Random(1))
        val run = habit(store, points = 11, allowance = 0)

        lapses.closeDays() // the first check only notes where to start
        clock.set(LocalDateTime(2026, 11, 12, 10, 0))
        val found = lapses.closeDays()

        assertEquals(listOf(true), found.map { it.overLimit })
        assertEquals(-11, book.balance(), "the full points, not half")
        assertEquals(100, Rules.current.penaltyPercent, "loaded from the store")
        assertEquals(SkipCheck.Allowed(used = 1, allowed = 0, overLimit = true, cost = 11), lapses.checkSkip(run.id))
    }

    @Test
    fun aTickPaysByTheStoredRulesEvenWhenOthersWereInUse() = runTest {
        val store = MemoryStore()
        store.putSetting(Rules.SETTING, Rules.encode(Rules(normalPayPercent = 300)))
        val book = Book(store, TestClock(LocalDateTime(2026, 11, 11, 10, 0))) { kotlinx.datetime.TimeZone.UTC }
        val run = habit(store, points = 10)
        Rules.current = Rules() // as in a process just woken by a notification

        assertEquals(TickResult.Ticked(points = 30, bonus = 0, finished = true), book.tickHabit(run.id))
        assertEquals(30, book.balance())

        // Back to the usual rules: the next tick pays as usual, and what was paid stays.
        store.putSetting(Rules.SETTING, Rules.encode(Rules()))
        assertTrue(book.undoHabitTick(run.id))
        assertEquals(0, book.balance())
        book.tickHabit(run.id)
        assertEquals(10, book.balance())
    }
}
