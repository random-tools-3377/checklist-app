package io.github.randomtools3377.checklist.ui

import kotlinx.coroutines.flow.mapLatest
import io.github.randomtools3377.checklist.data.SETTING_CALENDAR_IDS
import io.github.randomtools3377.checklist.data.SETTING_CALENDAR
import io.github.randomtools3377.checklist.calendar.PhoneCalendarInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.BackupError
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.YearReview
import io.github.randomtools3377.checklist.core.Effort
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.StreakBonus
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodFactors
import io.github.randomtools3377.checklist.core.MoodFindings
import io.github.randomtools3377.checklist.core.MoodInsights
import io.github.randomtools3377.checklist.core.ChallengeProgress
import io.github.randomtools3377.checklist.core.Challenges
import io.github.randomtools3377.checklist.core.Quit
import io.github.randomtools3377.checklist.core.Check
import io.github.randomtools3377.checklist.core.BusyTime
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.payFor
import io.github.randomtools3377.checklist.core.PerfectDays
import io.github.randomtools3377.checklist.core.RepeatPrice
import io.github.randomtools3377.checklist.core.Boost
import io.github.randomtools3377.checklist.core.Interest
import io.github.randomtools3377.checklist.core.GoalCheck
import io.github.randomtools3377.checklist.core.RewardCheck
import io.github.randomtools3377.checklist.core.BalanceCheck
import io.github.randomtools3377.checklist.core.Suggest
import io.github.randomtools3377.checklist.core.Ramp
import io.github.randomtools3377.checklist.core.DifficultySetting
import io.github.randomtools3377.checklist.core.Difficulty
import io.github.randomtools3377.checklist.core.DoneItem
import io.github.randomtools3377.checklist.core.DoneList
import io.github.randomtools3377.checklist.data.SETTING_STOPWATCH
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.data.SETTING_WIDGET_STYLE
import io.github.randomtools3377.checklist.data.SETTING_APP_ICON
import io.github.randomtools3377.checklist.widget.WidgetStyle
import io.github.randomtools3377.checklist.AppIcon
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_GIF
import io.github.randomtools3377.checklist.data.SETTING_CONFETTI
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_BUZZ
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_SOUND
import io.github.randomtools3377.checklist.data.SETTING_FONT
import io.github.randomtools3377.checklist.data.SETTING_POINTS_NAME
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.PointsName
import io.github.randomtools3377.checklist.data.SETTING_FIRST_TAB
import io.github.randomtools3377.checklist.data.SETTING_HIDDEN_TABS
import io.github.randomtools3377.checklist.data.SETTING_TODAY_SECTIONS
import io.github.randomtools3377.checklist.core.AppTabKind
import io.github.randomtools3377.checklist.core.TabChoice
import io.github.randomtools3377.checklist.core.Sections
import io.github.randomtools3377.checklist.core.SectionChoice
import io.github.randomtools3377.checklist.data.SETTING_PHOTO_STAMP
import io.github.randomtools3377.checklist.data.SETTING_BACKDROP_DIM
import io.github.randomtools3377.checklist.data.SETTING_BACKDROP
import io.github.randomtools3377.checklist.data.SETTING_TODAY_LAYOUT
import io.github.randomtools3377.checklist.data.SETTING_CARD_SIZE
import io.github.randomtools3377.checklist.data.SETTING_TEXT_SIZE
import io.github.randomtools3377.checklist.data.SETTING_RECENT_COLORS
import io.github.randomtools3377.checklist.data.SETTING_COLOR
import io.github.randomtools3377.checklist.data.SETTING_THEME
import io.github.randomtools3377.checklist.core.Colors
import kotlinx.coroutines.flow.collectLatest
import io.github.randomtools3377.checklist.data.SETTING_BADGES_SEEN
import io.github.randomtools3377.checklist.data.SETTING_BADGES
import io.github.randomtools3377.checklist.data.SETTING_WEEKLY_TARGET
import io.github.randomtools3377.checklist.core.WeeklyTarget
import io.github.randomtools3377.checklist.core.WeekProgress
import io.github.randomtools3377.checklist.core.Badges
import io.github.randomtools3377.checklist.core.Badge
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.LongPace
import io.github.randomtools3377.checklist.core.Order
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.Planned
import io.github.randomtools3377.checklist.core.Purchase
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.TodayPace
import io.github.randomtools3377.checklist.core.TodayView
import io.github.randomtools3377.checklist.core.Usual
import io.github.randomtools3377.checklist.core.countsToward
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.usualMinuteOfDay
import io.github.randomtools3377.checklist.core.Gap
import io.github.randomtools3377.checklist.core.GapSettings
import io.github.randomtools3377.checklist.core.Gaps
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Review
import io.github.randomtools3377.checklist.core.ReviewSummary
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.Penalty
import io.github.randomtools3377.checklist.core.PenaltyTask
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.Slot
import io.github.randomtools3377.checklist.core.StreakCounter
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.data.LOCK_GAPS
import io.github.randomtools3377.checklist.data.LOCK_HISTORY
import io.github.randomtools3377.checklist.data.LOCK_REASONS
import io.github.randomtools3377.checklist.data.LOCK_REVIEW
import io.github.randomtools3377.checklist.data.LOCK_BUDGET
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.LOCK_SHOP
import io.github.randomtools3377.checklist.data.SETTING_STREAK_TARGET
import io.github.randomtools3377.checklist.data.SETTING_STREAK_VALUE
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.ORDER_OWN
import io.github.randomtools3377.checklist.data.ORDER_RECOMMENDED
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.TodoDraft
import io.github.randomtools3377.checklist.data.SETTING_GUIDE
import io.github.randomtools3377.checklist.data.SETTING_WEEK_PLANNED
import io.github.randomtools3377.checklist.data.SETTING_MOOD_SKIPPED
import io.github.randomtools3377.checklist.data.SETTING_MOOD_AFTER_CLOSED
import io.github.randomtools3377.checklist.data.SETTING_MOOD_MORE_CLOSED
import io.github.randomtools3377.checklist.data.SETTING_MOOD
import io.github.randomtools3377.checklist.data.SETTING_TREAT_BUDGET
import io.github.randomtools3377.checklist.core.MoneyPrices
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.SavingProgress
import io.github.randomtools3377.checklist.core.MoneyGame
import io.github.randomtools3377.checklist.core.MoneyCategory
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.Pay
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.WorkDay
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.DEFAULT_SNOOZE_MINUTES
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_OWN_REASONS
import io.github.randomtools3377.checklist.data.SETTING_SNOOZE
import io.github.randomtools3377.checklist.data.SETTING_WEEKLY
import io.github.randomtools3377.checklist.reminders.FocusState
import io.github.randomtools3377.checklist.core.BackupReminder
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SETTING_MORNING_PLAN
import io.github.randomtools3377.checklist.data.SETTING_TODAY_ORDER
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.isoDayNumber
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Every goal, for the Goals screen. */
data class GoalsView(val goals: List<Goal>, val steps: Map<Long, List<Step>>, val hiddenPrivate: Int = 0, val today: LocalDate? = null)

/** What the database gives the Today screen, before the lock and the streaks are applied. */
private data class Raw(val goals: List<Goal>, val steps: List<Step>, val ticks: List<Tick>, val balance: Int, val usual: Map<Long, Int>)

/** How far back streaks are counted. */
private const val STREAK_DAYS = 400

/** How many days the planner shows, today included. */
private const val PLAN_DAYS = 14

/** How many recently picked colours the colour picker offers again. */
private const val MAX_RECENT_COLORS = 6

/** The most points a week the target can ask for. */
private const val MAX_WEEKLY_TARGET = 100_000

/** How long to wait after a change before looking for new badges, so a run of changes is looked at once. */
private const val BADGE_CHECK_DELAY_MS = 800L

/** A reward in the shop at what it costs now; [usualPrice] is its own price, before repeat buys this week. */
data class RewardRow(val reward: Reward, val usual: String?, val usualPrice: Int = reward.price, val boughtThisWeek: Int = 0)

data class ShopView(val balance: Int, val rewards: List<RewardRow>, val bought: List<Purchase>)

/** The character's path, the titles earned, the setting for the title shown, and the title shown. */
data class CharacterTitles(
    val path: io.github.randomtools3377.checklist.core.Path?,
    val earned: Set<io.github.randomtools3377.checklist.core.Title>,
    val setting: String?,
    val shown: String?,
)

/** Everything the Today screen shows. */
/** What the character shows on Today: what it says, whether it talks (check-ins on), and the row it suggests. */
data class CompanionView(
    val say: io.github.randomtools3377.checklist.core.CompanionSay,
    val talks: Boolean,
    val suggestion: HabitRow?,
    /** An area to offer ideas for, on a day with everything done or nothing due. */
    val ideaArea: io.github.randomtools3377.checklist.core.SkillArea? = null,
)

/**
 * The mood check-in once everything due today is done: the morning's mood, and the answer once
 * given (null while it is still asked).
 */
data class MoodAfterView(val morning: Int, val after: Int?)

data class TodayState(
    val view: TodayView,
    val now: LocalDateTime,
    val pace: TodayPace?,
    /** Today's due habits, in the order chosen in [recommended]. */
    val plan: List<Planned>,
    val recommended: Boolean,
    val longPaces: Map<Long, LongPace>,
    val hasDefaultFinish: Boolean,
    val streaks: Streaks,
    val streakSettings: StreakSettings,
    /** Private goals left off the list because the lock is on. */
    val hiddenPrivate: Int,
    /** For habits done several times a day: when the next one is due, by goal. */
    val nextSlots: Map<Long, Slot> = emptyMap(),
    /** "My day starts at" is set, so repetitions can be spread over the day. */
    val hasDayStart: Boolean = false,
    /** Each ticked habit's latest tick in its window, to add a note to. */
    val lastTicks: Map<Long, Tick> = emptyMap(),
    /** The goal missed most lately, which pays 50% more this week (see Boost). */
    val boostedGoalId: Long? = null,
    /** With streak shields on: the shields held and the days they saved. */
    val shields: io.github.randomtools3377.checklist.core.Shielded? = null,
    /** Today's events in the phone's calendar, when it is shown. */
    val events: List<Busy> = emptyList(),
    /** For goals to quit: days in a row avoided. */
    val quitDays: Map<Long, Int> = emptyMap(),
    /** The streak bonus today, as a percentage of what goals pay (100 for none). */
    val streakPercent: Int = 100,
    /** For goals with a challenge: how far it has got. */
    val challenges: Map<Long, ChallengeProgress> = emptyMap(),
    /** For the character's check-ins: what was left undone yesterday, and days in a row with nothing done. */
    val leftYesterday: Int = 0,
    val daysAway: Int = 0,
    /** Today is perfect so far, by the same test that pays the perfect-day bonus (see PerfectDays). */
    val perfectSoFar: Boolean = false,
)

/** What Today needs besides goals and ticks. */
private data class Extras(val lapses: List<Lapse>, val checks: List<Check>, val boostOn: Boolean, val events: List<Busy>, val streakBonusOn: Boolean)

/** Something just done, which the Undo bar can take back for a few seconds. */
class Undoable(val message: String, val undo: () -> Unit)

/** Which extras are on: the boost, the perfect-day bonus (0 is off) and repeat-buy prices. */
data class Boosts(val boost: Boolean = false, val perfectDayBonus: Int = 0, val repeatPrice: Boolean = false)

/** What the balance check shows: usual earnings now, and each reward and goal judged. */
data class BalanceCheckView(val perDay: Double?, val rate: Difficulty, val rewards: List<RewardCheck>, val goals: List<GoalCheck>)

/** A Start and Stop timing of a goal with an amount, running since [startedAt] (epoch milliseconds). */
data class Stopwatch(val goalId: Long, val startedAt: Long)

/** What the privacy lock covers, as chosen in Settings. */
/** Today in the budget game: its share, what's spent, and good money days in a row with and without today. */
data class MoneyGameView(val share: Double?, val spentCents: Long, val streak: Int, val streakBefore: Int)

/** First-time tips: whether they're on, and the guides (by name) already shown. */
data class TipsState(val on: Boolean, val seen: Set<String>)

/** The Budget tab's month: [first] is its first day. */
data class BudgetView(
    val first: LocalDate,
    val thisMonth: Boolean,
    val plan: PayPlan?,
    val changes: Map<LocalDate, WorkDay>,
    val entries: List<MoneyEntry>,
    val categories: List<MoneyCategory>,
)

data class Locks(
    val history: Boolean = false,
    val shop: Boolean = false,
    val privateGoals: Boolean = false,
    val reasons: Boolean = false,
    val gaps: Boolean = false,
    val review: Boolean = false,
    val budget: Boolean = false,
) {
    /** Whether anything at all is behind the lock. */
    val anyOn: Boolean get() = history || shop || privateGoals || reasons || gaps || review || budget
}

data class Settings(
    val dayEndHour: Int,
    val defaultFinish: LocalTime?,
    val order: String,
    /** When the user's day starts, if set ("My day starts at"). */
    val dayStart: LocalTime? = null,
    /** The morning plan notification, on unless turned off. It needs [dayStart]. */
    val morningPlan: Boolean = true,
    val streak: StreakSettings = StreakSettings(),
    val locks: Locks = Locks(),
    val gaps: GapSettings = GapSettings(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val phone: Phone) : ViewModel() {
    private val parts = phone.parts
    private val dao = parts.db.dao()
    private val book = parts.book
    private val repository = parts.repository

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<Settings> = combine(
        combine(
            dao.observeSetting(SETTING_DAY_END_HOUR),
            dao.observeSetting(SETTING_DEFAULT_FINISH),
            dao.observeSetting(SETTING_TODAY_ORDER),
            dao.observeSetting(SETTING_DAY_START),
            dao.observeSetting(SETTING_MORNING_PLAN),
        ) { hour, finish, order, start, morning ->
            Settings(
                dayEndHour = parseDayEndHour(hour),
                defaultFinish = finish?.toIntOrNull()?.toTime(),
                order = if (order == ORDER_OWN) ORDER_OWN else ORDER_RECOMMENDED,
                dayStart = start?.toIntOrNull()?.toTime(),
                morningPlan = morning != "0",
            )
        },
        combine(dao.observeSetting(SETTING_STREAK_TARGET), dao.observeSetting(SETTING_STREAK_VALUE)) { target, value ->
            val defaults = StreakSettings()
            StreakSettings(
                target = target?.let { runCatching { StreakTarget.valueOf(it) }.getOrNull() } ?: defaults.target,
                value = value?.toIntOrNull() ?: defaults.value,
            )
        },
        combine(
            dao.observeSetting(LOCK_HISTORY),
            dao.observeSetting(LOCK_SHOP),
            dao.observeSetting(LOCK_PRIVATE),
            dao.observeSetting(LOCK_REASONS),
            combine(dao.observeSetting(LOCK_GAPS), dao.observeSetting(LOCK_REVIEW), dao.observeSetting(LOCK_BUDGET)) { g, r, b -> Triple(g, r, b) },
        ) { h, sh, p, r, (g, rv, b) ->
            Locks(history = h == "1", shop = sh == "1", privateGoals = p == "1", reasons = r == "1", gaps = g == "1", review = rv == "1", budget = b == "1")
        },
        combine(dao.observeSetting(Gaps.SETTING_DAYS), dao.observeSetting(Gaps.SETTING_POINTS)) { days, points ->
            val defaults = GapSettings()
            GapSettings(days = days?.toIntOrNull() ?: defaults.days, points = points?.toIntOrNull() ?: defaults.points)
        },
    ) { basics, streak, locks, gapSettings ->
        basics.copy(streak = streak, locks = locks, gaps = gapSettings)
    }.state(Settings(0, null, ORDER_RECOMMENDED))

    /** True from unlocking until the user leaves the app. */
    val unlocked = MutableStateFlow(false)

    /** A goal to skip, asked for from a notification whose skip would go over the limit. */
    val skipRequest = MutableStateFlow<Long?>(null)

    fun requestSkip(goalId: Long) {
        skipRequest.value = goalId
    }

    fun clearSkipRequest() {
        skipRequest.value = null
    }

    fun markUnlocked() {
        unlocked.value = true
    }

    fun lockAgain() {
        unlocked.value = false
    }

    /** True while the lock is on for goals marked private and they aren't unlocked. */
    private val hidingPrivate: Flow<Boolean> = combine(settings, unlocked) { s, open -> s.locks.privateGoals && !open }.distinctUntilChanged()

    /** The goals marked private, removed ones too, to leave out or unname while they are locked. */
    private val privateIds: Flow<Set<Long>> = combine(dao.observeGoals(), dao.observeRemovedGoals()) { shown, removed ->
        io.github.randomtools3377.checklist.core.PrivateGoals.ids((shown + removed).map { it.toCore() })
    }
    val privateGoalIds: StateFlow<Set<Long>> = privateIds.state(emptySet())

    /** The phone's time, checked every half minute, so pace and the day turn over on their own. */
    private val now: Flow<LocalDateTime> = flow {
        while (true) {
            val moment = nowLocal()
            emit(LocalDateTime(moment.date, LocalTime(moment.hour, moment.minute)))
            delay(30_000)
        }
    }.distinctUntilChanged().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The user's current day. */
    private val today: Flow<LocalDate> =
        combine(now, settings) { moment, s -> Days.dayOf(moment, s.dayEndHour) }.distinctUntilChanged()

    // The phone's calendar, read on the phone once the user turns it on (see PhoneCalendar).

    /** Read again each time the app comes to the front, since the calendar can change meanwhile. */
    private val calendarRefresh = MutableStateFlow(0)

    /** Whether the phone's calendar is shown, and from which calendars (all when empty). */
    val calendarChoice: StateFlow<Pair<Boolean, Set<Long>>> =
        combine(dao.observeSetting(SETTING_CALENDAR), dao.observeSetting(SETTING_CALENDAR_IDS)) { on, ids ->
            (on == "1") to ids.orEmpty().split(",").mapNotNull { it.toLongOrNull() }.toSet()
        }.state(false to emptySet())

    /** Events from today on, for the planner's two weeks. Empty while the calendar is off. */
    val calendarEvents: StateFlow<List<Busy>> = combine(today, calendarChoice, calendarRefresh) { day, choice, _ -> day to choice }
        .mapLatest { (day, choice) ->
            if (!choice.first) emptyList() else withContext(kotlinx.coroutines.Dispatchers.IO) {
                phone.calendarEvents(choice.second, day, PLAN_DAYS)
            }
        }.state(emptyList())

    fun setCalendarOn(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_CALENDAR, if (on) "1" else "0") }
    }

    fun setCalendarIds(ids: Set<Long>) {
        viewModelScope.launch { repository.setSetting(SETTING_CALENDAR_IDS, ids.joinToString(",")) }
    }

    suspend fun phoneCalendars(): List<PhoneCalendarInfo> = phone.calendars()

    val todayState: StateFlow<TodayState?> = today.flatMapLatest { day ->
        // Streaks look back up to a year and a bit; that also covers every open window.
        val since = minOf(Days.earliestOpenDay(day), day.minusDays(STREAK_DAYS))
        val view = combine(
            dao.observeGoals(),
            dao.observeSteps(),
            dao.observeHabitTicksSince(since.toEpochDay()),
            dao.observeStepTicks(),
            dao.observeBalance(),
        ) { goals, steps, habitTicks, stepTicks, balance ->
            val ticks = (habitTicks + stepTicks).map { it.toCore() }
            val usual = habitTicks
                .filter { it.day >= day.minusDays(Usual.WINDOW_DAYS).toEpochDay() }
                .groupBy { it.goalId }
                .mapValues { (_, list) -> usualMinuteOfDay(list.map { it.at.toLocalDateTime().let { t -> t.hour * 60 + t.minute } }) }
                .filterValues { it != null }
                .mapValues { it.value!! }
            Raw(goals.map { it.toCore() }, steps.map { it.toCore() }, ticks, balance, usual)
        }
        val lapsesFlow = combine(
            dao.observeLapsesSince(since.toEpochDay()).map { rows -> rows.map { it.toCore() } },
            dao.observeChecksSince(since.toEpochDay()).map { rows -> rows.map { it.toCore() } },
            dao.observeSetting(Boost.SETTING),
            calendarEvents,
            dao.observeSetting(StreakBonus.SETTING),
        ) { lapses, checks, boost, events, streakBonus -> Extras(lapses, checks, Feature.BOOST.isOn(boost), events, Feature.STREAK_BONUS.isOn(streakBonus)) }
        combine(view, lapsesFlow, now, settings, unlocked) { raw, (lapses, checks, boostOn, events, streakBonusOn), moment, s, isUnlocked ->
            val busy = BusyTime.on(events, day, s.dayEndHour)
            val hidePrivate = s.locks.privateGoals && !isUnlocked
            val shownGoals = if (hidePrivate) raw.goals.filter { !it.private } else raw.goals
            // Goals with an amount get the time what is left takes, from a typed time or the
            // usual speed learned from their timings, so pacing and the order can plan for it.
            val todayView = TodayList.build(day, shownGoals, raw.steps, raw.ticks, raw.balance, lapses, checks).let { built ->
                val rates = shownGoals.filter { it.hasAmount }.associate { goal ->
                    goal.id to Amounts.minutesPerUnit(raw.ticks.filter { it.goalId == goal.id && it.stepId == null })
                }
                fun timed(row: HabitRow) =
                    if (row.goal.hasAmount) row.copy(goal = row.goal.copy(minutes = Amounts.minutesFor(row.goal, row.amountLeft, rates[row.goal.id]))) else row
                built.copy(
                    dueToday = built.dueToday.map(::timed),
                    thisWeek = built.thisWeek.map(::timed),
                    thisMonth = built.thisMonth.map(::timed),
                    thisYear = built.thisYear.map(::timed),
                )
            }
            val usual = raw.usual
            val records = StreakCounter.records(day.minusDays(STREAK_DAYS), day, raw.goals, raw.ticks.filter { it.stepId == null }, lapses)
            // Streak shields, while on, can keep the main streak going.
            val shieldsFrom = if (Feature.SHIELDS.isOn(dao.setting(Feature.SHIELDS.key))) {
                dao.setting(io.github.randomtools3377.checklist.core.Shields.FROM)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: day
            } else {
                null
            }
            val shielded = shieldsFrom?.let { io.github.randomtools3377.checklist.core.Shields.of(records, it, day, s.streak) }
            fun mainStreak(on: LocalDate): Int = shieldsFrom?.let { io.github.randomtools3377.checklist.core.Shields.of(records, it, on, s.streak).main }
                ?: StreakCounter.count(records, on, s.streak).done
            val streaks = StreakCounter.count(records, day, s.streak).let { plain -> shielded?.let { plain.copy(done = it.main) } ?: plain }
            val paceSettings = PaceSettings(s.dayEndHour, s.defaultFinish, s.dayStart)
            val pace = Pace.today(moment, day, todayView.dueToday, paceSettings, busy)
            val recommended = s.order == ORDER_RECOMMENDED
            val plan = if (recommended) {
                Order.recommend(moment, day, todayView.dueToday, paceSettings, usual, pace)
            } else {
                todayView.dueToday.map { row ->
                    val at = row.goal.at?.let { Pace.on(day, it, s.dayEndHour) }
                        ?: Pace.nextSlot(row, day, paceSettings)?.takeIf { it.fixed }?.at
                    Planned(row, at, fixed = at != null, late = at != null && !row.done && at < moment)
                }
            }
            val longPaces = buildMap {
                (todayView.thisWeek + todayView.thisMonth + todayView.thisYear).forEach { row ->
                    Pace.forHabit(row, day)?.let { put(row.goal.id, it) }
                }
                todayView.projects.forEach { project -> Pace.forProject(project, day)?.let { put(project.goal.id, it) } }
            }
            val nextSlots = todayView.dueToday.mapNotNull { row -> Pace.nextSlot(row, day, paceSettings)?.let { row.goal.id to it } }.toMap()
            // Slips only count from the day tracking began, so a new user isn't told off.
            val tracked = dao.setting(io.github.randomtools3377.checklist.core.Lapses.TRACKING_SINCE)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { from -> records.filter { it.day >= from } }
                .orEmpty()
            TodayState(
                todayView, moment, pace, plan, recommended, longPaces, s.defaultFinish != null,
                streaks, s.streak, hiddenPrivate = raw.goals.count { it.private && !it.archived } - shownGoals.count { it.private && !it.archived },
                nextSlots = nextSlots,
                hasDayStart = s.dayStart != null,
                boostedGoalId = if (boostOn) Boost.goalFor(day, raw.goals, lapses)?.id else null,
                events = busy,
                streakPercent = if (streakBonusOn) StreakBonus.percentFor(mainStreak(day.minusDays(1))) else 100,
                shields = shielded,
                quitDays = raw.goals.filter { it.quit && !it.archived }.associate { it.id to Quit.daysWithout(it, raw.ticks, day) },
                challenges = raw.goals.filter { it.challenge != null && !it.archived }.mapNotNull { goal ->
                    Challenges.progress(goal, raw.ticks, lapses, emptyList(), day)?.let { goal.id to it }
                }.toMap(),
                lastTicks = (todayView.dueToday + todayView.thisWeek + todayView.thisMonth + todayView.thisYear).mapNotNull { row ->
                    raw.ticks.filter { it.stepId == null && it.countsToward(row.goal, row.window.start) }.maxByOrNull { it.at }?.let { row.goal.id to it }
                }.toMap(),
                leftYesterday = io.github.randomtools3377.checklist.core.Checkins.leftYesterday(tracked, day),
                daysAway = io.github.randomtools3377.checklist.core.Checkins.daysAway(tracked, day),
                perfectSoFar = records.lastOrNull()?.takeIf { it.day == day }?.let(PerfectDays::isPerfect) == true,
            )
        }
    }.state(null)

    val goalsView: StateFlow<GoalsView?> = combine(dao.observeGoals(), dao.observeSteps(), settings, unlocked, today) { goals, steps, s, isUnlocked, day ->
        val all = goals.map { it.toCore() }
        val shown = if (s.locks.privateGoals && !isUnlocked) all.filter { !it.private } else all
        GoalsView(shown, steps.map { it.toCore() }.groupBy { it.goalId }, hiddenPrivate = all.size - shown.size, today = day)
    }.state(null)

    // How the app looks

    val appearance: StateFlow<Appearance> = combine(
        listOf(
            SETTING_THEME, SETTING_COLOR, SETTING_RECENT_COLORS, SETTING_TEXT_SIZE, SETTING_CARD_SIZE,
            SETTING_TODAY_LAYOUT, SETTING_BACKDROP, SETTING_BACKDROP_DIM, SETTING_PHOTO_STAMP,
            SETTING_TODAY_SECTIONS, SETTING_HIDDEN_TABS, SETTING_FIRST_TAB,
            SETTING_FONT, SETTING_CELEBRATE_SOUND, SETTING_CELEBRATE_BUZZ, SETTING_CONFETTI, SETTING_DEFAULT_GIF,
            SETTING_APP_ICON, SETTING_WIDGET_STYLE, io.github.randomtools3377.checklist.core.ColourBlind.SETTING,
            io.github.randomtools3377.checklist.data.SETTING_TAB_ORDER,
        ).map { dao.observeSetting(it) },
    ) { v ->
        Appearance(
            theme = ThemeMode.entries.firstOrNull { it.name == v[0] } ?: ThemeMode.PHONE,
            color = v[1]?.let { Colors.parseHex(it) },
            recent = v[2].orEmpty().split(",").mapNotNull { Colors.parseHex(it) },
            textSize = TextSize.entries.firstOrNull { it.name == v[3] } ?: TextSize.NORMAL,
            cardSize = CardSize.entries.firstOrNull { it.name == v[4] } ?: CardSize.COMFORTABLE,
            layout = TodayLayout.entries.firstOrNull { it.name == v[5] } ?: TodayLayout.LIST,
            backdrop = Backdrop.entries.firstOrNull { it.name == v[6] } ?: Backdrop.NONE,
            dim = v[7]?.toIntOrNull()?.coerceIn(Appearance.MIN_DIM, Appearance.MAX_DIM) ?: Appearance.DEFAULT_DIM,
            photoStamp = v[8].orEmpty(),
            sections = Sections.decode(v[9]),
            tabs = TabChoice.decode(v[10], v[11], v[20]),
            font = FontChoice.entries.firstOrNull { it.name == v[12] } ?: FontChoice.PHONE,
            celebrate = CelebrationStyle(
                sound = v[13] == "1",
                buzz = v[14] != "0",
                confetti = ConfettiAmount.entries.firstOrNull { it.name == v[15] } ?: ConfettiAmount.SOME,
                defaultGif = v[16]?.takeIf { Celebration.isValid(it) },
            ),
            icon = AppIcon.entries.firstOrNull { it.name == v[17] } ?: AppIcon.GARDEN,
            widgetStyle = WidgetStyle.entries.firstOrNull { it.name == v[18] } ?: WidgetStyle.SOLID,
            colourBlind = v[19] == "1",
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    fun setPointsName(name: PointsName) {
        viewModelScope.launch { repository.setSetting(SETTING_POINTS_NAME, PointsName.encode(name)) }
    }

    /** The launcher icon follows the setting (see ChecklistApplication). */
    fun setAppIcon(icon: AppIcon) {
        viewModelScope.launch { repository.setSetting(SETTING_APP_ICON, icon.name) }
    }

    fun setWidgetStyle(style: WidgetStyle) {
        viewModelScope.launch { repository.setSetting(SETTING_WIDGET_STYLE, style.name) }
    }

    // The optional AI, in the separate Checklist AI app.

    /** How Checklist AI stands; null until checked. */
    val aiStatus = MutableStateFlow<io.github.randomtools3377.checklist.data.AiStatus?>(null)

    fun refreshAi() {
        viewModelScope.launch { aiStatus.value = phone.aiStatus() }
    }

    /** Asks the AI to read a goal from [sentence]; [onResult] gets the goal, or why there isn't one. */
    fun goalFromSentence(sentence: String, onResult: (io.github.randomtools3377.checklist.core.GoalIdea?, String?) -> Unit) {
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.GoalFromSentence
            phone.askAi(ask.SYSTEM, ask.prompt(sentence), ask.SCHEMA, ask.MAX_TOKENS).fold(
                onSuccess = { answer -> onResult(ask.parse(answer), null) },
                onFailure = { e -> onResult(null, "The AI couldn't answer: ${e.message ?: "unknown error"}.") },
            )
        }
    }

    /** Asks the AI for steps for a project called [name]; [onResult] gets them, or why there are none. */
    fun projectSteps(name: String, onResult: (List<String>, String?) -> Unit) {
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.ProjectSteps
            phone.askAi(ask.SYSTEM, ask.prompt(name), ask.SCHEMA, ask.MAX_TOKENS).fold(
                onSuccess = { answer -> onResult(ask.parse(answer), null) },
                onFailure = { e -> onResult(emptyList(), "The AI couldn't answer: ${e.message ?: "unknown error"}.") },
            )
        }
    }

    /** Asks the AI for a note on the week in [summary]; [onResult] gets it, or why there isn't one. */
    fun coachNote(summary: io.github.randomtools3377.checklist.core.ReviewSummary, hideReasons: Boolean, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.CoachNote
            phone.askAi(ask.SYSTEM, ask.prompt(summary, hideReasons), null, ask.MAX_TOKENS).fold(
                onSuccess = { answer -> onResult(ask.clean(answer), null) },
                onFailure = { e -> onResult(null, "The AI couldn't answer: ${e.message ?: "unknown error"}.") },
            )
        }
    }

    fun setColourBlind(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.ColourBlind.SETTING, if (on) "1" else "0") }
    }

    fun setFont(font: FontChoice) {
        viewModelScope.launch { repository.setSetting(SETTING_FONT, font.name) }
    }

    // Each celebration setting is saved on its own, so quick taps on two of them can't undo each other.

    fun setCelebrationSound(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_CELEBRATE_SOUND, if (on) "1" else "0") }
    }

    fun setCelebrationBuzz(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_CELEBRATE_BUZZ, if (on) "1" else "0") }
    }

    fun setConfetti(amount: ConfettiAmount) {
        viewModelScope.launch { repository.setSetting(SETTING_CONFETTI, amount.name) }
    }

    fun setDefaultGif(gif: String?) {
        viewModelScope.launch { repository.setSetting(SETTING_DEFAULT_GIF, gif.orEmpty()) }
    }

    /** Changes Today's sections from what is saved, so quick taps each build on the last. */
    fun changeSections(change: (List<SectionChoice>) -> List<SectionChoice>) {
        viewModelScope.launch {
            val now = Sections.decode(dao.setting(SETTING_TODAY_SECTIONS))
            repository.setSetting(SETTING_TODAY_SECTIONS, Sections.encode(change(now)))
        }
    }

    fun setTabHidden(tab: AppTabKind, hidden: Boolean) {
        viewModelScope.launch {
            val now = TabChoice.decode(dao.setting(SETTING_HIDDEN_TABS), null).hidden
            repository.setSetting(SETTING_HIDDEN_TABS, TabChoice.encodeHidden(if (hidden) now + tab else now - tab))
        }
    }

    /** Moves a tab one place earlier (-1) or later (+1) along the bottom. */
    fun moveTab(tab: AppTabKind, by: Int) {
        viewModelScope.launch {
            val now = TabChoice.decode(null, null, dao.setting(io.github.randomtools3377.checklist.data.SETTING_TAB_ORDER))
            repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_TAB_ORDER, TabChoice.encodeOrder(now.moved(tab, by)))
        }
    }

    fun setFirstTab(tab: AppTabKind) {
        viewModelScope.launch { repository.setSetting(SETTING_FIRST_TAB, tab.name) }
    }

    fun setTextSize(size: TextSize) {
        viewModelScope.launch { repository.setSetting(SETTING_TEXT_SIZE, size.name) }
    }

    fun setCardSize(size: CardSize) {
        viewModelScope.launch { repository.setSetting(SETTING_CARD_SIZE, size.name) }
    }

    fun setTodayLayout(layout: TodayLayout) {
        viewModelScope.launch { repository.setSetting(SETTING_TODAY_LAYOUT, layout.name) }
    }

    /** A photo needs picking first; without one, My photo shows nothing behind Today. */
    fun setBackdrop(backdrop: Backdrop) {
        viewModelScope.launch { repository.setSetting(SETTING_BACKDROP, backdrop.name) }
    }

    fun setBackdropDim(percent: Int) {
        viewModelScope.launch { repository.setSetting(SETTING_BACKDROP_DIM, percent.coerceIn(Appearance.MIN_DIM, Appearance.MAX_DIM).toString()) }
    }

    /** Copies a photo from the Photo Picker in and puts it behind Today. */
    fun pickPhoto(file: PickedFile, onError: (String) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { phone.addBackgroundPhoto(file) }.getOrDefault(false)
            if (ok) {
                repository.setSetting(SETTING_PHOTO_STAMP, nowMillis().toString())
                repository.setSetting(SETTING_BACKDROP, Backdrop.PHOTO.name)
            } else {
                onError("That photo couldn't be read. Try another one.")
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repository.setSetting(SETTING_THEME, mode.name) }
    }

    /** Null matches the wallpaper. A colour from the picker is remembered among the recent ones. */
    fun setColor(color: Int?) {
        viewModelScope.launch {
            repository.setSetting(SETTING_COLOR, color?.let { Colors.hex(it) } ?: "")
            if (color != null) rememberColor(color)
        }
    }

    /** Keeps a colour picked with the colour picker, for picking again. */
    fun rememberColor(color: Int) {
        if (Colors.PRESETS.any { it.second == color }) return
        viewModelScope.launch {
            val recent = (listOf(color) + appearance.value.recent.filter { it != color }).take(MAX_RECENT_COLORS)
            repository.setSetting(SETTING_RECENT_COLORS, recent.joinToString(",") { Colors.hex(it) })
        }
    }

    // The weekly points target and badges

    /** The weekly points target, 0 when not set. */
    val weeklyTarget: StateFlow<Int> = dao.observeSetting(SETTING_WEEKLY_TARGET).map { it?.toIntOrNull()?.coerceAtLeast(0) ?: 0 }.state(0)

    fun setWeeklyTarget(points: Int) {
        viewModelScope.launch { repository.setSetting(SETTING_WEEKLY_TARGET, points.coerceIn(0, MAX_WEEKLY_TARGET).toString()) }
    }

    /** This week against the target, or null while no target is set. */
    val weekProgress: StateFlow<WeekProgress?> = today.flatMapLatest { day ->
        val monday = Days.periodStart(day, Period.WEEKLY)
        combine(dao.observeEntries(monday.toEpochDay(), day.toEpochDay()), dao.observeSetting(SETTING_WEEKLY_TARGET)) { rows, target ->
            val points = target?.toIntOrNull() ?: 0
            if (points <= 0) null else WeeklyTarget.progress(rows.map { it.toCore() }, day, points)
        }
    }.state(null)

    /** Badges earned, each with the day it was first earned. */
    val badges: StateFlow<Map<Badge, LocalDate>> = dao.observeSetting(SETTING_BADGES).map { Badges.decode(it) }.state(emptyMap())

    /** Badges earned since the user last looked, for the card on Today. */
    val newBadges: StateFlow<List<Badge>> = combine(dao.observeSetting(SETTING_BADGES), dao.observeSetting(SETTING_BADGES_SEEN)) { earned, seen ->
        val seenNames = seen.orEmpty().split(",").toSet()
        Badges.decode(earned).keys.filter { it.name !in seenNames }.sortedBy { it.ordinal }
    }.state(emptyList())

    fun badgesSeen() {
        viewModelScope.launch {
            val earned = Badges.decode(dao.setting(SETTING_BADGES)).keys
            repository.setSetting(SETTING_BADGES_SEEN, earned.joinToString(",") { it.name })
        }
    }

    init {
        // Looks for new badges a moment after each tick, undo or change of day.
        viewModelScope.launch {
            combine(today, dao.observeBalance(), dao.observeTickCount(), settings, dao.observeSetting(SETTING_WEEKLY_TARGET)) { day, _, _, s, target ->
                Triple(day, s.streak, target?.toIntOrNull() ?: 0)
            }.collectLatest { (day, streak, target) ->
                delay(BADGE_CHECK_DELAY_MS)
                findBadges(day, streak, target)
            }
        }
    }

    private suspend fun findBadges(day: LocalDate, streak: StreakSettings, target: Int) {
        val ticks = dao.allTicks().map { it.toCore() }
        if (ticks.isEmpty()) return
        val goals = dao.allGoals().map { it.toCore() }
        val steps = dao.allSteps().map { it.toCore() }
        val entries = dao.allEntries().map { it.toCore() }
        val lapses = dao.allLapses().map { it.toCore() }
        val earned = withContext(Dispatchers.Default) {
            val from = maxOf(ticks.minOf { it.day }, day.minusDays(STREAK_DAYS))
            val records = StreakCounter.records(from, day, goals.filter { !it.archived }, ticks.filter { it.stepId == null }, lapses)
            Badges.earned(day, records, streak, goals, steps, ticks, entries, target, character = Feature.CHARACTER in features.value)
        }
        val kept = Badges.decode(dao.setting(SETTING_BADGES))
        val merged = Badges.merge(kept, earned)
        if (merged.size > kept.size) repository.setSetting(SETTING_BADGES, Badges.encode(merged))
    }

    // Skips, misses and penalties

    private val lapses = parts.lapses

    /** Misses still waiting for a reason. They come first when the app opens. */
    val unexplained: StateFlow<List<Lapse>?> = dao.observeUnexplained().map { rows -> rows.map { it.toCore() } }.state(null)

    /** The penalty task owed right now, if any. It comes next, and fills the screen until done. */
    val openPenalty: StateFlow<Penalty?> = dao.observeOpenPenalties().map { rows -> rows.firstOrNull()?.toCore() }.state(null)

    val penaltyTasks: StateFlow<List<PenaltyTask>> = dao.observePenaltyTasks().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /** Goals skipped or missed in the last two weeks, which a penalty can be paid off by making up. */
    val makeUpChoices: StateFlow<List<Goal>> = today.flatMapLatest { day ->
        combine(dao.observeLapsesSince(day.minusDays(14).toEpochDay()), dao.observeGoals()) { recent, goals ->
            val ids = recent.filter { !it.forgotten }.map { it.goalId }.toSet()
            goals.map { it.toCore() }.filter { it.id in ids && it.isHabitLike }
        }
    }.state(emptyList())

    /** Looks for misses and gaps since the last check. Called when the app comes to the front. */
    fun closeDays() {
        calendarRefresh.value++
        viewModelScope.launch { parts.closeDays() }
        autoBackup()
        refreshFocus()
    }

    // The focus timer

    val focus = MutableStateFlow<FocusState?>(null)

    private fun refreshFocus() {
        focus.value = phone.focus()
        viewModelScope.launch { phone.rescheduleReminders() }
    }

    fun startFocus(goalId: Long, minutes: Int) {
        phone.startFocus(goalId, minutes)
        refreshFocus()
    }

    fun pauseFocus() {
        phone.pauseFocus()
        refreshFocus()
    }

    fun resumeFocus() {
        phone.resumeFocus()
        refreshFocus()
    }

    fun stopFocus() {
        phone.stopFocus()
        refreshFocus()
    }

    /**
     * Time's up, or "Done now": the goal is ticked. The timer is taken from the phone as it is
     * now, so if the phone's alarm already ended it and ticked the goal, it isn't ticked again.
     */
    fun finishFocus(onResult: (TickResult) -> Unit) {
        val goalId = phone.takeFocus()?.goalId
        refreshFocus()
        if (goalId != null) tickHabit(goalId, onResult)
    }

    /** A tab to open, asked for by a notification (the weekly summary opens the review). */
    val openTab = MutableStateFlow<String?>(null)

    // The weekly summary

    val weeklySummary: StateFlow<Boolean> = dao.observeSetting(SETTING_WEEKLY).map { it != "0" }.state(true)

    fun setWeeklySummary(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_WEEKLY, if (on) "1" else "0") }
    }

    // Backups: the reminder, and the automatic backup to a folder

    /** What Settings shows about backups, and whether Today shows the reminder. */
    data class BackupState(
        val lastBackup: Long?,
        val folderName: String?,
        val folderSet: Boolean,
        val folderGone: Boolean,
        val reminderOn: Boolean,
        /** Today's reminder: null when not due; otherwise days since the last backup, or -1 for never. */
        val due: Int?,
    )

    private val folderGone = MutableStateFlow(false)
    val backupState = MutableStateFlow(BackupState(null, null, false, false, true, null))

    fun refreshBackup() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val now = nowMillis()
                val last = phone.lastBackup()
                val hasData = dao.activeGoals().isNotEmpty()
                val due = if (phone.backupReminderOn() && phone.backupLaterUntil() < now &&
                    BackupReminder.due(last, phone.firstSeen(), now, hasData)
                ) {
                    last?.let { BackupReminder.daysSince(it, now) } ?: -1
                } else {
                    null
                }
                BackupState(last, phone.backupFolderName(), phone.backupFolder() != null, folderGone.value, phone.backupReminderOn(), due)
            }
            backupState.value = state
        }
    }

    private fun autoBackup() {
        viewModelScope.launch {
            val result = runCatching {
                phone.autoBackupIfDue(Days.dayOf(nowLocal(), settings.value.dayEndHour)) { repository.exportBackup() }
            }.getOrDefault(io.github.randomtools3377.checklist.data.AutoBackupResult.NotDue)
            if (result == io.github.randomtools3377.checklist.data.AutoBackupResult.FolderGone) folderGone.value = true
            if (result == io.github.randomtools3377.checklist.data.AutoBackupResult.Saved) folderGone.value = false
            refreshBackup()
        }
    }

    /** "Later" on Today's backup reminder: it comes back tomorrow. */
    fun backupLater() {
        phone.backupLater(nowMillis() + 86_400_000L)
        refreshBackup()
    }

    fun setBackupReminder(on: Boolean) {
        phone.setBackupReminderOn(on)
        refreshBackup()
    }

    /** Picks the automatic backup's folder (null turns it off), then backs up there right away. */
    fun setBackupFolder(folder: PickedFile?) {
        viewModelScope.launch {
            runCatching { phone.setBackupFolder(folder) }
            folderGone.value = false
            autoBackup()
        }
    }

    /** How long "Snooze" on a reminder puts it off, in minutes. */
    val snoozeMinutes: StateFlow<Int> = dao.observeSetting(SETTING_SNOOZE).map { it?.toIntOrNull() ?: DEFAULT_SNOOZE_MINUTES }.state(DEFAULT_SNOOZE_MINUTES)

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { repository.setSetting(SETTING_SNOOZE, minutes.toString()) }
    }

    // Gaps and the review

    /** A gap waiting for its check-in. It comes first when the app opens. */
    val unansweredGap: StateFlow<Gap?> = dao.observeUnansweredGaps().map { rows -> rows.firstOrNull()?.toCore() }.state(null)

    /** Every gap, to mark in the history and the review. */
    val gaps: StateFlow<List<Gap>> = dao.observeGaps().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /** How many misses fall inside a gap, and the points they cost, for the check-in. */
    suspend fun gapCost(gap: Gap): Pair<Int, Int> {
        val inGap = dao.allLapses(gap.from.toEpochDay(), gap.to.toEpochDay()).filter { it.gapId == gap.id }
        return inGap.size to inGap.sumOf { it.cost }
    }

    fun explainGap(gapId: Long, reason: Reason, note: String?, reasonText: String? = null) {
        viewModelScope.launch { parts.gaps.explain(gapId, reason, note, reasonText) }
    }

    fun setGapSettings(settings: GapSettings) {
        viewModelScope.launch {
            repository.setSetting(Gaps.SETTING_DAYS, settings.days.toString())
            repository.setSetting(Gaps.SETTING_POINTS, settings.points.toString())
        }
    }

    val reviewPeriod = MutableStateFlow(Period.WEEKLY)

    /** A day inside the stretch being reviewed; null for the current one. */
    val reviewDay = MutableStateFlow<LocalDate?>(null)

    fun reviewStep(by: Int) {
        viewModelScope.launch {
            val today = Days.dayOf(nowLocal(), settings.value.dayEndHour)
            val day = reviewDay.value ?: today
            val moved = when (reviewPeriod.value) {
                Period.DAILY -> day.plusDays(by)
                Period.WEEKLY -> day.plusDays(7 * by)
                Period.YEARLY -> Review.range(day, Period.YEARLY).let { (start, end) -> if (by < 0) start.minusDays(1) else end.plusDays(1) }
                else -> Review.range(day, Period.MONTHLY).let { (start, end) -> if (by < 0) start.minusDays(1) else end.plusDays(1) }
            }
            reviewDay.value = if (moved >= today) null else moved
        }
    }

    val review: StateFlow<ReviewSummary?> = combine(reviewPeriod, reviewDay, today) { period, day, todayDay ->
        Triple(period, day ?: todayDay, todayDay)
    }.distinctUntilChanged().flatMapLatest { (period, day, todayDay) ->
        val (from, to) = Review.range(day, period)
        val windowsFrom = Days.earliestOpenDay(from).minusDays(7)
        combine(
            combine(dao.observeGoals(), hidingPrivate) { goals, hide -> goals to hide },
            dao.observeHabitTicksSince(windowsFrom.toEpochDay()),
            dao.observeLapsesSince(windowsFrom.toEpochDay()),
            dao.observeEntries(from.toEpochDay(), to.toEpochDay()),
            dao.observeGaps(),
        ) { (goals, hide), ticks, lapsesRows, entries, gapRows ->
            Review.summarize(
                from = from,
                to = to,
                today = todayDay,
                goals = goals.map { it.toCore() },
                ticks = ticks.map { it.toCore() },
                lapses = lapsesRows.map { it.toCore() },
                entries = entries.map { it.toCore() },
                gaps = gapRows.map { it.toCore() },
                hidePrivate = hide,
            )
        }
    }.state(null)

    /** Moods over the last year, for the review. */
    val reviewMoods: StateFlow<List<Mood>> = today.flatMapLatest { day ->
        dao.observeMoodsSince(day.minusDays(400).toEpochDay()).map { rows -> rows.map { it.toCore() } }
    }.state(emptyList())

    // The daily mood check-in

    /** Today's mood, if it was given. */
    val todayMood: StateFlow<Mood?> = today.flatMapLatest { day -> dao.observeMood(day.toEpochDay()).map { it?.toCore() } }.state(null)

    /** Whether Today asks how the user feels: on, not answered and not put off today. */
    val askMood: StateFlow<Boolean> = combine(today, todayMood, dao.observeSetting(SETTING_MOOD), dao.observeSetting(SETTING_MOOD_SKIPPED)) { day, mood, on, skipped ->
        Feature.MOOD.isOn(on) && mood == null && skipped != day.toString()
    }.state(false)

    fun setMood(score: Int, note: String? = null) {
        viewModelScope.launch { repository.setMood(currentDay(), score, note) }
    }

    fun skipMood() {
        viewModelScope.launch { repository.setSetting(SETTING_MOOD_SKIPPED, currentDay().toString()) }
    }

    /** Whether the mood check-in is on (it is unless turned off). */
    val askMoodOn: StateFlow<Boolean> = dao.observeSetting(SETTING_MOOD).map { Feature.MOOD.isOn(it) }.state(false)

    fun setMoodCheckIn(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_MOOD, if (on) "1" else "0") }
    }

    /** The streak bonus, on unless turned off. */
    val streakBonusOn: StateFlow<Boolean> = dao.observeSetting(StreakBonus.SETTING).map { Feature.STREAK_BONUS.isOn(it) }.state(false)

    fun setStreakBonus(on: Boolean) {
        viewModelScope.launch { repository.setSetting(StreakBonus.SETTING, if (on) "1" else "0") }
    }

    suspend fun checkSkip(goalId: Long): SkipCheck = lapses.checkSkip(goalId)

    fun skip(goalId: Long, reason: Reason?, note: String?, reasonText: String? = null, onResult: (SkipResult) -> Unit = {}) {
        viewModelScope.launch { onResult(lapses.skip(goalId, reason, note, reasonText)) }
    }

    fun explain(lapseId: Long, reason: Reason, note: String?, reasonText: String? = null) {
        viewModelScope.launch { lapses.explain(lapseId, reason, note, reasonText) }
    }

    /** The user's own miss reasons, offered after the four. */
    val ownReasons: StateFlow<List<String>> = dao.observeSetting(SETTING_OWN_REASONS)
        .map { text -> text?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty() }
        .state(emptyList())

    fun addOwnReason(text: String) {
        val reason = text.trim().take(40)
        if (reason.isEmpty() || Reason.entries.any { it.label.equals(reason, ignoreCase = true) }) return
        val list = (ownReasons.value.filter { !it.equals(reason, ignoreCase = true) } + reason).take(12)
        viewModelScope.launch { repository.setSetting(SETTING_OWN_REASONS, list.joinToString("\n")) }
    }

    fun removeOwnReason(text: String) {
        viewModelScope.launch { repository.setSetting(SETTING_OWN_REASONS, ownReasons.value.filter { it != text }.joinToString("\n")) }
    }

    /** Fixes a tick: when it was really done, on the same day, and its note. */
    fun editTick(tickId: Long, at: Long?, note: String?) {
        viewModelScope.launch { book.editTick(tickId, at, note) }
    }

    suspend fun loadTick(tickId: Long): Tick? = dao.tick(tickId)?.toCore()

    /** Each tick's note, for the history. */
    val tickNotes: StateFlow<Map<Long, String>> = dao.observeTickNotes()
        .map { rows -> rows.mapNotNull { row -> row.note?.let { row.id to it } }.toMap() }
        .state(emptyMap())

    fun forgotToTick(lapseId: Long) {
        viewModelScope.launch { lapses.forgotToTick(lapseId) }
    }

    fun makeUp(penaltyId: Long, goalId: Long) {
        viewModelScope.launch { lapses.makeUp(penaltyId, goalId) }
    }

    fun drawTask(penaltyId: Long) {
        viewModelScope.launch { lapses.drawTask(penaltyId) }
    }

    fun finishPenalty(penaltyId: Long) {
        viewModelScope.launch { lapses.finishPenalty(penaltyId) }
    }

    fun addPenaltyTask(name: String) {
        viewModelScope.launch { repository.addPenaltyTask(name.trim()) }
    }

    fun removePenaltyTask(id: Long) {
        viewModelScope.launch { repository.removePenaltyTask(id) }
    }

    fun setStreakSettings(settings: StreakSettings) {
        viewModelScope.launch {
            repository.setSetting(SETTING_STREAK_TARGET, settings.target.name)
            repository.setSetting(SETTING_STREAK_VALUE, settings.value.toString())
        }
    }

    fun setLock(name: String, on: Boolean) {
        viewModelScope.launch { repository.setSetting(name, if (on) "1" else "0") }
    }

    val shopView: StateFlow<ShopView?> = today.flatMapLatest { day ->
        val pointsPerDay = combine(
            dao.observeEarnedByDay(day.minusDays(Usual.WINDOW_DAYS).toEpochDay()),
            dao.observeFirstEarningDay(),
        ) { earned, first ->
            Usual.pointsPerDay(
                earnedByDay = earned.associate { it.day.toDay() to it.amount },
                firstDay = first?.toDay(),
                today = day,
            )
        }
        // Rewards bought this week cost more each time (see RepeatPrice): from Monday, when the day ends.
        val weekStart = LocalDateTime(Boost.weekOf(day), LocalTime(settings.value.dayEndHour, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val thisWeek = combine(dao.observePurchasesSince(weekStart), dao.observeSetting(RepeatPrice.SETTING)) { list, on ->
            Feature.REPEAT_PRICE.isOn(on) to list.filter { it.returnedAt == null }.groupingBy { it.rewardId }.eachCount()
        }
        combine(dao.observeBalance(), dao.observeRewards(), dao.observeUnusedPurchases(), pointsPerDay, thisWeek) { balance, rewards, bought, perDay, (on, counts) ->
            ShopView(
                balance = balance,
                rewards = rewards.map {
                    val reward = it.toCore()
                    val times = counts[reward.id] ?: 0
                    val now = RepeatPrice.now(reward.price, times, on)
                    RewardRow(reward.copy(price = now), Usual.describe(now, perDay), usualPrice = reward.price, boughtThisWeek = times)
                },
                bought = bought.map { it.toCore() },
            )
        }
    }.state(null)

    /** Every change to the balance, newest first. Private goals' entries aren't named while they are locked. */
    val history: StateFlow<List<Entry>?> = combine(dao.observeEntries(), privateIds, hidingPrivate) { rows, ids, hide ->
        rows.map { it.toCore() }.let { entries -> if (hide) io.github.randomtools3377.checklist.core.PrivateGoals.unnamed(entries, ids) else entries }
    }.state(null)

    // Ticking and the shop. Results come back on the main thread.

    /** What the Undo bar offers to take back: the last tick, removed goal or purchase. */
    val undoBar = kotlinx.coroutines.flow.MutableSharedFlow<Undoable>(extraBufferCapacity = 4)

    private fun offerUndo(message: String, undo: suspend () -> Unit) {
        undoBar.tryEmit(Undoable(message) { viewModelScope.launch { undo() } })
    }

    private suspend fun offerTickUndo(
        goalId: Long,
        result: TickResult,
        luck: io.github.randomtools3377.checklist.data.Repository.LuckPaid? = null,
        wonBets: Int = 0,
        undo: suspend () -> Unit,
    ) {
        if (result !is TickResult.Ticked) return
        // A goal done: the next in its habit chain is reminded.
        if (result.finished) phone.afterDone(goalId)
        val name = repository.goal(goalId)?.name ?: return
        val paid = result.points + result.bonus
        val lucky = luck?.let { if (it.double) " · 🍀 lucky, twice the points: +${it.points}" else " · 🍀 lucky chest: +${it.points}" }.orEmpty()
        val bet = if (wonBets > 0) " · 🎲 bet won: +$wonBets" else ""
        offerUndo((if (paid > 0) "$name · +$paid" else "$name ticked") + lucky + bet, undo)
    }

    fun tickHabit(goalId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.tickHabit(goalId)
            onResult(result)
            // Lucky ticks: now and then a tick pays extra. Undoing the tick takes the extra back.
            val luck = if (result is TickResult.Ticked && Feature.LUCKY in features.value) {
                repository.luckyTick(goalId, currentDay(), result.points)
            } else {
                null
            }
            // Bets on yourself: one reached by this tick is won straight away.
            val betsOn = result is TickResult.Ticked && Feature.BETS in features.value
            val won = if (betsOn) parts.bets.settle().filter { it.state == io.github.randomtools3377.checklist.core.BetState.WON } else emptyList()
            offerTickUndo(goalId, result, luck, wonBets = won.sumOf { it.prize }) {
                book.undoHabitTick(goalId)
                luck?.let { repository.removeEntry(it.entryId) }
                if (won.isNotEmpty()) parts.bets.takeBackWins()
            }
        }
    }

    /** "I slipped" on a goal to quit: today is a miss now, and the misses screen asks why. */
    fun slip(goalId: Long) {
        viewModelScope.launch { lapses.slip(goalId) }
    }

    fun undoHabitTick(goalId: Long) {
        viewModelScope.launch { book.undoHabitTick(goalId) }
    }

    /** Enters how much of a goal's amount was done; a timing from Start and Stop ends with it. */
    fun logAmount(goalId: Long, amount: Double, seconds: Int?, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.logAmount(goalId, amount, seconds)
            if (seconds != null) repository.setSetting(SETTING_STOPWATCH, "")
            onResult(result)
            offerTickUndo(goalId, result) { book.undoHabitTick(goalId) }
        }
    }

    /** The Start and Stop timing running now, if any. It keeps running when the app is closed. */
    val stopwatch: StateFlow<Stopwatch?> = dao.observeSetting(SETTING_STOPWATCH).map { text ->
        val parts = text?.split("|")?.takeIf { it.size == 2 } ?: return@map null
        val goalId = parts[0].toLongOrNull() ?: return@map null
        val startedAt = parts[1].toLongOrNull() ?: return@map null
        Stopwatch(goalId, startedAt)
    }.state(null)

    // The planner: the coming days, and moving one day's goal to another day of its week.

    /** Today and the 13 days after it, each as Today would show it. */
    val planDays: StateFlow<List<TodayView>> = today.flatMapLatest { day ->
        val since = Days.earliestOpenDay(day)
        combine(
            dao.observeGoals(),
            dao.observeSteps(),
            dao.observeHabitTicksSince(since.toEpochDay()),
            dao.observeLapsesSince(since.toEpochDay()),
            combine(settings, unlocked) { s, isUnlocked -> s.locks.privateGoals && !isUnlocked },
        ) { goals, steps, ticks, lapses, hidePrivate ->
            val shown = goals.map { it.toCore() }.filter { !(hidePrivate && it.private) }
            val coreSteps = steps.map { it.toCore() }
            val coreTicks = ticks.map { it.toCore() }
            val coreLapses = lapses.map { it.toCore() }
            (0 until PLAN_DAYS).map { d -> TodayList.build(day.plusDays(d), shown, coreSteps, coreTicks, 0, coreLapses) }
        }
    }.state(emptyList())

    /** Moves one day's goal to another day of the same week. Free; a miss there counts as usual. */
    fun moveGoal(goalId: Long, from: LocalDate, to: LocalDate, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val today = Days.dayOf(nowLocal(), settings.value.dayEndHour)
            onDone(repository.moveGoal(goalId, from, to, today))
        }
    }

    /** A goal's clock time for one day only, from the timeline; null puts back its usual time. */
    fun retime(goalId: Long, day: LocalDate, minute: Int?) {
        viewModelScope.launch { repository.retimeGoal(goalId, day, minute, currentDay()) }
    }

    /** Stop pressed on the timing notification, for this goal: Today asks how much was done. */
    val stopRequest = MutableStateFlow<Long?>(null)

    fun startStopwatch(goalId: Long) {
        viewModelScope.launch { repository.setSetting(SETTING_STOPWATCH, "$goalId|${nowMillis()}") }
    }

    fun cancelStopwatch() {
        viewModelScope.launch { repository.setSetting(SETTING_STOPWATCH, "") }
    }

    /** A goal's usual speed, from its latest timings: minutes for one unit, and how many timings. */
    suspend fun usualSpeed(goalId: Long): Pair<Double, Int>? {
        val ticks = dao.allTicks().map { it.toCore() }.filter { it.goalId == goalId && it.stepId == null }
        val rate = Amounts.minutesPerUnit(ticks) ?: return null
        return rate to ticks.count { (it.seconds ?: 0) > 0 && (it.amount ?: 0.0) > 0 }.coerceAtMost(Amounts.TIMINGS)
    }

    /** Ticks one item of a routine; the last one ticks the routine. */
    fun checkItem(stepId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.checkItem(stepId)
            onResult(result)
            val step = dao.step(stepId)
            if (step != null && result is TickResult.Ticked) offerUndo("${step.name} ticked") { book.uncheckItem(stepId) }
        }
    }

    fun uncheckItem(stepId: Long) {
        viewModelScope.launch { book.uncheckItem(stepId) }
    }

    /** "Done" on a routine: every item still open, and so the routine. */
    fun finishRoutine(goalId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.finishRoutine(goalId)
            onResult(result)
            offerTickUndo(goalId, result) { book.undoRoutine(goalId) }
        }
    }

    fun undoRoutine(goalId: Long) {
        viewModelScope.launch { book.undoRoutine(goalId) }
    }

    fun tickStep(stepId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.tickStep(stepId)
            onResult(result)
            val step = dao.step(stepId)
            if (step != null && result is TickResult.Ticked) {
                val paid = result.points + result.bonus
                offerUndo(if (paid > 0) "${step.name} · +$paid" else "${step.name} ticked") { book.undoStep(stepId) }
            }
        }
    }

    fun undoStep(stepId: Long) {
        viewModelScope.launch { book.undoStep(stepId) }
    }

    fun buy(rewardId: Long, onResult: (BuyResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.buy(rewardId)
            onResult(result)
            if (result is BuyResult.Bought) {
                val name = result.prize ?: repository.reward(rewardId)?.name ?: return@launch
                countRewardSpending(result.purchaseId, rewardId, name)
                offerUndo("Bought $name · −${result.price}") { returnAndUncount(result.purchaseId) }
            }
        }
    }

    fun use(purchaseId: Long) {
        viewModelScope.launch { book.use(purchaseId) }
    }

    fun returnPurchase(purchaseId: Long) {
        viewModelScope.launch { returnAndUncount(purchaseId) }
    }

    // Editing

    suspend fun loadGoal(id: Long): Pair<Goal, List<Step>>? {
        val goal = repository.goal(id) ?: return null
        // A project's steps, or a routine's checklist.
        val steps = repository.steps(id)
        return goal to steps
    }

    suspend fun loadReward(id: Long): Reward? = repository.reward(id)

    fun saveHabit(draft: HabitDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveHabit(draft)
            onDone()
        }
    }

    /** Saves a quick to-do, from its editor or from the box on Today. */
    fun saveTodo(draft: TodoDraft, onDone: () -> Unit = {}) {
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            repository.saveTodo(draft)
            onDone()
        }
    }

    // Difficulty and suggestions

    /** The difficulty the user set: a level, or a ramp. */
    val difficulty: StateFlow<DifficultySetting> =
        combine(dao.observeSetting(DifficultySetting.LEVEL), dao.observeSetting(DifficultySetting.RAMP)) { level, ramp ->
            DifficultySetting.decode(level, ramp)
        }.state(DifficultySetting())

    /** Today's difficulty, which sets what goals pay. */
    val currentDifficulty: StateFlow<Difficulty> = combine(difficulty, today) { d, day -> d.on(day) }.state(Difficulty.NORMAL)

    /** What the user would spend on treats in a normal week, for prices from money. */
    val treatBudget: StateFlow<Double?> = dao.observeSetting(SETTING_TREAT_BUDGET).map { it?.toDoubleOrNull()?.takeIf { v -> v > 0 } }.state(null)

    /**
     * The user's usual earnings a day at Normal, for suggested prices: what they earned lately,
     * taken back to Normal, or before there is any history, what their goals would pay.
     */
    val usualAtNormal: StateFlow<Double?> = today.flatMapLatest { day ->
        combine(
            dao.observeEarnedByDay(day.minusDays(Usual.WINDOW_DAYS).toEpochDay()),
            dao.observeFirstEarningDay(),
            dao.observeGoals(),
            currentDifficulty,
        ) { earned, first, goals, rate ->
            val earnedPerDay = Usual.pointsPerDay(earned.associate { it.day.toDay() to it.amount }, first?.toDay(), day)
            earnedPerDay?.let { it * 100 / rate.percent } ?: Suggest.expectedPerDay(goals.map { it.toCore() }, day)
        }
    }.state(null)

    /**
     * Points for 1 of money, for rewards whose price follows what they cost (see MoneyPrices):
     * the rate in use, or a fresh one once the goals have drifted from it. Null without a weekly
     * treat budget or a repeating goal that pays.
     */
    val moneyRate: StateFlow<MoneyPrices.Rate?> = combine(
        dao.observeSetting(MoneyPrices.RATE), dao.observeGoals(), treatBudget, today,
    ) { stored, goals, budget, day ->
        MoneyPrices.next(MoneyPrices.decode(stored), MoneyPrices.fresh(goals.map { it.toCore() }, day, budget))
    }.state(null)

    init {
        // Prices that follow money keep in step with what the goals pay, and with the budget.
        viewModelScope.launch {
            combine(dao.observeGoals(), treatBudget, dao.observeRewards(), today) { _, _, _, day -> day }
                .collectLatest { day -> repository.keepMoneyPricesInStep(day) }
        }
    }

    /** First-time tips: on unless turned off, and the guides already shown. Null until read. */
    val tips: StateFlow<TipsState?> = combine(dao.observeSetting(SETTING_TIPS_OFF), dao.observeSetting(SETTING_TIPS_SEEN)) { off, seen ->
        TipsState(on = off != "1", seen = seen.orEmpty().split(",").filter { it.isNotBlank() }.toSet())
    }.state(null)

    fun tipSeen(topic: HelpTopic) {
        val seen = (tips.value?.seen.orEmpty() + topic.name).sorted().joinToString(",")
        viewModelScope.launch { repository.setSetting(SETTING_TIPS_SEEN, seen) }
    }

    fun setTipsOn(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_TIPS_OFF, if (on) "0" else "1") }
    }

    fun showTipsAgain() {
        viewModelScope.launch {
            repository.setSetting(SETTING_TIPS_SEEN, "")
            repository.setSetting(SETTING_TIPS_OFF, "0")
        }
    }

    /** Every number for points, prices and the game, as the user set them (see Rules). */
    val rules: StateFlow<io.github.randomtools3377.checklist.core.Rules> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.Rules.SETTING)
            .map { io.github.randomtools3377.checklist.core.Rules.decode(it) }
            .state(io.github.randomtools3377.checklist.core.Rules())

    init {
        // The rules in force follow the setting, from the first moment it is read.
        viewModelScope.launch { rules.collect { io.github.randomtools3377.checklist.core.Rules.current = it } }
    }

    fun setRules(rules: io.github.randomtools3377.checklist.core.Rules) {
        viewModelScope.launch {
            repository.setSetting(io.github.randomtools3377.checklist.core.Rules.SETTING, io.github.randomtools3377.checklist.core.Rules.encode(rules))
        }
    }

    /** Savings interest on a positive balance each week, as a whole percentage; 0 is off. */
    val interestPercent: StateFlow<Int> =
        dao.observeSetting(Interest.PERCENT).map { it?.toIntOrNull()?.coerceIn(0, 100) ?: 0 }.state(0)

    /** The boost, the perfect-day bonus and repeat-buy prices, each of which can be turned off. */
    val boosts: StateFlow<Boosts> = combine(
        dao.observeSetting(Boost.SETTING), dao.observeSetting(PerfectDays.BONUS), dao.observeSetting(RepeatPrice.SETTING),
    ) { boost, perfect, repeat ->
        Boosts(Feature.BOOST.isOn(boost), perfect?.toIntOrNull()?.coerceIn(0, 10_000) ?: 0, Feature.REPEAT_PRICE.isOn(repeat))
    }.state(Boosts())

    fun setBoost(on: Boolean) {
        viewModelScope.launch { repository.setSetting(Boost.SETTING, if (on) "1" else "0") }
    }

    fun setPerfectDayBonus(points: Int) {
        viewModelScope.launch { repository.setSetting(PerfectDays.BONUS, points.coerceIn(0, 10_000).toString()) }
    }

    fun setRepeatPrice(on: Boolean) {
        viewModelScope.launch { repository.setSetting(RepeatPrice.SETTING, if (on) "1" else "0") }
    }

    fun setInterest(percent: Int) {
        viewModelScope.launch { repository.setSetting(Interest.PERCENT, percent.coerceIn(0, 100).toString()) }
    }

    /** The balance check in the shop: prices against earnings, and goals against how often they get done. */
    val balanceCheck: StateFlow<BalanceCheckView?> = today.flatMapLatest { day ->
        val perDay = combine(
            dao.observeEarnedByDay(day.minusDays(Usual.WINDOW_DAYS).toEpochDay()),
            dao.observeFirstEarningDay(),
        ) { earned, first -> Usual.pointsPerDay(earned.associate { it.day.toDay() to it.amount }, first?.toDay(), day) }
        val shown = combine(dao.observeGoals(), settings, unlocked) { goals, s, isUnlocked ->
            val all = goals.map { it.toCore() }
            if (s.locks.privateGoals && !isUnlocked) all.filter { !it.private } else all
        }
        combine(
            combine(dao.observeRewards(), shown, dao.observeHabitTicksSince(day.minusDays(BalanceCheck.DAYS).toEpochDay())) { r, g, t -> Triple(r, g, t) },
            perDay,
            usualAtNormal,
            moneyRate,
            currentDifficulty,
        ) { (rewards, goals, ticks), earned, atNormal, money, rate ->
            BalanceCheckView(
                perDay = earned,
                rate = rate,
                rewards = BalanceCheck.rewards(rewards.map { it.toCore() }, earned, atNormal, money),
                goals = BalanceCheck.goals(goals, ticks.map { it.toCore() }, day) { it.createdAt.toLocalDateTime().date },
            )
        }
    }.state(null)

    fun setRewardPrice(id: Long, price: Int) {
        viewModelScope.launch { repository.setRewardPrice(id, price) }
    }

    fun setGoalPoints(id: Long, points: Int) {
        viewModelScope.launch { repository.setGoalPoints(id, points) }
    }

    /** Picks a level by hand, which stops a ramp. */
    fun setDifficulty(level: Difficulty) {
        viewModelScope.launch {
            repository.setSetting(DifficultySetting.LEVEL, level.name)
            repository.setSetting(DifficultySetting.RAMP, "")
        }
    }

    /** Starts getting harder (or easier) on a schedule, from this week. */
    fun startRamp(from: Difficulty, to: Difficulty, weeks: Int) {
        viewModelScope.launch {
            repository.setSetting(DifficultySetting.RAMP, Ramp.encode(Ramp(from, to, weeks.coerceIn(1, 52), currentDay())))
        }
    }

    /** Stops the ramp, staying at the level it has reached. */
    fun stopRamp() {
        viewModelScope.launch {
            repository.setSetting(DifficultySetting.LEVEL, currentDifficulty.value.name)
            repository.setSetting(DifficultySetting.RAMP, "")
        }
    }

    fun setTreatBudget(value: Double?) {
        viewModelScope.launch { repository.setSetting(SETTING_TREAT_BUDGET, value?.takeIf { it > 0 }?.toString() ?: "") }
    }

    /** Finished projects and done to-dos, newest first; private ones hidden while locked. */
    val doneList: StateFlow<List<DoneItem>> = combine(
        dao.observeGoals(), dao.observeSteps(), dao.observeStepTicks(), dao.observeTodoTicks(), combine(settings, unlocked) { s, u -> s to u },
    ) { goals, steps, stepTicks, todoTicks, (s, isUnlocked) ->
        val all = goals.map { it.toCore() }
        val shown = if (s.locks.privateGoals && !isUnlocked) all.filter { !it.private } else all
        DoneList.of(shown, steps.map { it.toCore() }, (stepTicks + todoTicks).map { it.toCore() })
    }.state(emptyList())

    /** The optional features turned on (see Feature). Off until the user turns them on. */
    val features: StateFlow<Set<Feature>> = combine(Feature.entries.map { dao.observeSetting(it.key) }) { values ->
        Feature.entries.filterIndexed { i, feature -> feature.isOn(values[i]) }.toSet()
    }.state(emptySet())

    /** The character's level and skills, while the character is on; null when it is off. */
    val character: StateFlow<io.github.randomtools3377.checklist.core.CharacterSheet?> =
        combine(features, dao.observeBalance(), dao.observeGoals(), dao.observeSetting(io.github.randomtools3377.checklist.core.Paths.SETTING)) { on, _, _, path ->
            if (Feature.CHARACTER in on) io.github.randomtools3377.checklist.core.Paths.decode(path) to true else null to false
        }.mapLatest { (path, on) ->
            if (!on) null else withContext(Dispatchers.Default) {
                io.github.randomtools3377.checklist.core.Experience.of(dao.allEntries().map { it.toCore() }, dao.allGoals().map { it.toCore() }, path)
            }
        }.state(null)

    /** The character's path, its titles earned, and the title shown, while the character is on. */
    val characterTitles: StateFlow<CharacterTitles?> =
        combine(character, dao.observeSetting(io.github.randomtools3377.checklist.core.Paths.SETTING), dao.observeSetting(io.github.randomtools3377.checklist.core.Titles.SETTING), settings) { sheet, path, title, s ->
            sheet?.let { Triple(it, io.github.randomtools3377.checklist.core.Paths.decode(path), title) to s.streak }
        }.mapLatest { found ->
            found?.let { (picked, streak) ->
                val (sheet, path, title) = picked
                withContext(Dispatchers.Default) {
                    val day = currentDay()
                    val from = day.minusDays(STREAK_DAYS)
                    val goals = dao.allGoals().map { it.toCore() }
                    val ticks = dao.allTicks().map { it.toCore() }
                    val records = StreakCounter.records(from, day, goals.filter { !it.archived }, ticks.filter { it.stepId == null }, dao.allLapses().map { it.toCore() })
                    val earned = io.github.randomtools3377.checklist.core.Titles.earned(
                        ticks, dao.allEntries().map { it.toCore() }, records, sheet, streak, { at -> at.toLocalDateTime().time },
                    )
                    CharacterTitles(path, earned, title, io.github.randomtools3377.checklist.core.Titles.shown(title, path, earned))
                }
            }
        }.state(null)

    fun setPath(path: io.github.randomtools3377.checklist.core.Path) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Paths.SETTING, path.name) }
    }

    /** The title to show: a title's name, "PATH" for the path's, or "" for none. */
    fun setTitle(choice: String) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Titles.SETTING, choice) }
    }

    /** Today's quests, each with how far along it is, while quests are on; null when off. */
    val quests: StateFlow<io.github.randomtools3377.checklist.core.DayQuests?> =
        combine(features, todayState) { on, state -> if (Feature.QUESTS in on) state else null }
            .flatMapLatest { state ->
                if (state == null) {
                    kotlinx.coroutines.flow.flowOf(null)
                } else {
                    val day = state.view.day
                    combine(dao.observeHabitTicksSince(day.toEpochDay()), dao.observeEntries(day.toEpochDay(), day.toEpochDay())) { ticks, entries ->
                        val rows = state.view.dueToday
                        val todays = ticks.map { it.toCore() }.filter { it.day == day }
                        val earned = entries.map { it.toCore() }
                            .filter { it.kind in io.github.randomtools3377.checklist.core.Experience.KINDS && it.goalId != null }
                            .sumOf { it.amount }
                        val list = io.github.randomtools3377.checklist.core.Quests.of(day, rows.map { it.goal }).map { quest ->
                            io.github.randomtools3377.checklist.core.Quests.progress(quest, rows, todays, { at -> at.toLocalDateTime().time }, earned)
                        }
                        io.github.randomtools3377.checklist.core.DayQuests(day, list)
                    }
                }
            }.state(null)

    init {
        // A finished quest pays its bonus once.
        viewModelScope.launch {
            quests.collect { day -> day?.list?.filter { it.complete }?.forEach { repository.payQuest(day.day, it.quest) } }
        }
    }

    /** This week's points against last week's by the same time, while the race is on; null when off. */
    val race: StateFlow<io.github.randomtools3377.checklist.core.RaceStanding?> =
        combine(features, today, dao.observeBalance()) { on, day, _ -> if (Feature.RACE in on) day else null }
            .mapLatest { day ->
                day?.let { d ->
                    val monday = d.minusDays(d.dayOfWeek.isoDayNumber - 1)
                    io.github.randomtools3377.checklist.core.Race.of(
                        dao.entries(monday.minusDays(7).toEpochDay(), d.toEpochDay()).map { it.toCore() }, d, nowMillis(),
                    )
                }
            }.state(null)

    /** This week's boss and the items in hand, while items and bosses are on; null when off. */
    val boss: StateFlow<io.github.randomtools3377.checklist.core.BossView?> =
        combine(features, today, dao.observeBalance(), dao.observeSetting(io.github.randomtools3377.checklist.core.Bosses.FIGHT), dao.observeGoals()) { on, day, _, _, _ ->
            if (Feature.BOSSES in on) day else null
        }.mapLatest { day -> day?.let { repository.bossView(it) } }.state(null)

    /** Uses every item in hand on this week's boss; [onDone] says what it did. */
    fun attackBoss(onDone: (io.github.randomtools3377.checklist.core.Attack?) -> Unit) {
        viewModelScope.launch { onDone(repository.attackBoss(currentDay())) }
    }

    /** This week's bets, each with its goal and how far along it is, while bets are on; null when off. */
    val bets: StateFlow<List<io.github.randomtools3377.checklist.core.BetRow>?> =
        combine(features, today, dao.observeBalance(), dao.observeSetting(io.github.randomtools3377.checklist.core.Bets.SETTING), dao.observeGoals()) { on, day, _, text, goals ->
            if (Feature.BETS in on) Triple(day, text, goals) else null
        }.mapLatest { found ->
            found?.let { (day, text, goals) ->
                val monday = io.github.randomtools3377.checklist.core.Bosses.monday(day)
                val ticks = dao.allTicks().map { it.toCore() }
                io.github.randomtools3377.checklist.core.Bets.decode(text).filter { it.week == monday }.mapNotNull { bet ->
                    goals.firstOrNull { it.id == bet.goalId }?.let { goal ->
                        io.github.randomtools3377.checklist.core.BetRow(bet, "${goal.icon} ${goal.name}", io.github.randomtools3377.checklist.core.Bets.count(ticks, bet.goalId, monday))
                    }
                }
            }
        }.state(null)

    /** Bets that [goalId] will be done [times] times this week, for [stake] points; [onDone] gets why not, or null. */
    fun placeBet(goalId: Long, times: Int, stake: Int, onDone: (io.github.randomtools3377.checklist.core.BetRefusal?) -> Unit) {
        viewModelScope.launch { onDone(parts.bets.place(goalId, times, stake)) }
    }

    /** How the user has styled the character: skin, hair and eyes. */
    val look: StateFlow<io.github.randomtools3377.checklist.core.Look> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.Looks.SETTING)
            .map { io.github.randomtools3377.checklist.core.Looks.decode(it) }
            .state(io.github.randomtools3377.checklist.core.Look())

    fun setLook(look: io.github.randomtools3377.checklist.core.Look) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Looks.SETTING, io.github.randomtools3377.checklist.core.Looks.encode(look)) }
    }

    /**
     * What the character says on Today, and its face: while the character is on. The words and
     * the suggestion are shown only with check-ins on; the face follows how things are going
     * either way. A new level is cheered until it is seen.
     */
    val companion: StateFlow<CompanionView?> =
        combine(
            combine(character, features) { sheet, on -> sheet to (Feature.CHECKINS in on) },
            todayState,
            todayMood,
            askMood,
            dao.observeSetting(io.github.randomtools3377.checklist.core.Checkins.LEVEL_SEEN),
        ) { (sheet, talks), state, mood, ask, seen ->
            if (sheet == null || state == null) return@combine null
            val level = sheet.level.level
            val seenLevel = seen?.toIntOrNull()
            if (seenLevel == null) {
                // The first look: start from here, no cheer.
                repository.setSetting(io.github.randomtools3377.checklist.core.Checkins.LEVEL_SEEN, level.toString())
            }
            // A goal skipped today counts neither as done nor as left.
            val rows = state.view.dueToday.filter { it.done || it.skips == 0 }
            val left = rows.filter { !it.done && !it.goal.quit && !it.goal.hasAmount }
            val say = io.github.randomtools3377.checklist.core.Checkins.say(
                io.github.randomtools3377.checklist.core.CompanionFacts(
                    day = state.view.day,
                    now = state.now.time,
                    left = left.map { it.goal },
                    doneToday = rows.count { it.done },
                    dueToday = rows.size,
                    missedYesterday = state.leftYesterday,
                    mainStreak = state.streaks.done,
                    mood = mood?.score,
                    askMood = ask && talks,
                    daysAway = state.daysAway,
                    newLevel = level.takeIf { seenLevel != null && it > seenLevel },
                ),
            )
            // With everything done, or nothing due, it offers ideas for the area furthest behind.
            val restful = !say.levelUp && !say.askMood && (rows.isEmpty() || rows.all { it.done })
            CompanionView(
                say, talks, say.suggestion?.let { goal -> left.firstOrNull { it.goal.id == goal.id } },
                ideaArea = if (restful) io.github.randomtools3377.checklist.core.Ideas.weakest(sheet) else null,
            )
        }.state(null)

    // Mood and your goals

    /**
     * Which habits, and which things noted at the check-ins, go with a better mood and which with
     * a lower one, over the last 90 days (see MoodInsights). Private goals are left out while
     * they are locked.
     */
    val moodFindings: StateFlow<MoodFindings> = today.flatMapLatest { day ->
        val from = day.minusDays(MoodInsights.DAYS)
        combine(
            dao.observeGoals(),
            // Back to the start of every window those days fall in, for goals counted over a week or more.
            dao.observeHabitTicksSince(Days.earliestOpenDay(from).toEpochDay()),
            dao.observeMoodsSince(from.toEpochDay()),
            rules,
            combine(settings, unlocked) { s, open -> s.locks.privateGoals && !open },
        ) { goals, ticks, moods, rules, hidePrivate ->
            val shown = goals.map { it.toCore() }.filter { !it.archived && !(hidePrivate && it.private) }
            MoodInsights.find(shown, ticks.map { it.toCore() }, moods.map { it.toCore() }, day, rules)
        }
    }.state(MoodFindings.NONE)

    /**
     * The check-in once everything due today is done, with the mood check-in on and the morning's
     * mood given: asked until answered or put off, then its answer until closed. Null otherwise.
     */
    val moodAfter: StateFlow<MoodAfterView?> =
        combine(today, todayState, todayMood, features, dao.observeSetting(SETTING_MOOD_AFTER_CLOSED)) { day, state, mood, on, closed ->
            if (Feature.MOOD !in on || mood == null || closed == day.toString()) return@combine null
            if (mood.after == null && (state == null || state.view.day != day || !MoodInsights.allDone(state.view.dueToday))) return@combine null
            MoodAfterView(mood.score, mood.after)
        }.state(null)

    fun setMoodAfter(score: Int) {
        viewModelScope.launch { repository.setMoodAfter(currentDay(), score) }
    }

    /**
     * "Not now" before answering, or closing the answer: either way it is gone until tomorrow,
     * and so are the morning's notes under it.
     */
    fun closeMoodAfter() {
        viewModelScope.launch {
            repository.setSetting(SETTING_MOOD_AFTER_CLOSED, currentDay().toString())
            repository.setSetting(SETTING_MOOD_MORE_CLOSED, currentDay().toString())
        }
    }

    // The mood journal: notes and the things behind each day's mood

    /**
     * Whether Today shows "What's behind it?" under the morning's mood: from when it is given
     * until "Done", or until the check-in once everything is done takes over.
     */
    val moodMore: StateFlow<Boolean> =
        combine(today, todayMood, features, dao.observeSetting(SETTING_MOOD_MORE_CLOSED), moodAfter) { day, mood, on, closed, after ->
            Feature.MOOD in on && mood != null && mood.after == null && closed != day.toString() && after == null
        }.state(false)

    fun closeMoodMore() {
        viewModelScope.launch { repository.setSetting(SETTING_MOOD_MORE_CLOSED, currentDay().toString()) }
    }

    /** The things offered at the check-ins: the app's own until the user changes the list. */
    val moodFactorList: StateFlow<List<String>> = dao.observeSetting(MoodFactors.SETTING).map { MoodFactors.list(it) }.state(MoodFactors.BUILT_IN)

    /** Picks a thing behind today's mood, or takes it off. */
    fun toggleMoodFactor(name: String, day: LocalDate = currentDay()) {
        viewModelScope.launch { repository.toggleMoodFactor(day, name) }
    }

    /** The user's own thing, added to the list and picked for [day] (today unless said). */
    fun addMoodFactor(name: String, day: LocalDate? = currentDay()) {
        viewModelScope.launch { repository.addMoodFactor(name, day) }
    }

    /** Takes a thing off the list offered; days that had it keep it. */
    fun removeMoodFactor(name: String) {
        viewModelScope.launch { repository.setMoodFactorList(MoodFactors.remove(repository.moodFactorList(), name)) }
    }

    /** Puts back any of the app's own things that were taken off the list. */
    fun restoreMoodFactors() {
        viewModelScope.launch { repository.setMoodFactorList(MoodFactors.withBuiltIns(repository.moodFactorList())) }
    }

    /** Today's note from the morning's check-in, or with [after] from the one once everything is done. */
    fun setMoodNote(note: String, after: Boolean) {
        viewModelScope.launch { repository.setMoodNote(currentDay(), note, after) }
    }

    /** Every day with a mood, newest first, for the journal. */
    val moodJournal: StateFlow<List<Mood>> =
        dao.observeMoodsSince(Long.MIN_VALUE).map { rows -> rows.map { it.toCore() }.sortedByDescending { it.day } }.state(emptyList())

    /** A day changed in the journal. */
    fun editMoodDay(mood: Mood) {
        viewModelScope.launch { repository.editMoodDay(mood) }
    }

    /** On a morning that started low (1 or 2): a goal still to do today that usually lifts the user. */
    val moodStart: StateFlow<Goal?> = combine(todayState, todayMood, moodFindings) { state, mood, findings ->
        if (state == null || mood == null || mood.after != null || mood.score > 2) return@combine null
        val view = state.view
        MoodInsights.startWith(findings, view.dueToday + view.thisWeek + view.thisMonth + view.thisYear)
    }.state(null)

    /** Puts a goal right after another in a habit chain, from "Mood and your goals". */
    fun setGoalAfter(id: Long, after: Long) {
        viewModelScope.launch { repository.setGoalAfter(id, after) }
    }

    // Ideas to level up a skill area

    /** Every goal not removed, for the ideas: what the user does already. */
    val currentGoals: StateFlow<List<Goal>> = dao.observeGoals().map { rows -> rows.map { it.toCore() }.filter { !it.archived } }.state(emptyList())

    /** Ideas the user said aren't for them. */
    val hiddenIdeas: StateFlow<Set<String>> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.Ideas.HIDDEN).map { io.github.randomtools3377.checklist.core.Ideas.decodeHidden(it) }.state(emptySet())

    fun hideIdea(key: String) {
        viewModelScope.launch {
            val now = io.github.randomtools3377.checklist.core.Ideas.decodeHidden(dao.setting(io.github.randomtools3377.checklist.core.Ideas.HIDDEN))
            repository.setSetting(io.github.randomtools3377.checklist.core.Ideas.HIDDEN, io.github.randomtools3377.checklist.core.Ideas.encodeHidden(now + key))
        }
    }

    /** Brings back the ideas hidden for [area]. */
    fun unhideIdeas(area: io.github.randomtools3377.checklist.core.SkillArea) {
        viewModelScope.launch {
            val prefix = area.name.lowercase() + ":"
            val now = io.github.randomtools3377.checklist.core.Ideas.decodeHidden(dao.setting(io.github.randomtools3377.checklist.core.Ideas.HIDDEN))
            repository.setSetting(io.github.randomtools3377.checklist.core.Ideas.HIDDEN, io.github.randomtools3377.checklist.core.Ideas.encodeHidden(now.filterNot { it.startsWith(prefix) }.toSet()))
        }
    }

    /** An idea waiting for the goal editor to open: a new goal, or a step up of goal [AreaIdea.goalId]. */
    private val editorIdea = MutableStateFlow<io.github.randomtools3377.checklist.core.AreaIdea?>(null)

    /** Hands [idea] to the goal editor about to open, which fills it in for the user to check and save. */
    fun useIdea(idea: io.github.randomtools3377.checklist.core.AreaIdea) {
        editorIdea.value = idea
    }

    /** The idea for the editor of goal [id] (0 for a new one), once. */
    fun takeIdea(id: Long): io.github.randomtools3377.checklist.core.AreaIdea? {
        val idea = editorIdea.value ?: return null
        if ((idea.goalId ?: 0L) != id) return null
        editorIdea.value = null
        return idea
    }

    /** Asks the AI for new ideas for [area]; [onResult] gets them, or why there are none. */
    fun aiIdeas(area: io.github.randomtools3377.checklist.core.SkillArea, shown: List<String>, onResult: (List<io.github.randomtools3377.checklist.core.AreaIdea>, String?) -> Unit) {
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.AreaIdeasFromAi
            val level = character.value?.skillLevel(area)?.level ?: 1
            val prompt = ask.prompt(area, level, currentGoals.value, shown)
            phone.askAi(ask.SYSTEM, prompt, ask.SCHEMA, ask.MAX_TOKENS).fold(
                onSuccess = { answer ->
                    val ideas = ask.parse(answer, area, shown + currentGoals.value.map { it.name })
                    onResult(ideas, if (ideas.isEmpty()) "The AI's ideas didn't make sense. Try again." else null)
                },
                onFailure = { e -> onResult(emptyList(), "The AI couldn't answer: ${e.message ?: "unknown error"}.") },
            )
        }
    }

    /** The new level has been cheered. */
    fun levelSeen() {
        viewModelScope.launch {
            character.value?.let { repository.setSetting(io.github.randomtools3377.checklist.core.Checkins.LEVEL_SEEN, it.level.level.toString()) }
        }
    }

    /** The weekly planning card on Today: from Monday, until this week is planned or put off. */
    val showWeekPlan: StateFlow<Boolean> = combine(today, features, dao.observeSetting(SETTING_WEEK_PLANNED)) { day, on, planned ->
        Feature.WEEKLY_PLANNING in on && planned != YearReview.weekOf(day).toString()
    }.state(false)

    /** This week is planned, or put off: the card goes until next Monday. */
    fun weekPlanned() {
        viewModelScope.launch { repository.setSetting(SETTING_WEEK_PLANNED, YearReview.weekOf(currentDay()).toString()) }
    }

    /** Last week, summed up, for weekly planning. */
    val lastWeek: StateFlow<ReviewSummary?> = today.flatMapLatest { day ->
        val (from, to) = Review.range(day.minusDays(7), Period.WEEKLY)
        val windowsFrom = Days.earliestOpenDay(from).minusDays(7)
        combine(
            combine(dao.observeGoals(), hidingPrivate) { goals, hide -> goals to hide },
            dao.observeHabitTicksSince(windowsFrom.toEpochDay()),
            dao.observeLapsesSince(windowsFrom.toEpochDay()),
            dao.observeEntries(from.toEpochDay(), to.toEpochDay()),
            dao.observeGaps(),
        ) { (goals, hide), ticks, lapsesRows, entries, gapRows ->
            Review.summarize(
                from = from, to = to, today = day, goals = goals.map { it.toCore() }, ticks = ticks.map { it.toCore() },
                lapses = lapsesRows.map { it.toCore() }, entries = entries.map { it.toCore() }, gaps = gapRows.map { it.toCore() },
                hidePrivate = hide,
            )
        }
    }.state(null)

    /** Turns a feature on or off. The phone calendar also needs its permission (see Settings). */
    fun setFeature(feature: Feature, on: Boolean) {
        viewModelScope.launch {
            repository.setSetting(feature.key, if (on) feature.onValue else "0")
            // Shields count from the day they are first turned on, never earlier.
            if (on && feature == Feature.SHIELDS && dao.setting(io.github.randomtools3377.checklist.core.Shields.FROM) == null) {
                repository.setSetting(io.github.randomtools3377.checklist.core.Shields.FROM, currentDay().toString())
            }
        }
    }

    /** The welcome card on Today: for someone new, until it is answered or a goal is added. */
    val showWelcome: StateFlow<Boolean> = combine(dao.observeSetting(SETTING_GUIDE), dao.observeGoals(), dao.observeRemovedGoals()) { guide, goals, removed ->
        guide != "done" && goals.isEmpty() && removed.isEmpty()
    }.state(false)

    fun closeWelcome() {
        viewModelScope.launch { repository.setSetting(SETTING_GUIDE, "done") }
    }

    /** Adds the guide's example goals and rewards. */
    fun addExamples(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addExamples(currentDay())
            onDone()
        }
    }

    /** Adds a to-do from the box on Today; [onAdded] gets its id, to open it for a date. */
    fun addTodo(name: String, onAdded: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch { onAdded(repository.saveTodo(TodoDraft(id = 0, name = name.trim()))) }
    }

    /** The user's current day, with the day ending when they set it to. */
    fun currentDay(): LocalDate = Days.dayOf(nowLocal(), settings.value.dayEndHour)

    fun saveProject(draft: ProjectDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveProject(draft)
            onDone()
        }
    }

    /** Goals the user removed, to bring back. Private ones are left out while they are locked. */
    val removedGoals: StateFlow<List<Goal>> = combine(dao.observeRemovedGoals(), hidingPrivate) { rows, hide ->
        rows.map { it.toCore() }.filter { !hide || !it.private }
    }.state(emptyList())

    /** The categories the goals use, offered in the goal editor. */
    val categories: StateFlow<List<String>> = dao.observeGoals().map { rows -> Categories.of(rows.map { it.toCore() }) }.state(emptyList())

    fun duplicateGoal(id: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch { repository.duplicateGoal(id)?.let(onDone) }
    }

    fun restoreGoal(id: Long) {
        viewModelScope.launch { repository.restoreGoal(id) }
    }

    /** Several goals at once: a category for them all ("" for none). */
    fun setCategories(ids: List<Long>, category: String) {
        viewModelScope.launch { repository.setCategories(ids, category) }
    }

    /** Several goals at once: how hard they are, which suggests their points. */
    fun setEfforts(ids: List<Long>, effort: Effort?) {
        viewModelScope.launch { repository.setEfforts(ids, effort) }
    }

    /** Several goals removed at once; the Undo bar brings them all back. */
    fun removeGoals(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { repository.removeGoal(it) }
            offerUndo(if (ids.size == 1) "Removed 1 goal" else "Removed ${ids.size} goals") { ids.forEach { repository.restoreGoal(it) } }
        }
    }

    fun removeGoal(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val name = repository.goal(id)?.name
            repository.removeGoal(id)
            onDone()
            if (name != null) offerUndo("Removed $name") { repository.restoreGoal(id) }
        }
    }

    fun moveGoal(id: Long, by: Int) {
        viewModelScope.launch { repository.moveGoal(id, by) }
    }

    fun saveReward(draft: RewardDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveReward(draft)
            onDone()
        }
    }

    fun removeReward(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.removeReward(id)
            onDone()
        }
    }

    fun moveReward(id: Long, by: Int) {
        viewModelScope.launch { repository.moveReward(id, by) }
    }

    // The budget.

    /** The user's pay and usual week, once set. */
    val payPlan: StateFlow<PayPlan?> = dao.observeSetting(PayPlan.SETTING).map { PayPlan.decode(it) }.state(null)

    /** The budget's categories, in the user's order, removed ones included (for the history). */
    val moneyCategories: StateFlow<List<MoneyCategory>> =
        dao.observeMoneyCategories().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /** The month the Budget tab shows, by its first day; null for this month. */
    private val budgetMonth = MutableStateFlow<LocalDate?>(null)

    /** The Budget tab's month: its money, its changed work days, and the pay to work out its bars from. */
    val budget: StateFlow<BudgetView?> = combine(budgetMonth, today) { shown, _ -> shown ?: Budget.monthOf(nowLocal().date) }
        .flatMapLatest { first ->
            val days = Pay.monthDays(first)
            combine(
                payPlan,
                dao.observeWorkDays(first.toEpochDay(), days.last().toEpochDay()),
                dao.observeMoney(first.toEpochDay(), days.last().toEpochDay()),
                moneyCategories,
            ) { plan, changed, money, categories ->
                BudgetView(
                    first = first,
                    thisMonth = first == Budget.monthOf(nowLocal().date),
                    plan = plan,
                    changes = changed.map { it.toCore() }.associateBy { it.day },
                    entries = money.map { it.toCore() },
                    categories = categories,
                )
            }
        }.state(null)

    /** Moves the Budget tab [by] months, never past this month. */
    fun showBudgetMonth(by: Int) {
        val now = Budget.monthOf(nowLocal().date)
        var shown = budgetMonth.value ?: now
        repeat(kotlin.math.abs(by)) {
            shown = if (by < 0) Budget.monthOf(shown.minusDays(1)) else Pay.monthDays(shown).last().plusDays(1)
        }
        budgetMonth.value = if (shown >= now) null else shown
    }

    /** Bills and subscriptions, stopped ones included (for the history). */
    val bills: StateFlow<List<Bill>> = dao.observeBills().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /** Saving goals with what's in each. */
    val savings: StateFlow<List<SavingProgress>> = combine(dao.observeSavingGoals(), dao.observeSavingMoney()) { goals, money ->
        SavingProgress.of(goals.map { it.toCore() }, money.map { it.toCore() })
    }.state(emptyList())

    init {
        // Bills count as spent on their days, by themselves, while Budget is on.
        viewModelScope.launch {
            combine(features, today, dao.observeBills()) { on, _, _ -> Feature.BUDGET in on }
                .collectLatest { on -> if (on) repository.countBills(nowLocal().date) }
        }
    }

    suspend fun loadBill(id: Long): Bill? = repository.bill(id)

    fun saveBill(bill: Bill, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveBill(bill, nowLocal().date)
            onDone()
        }
    }

    fun removeBill(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.removeBill(id)
            onDone()
        }
    }

    fun saveSavingGoal(goal: SavingGoal) {
        viewModelScope.launch { repository.saveSavingGoal(goal) }
    }

    fun removeSavingGoal(id: Long) {
        viewModelScope.launch { repository.removeSavingGoal(id) }
    }

    /** Puts money into a saving goal, or takes it out. With the budget game on, reaching it pays. */
    fun moveSaving(goal: SavingGoal, cents: Long, out: Boolean) {
        viewModelScope.launch {
            repository.moveSaving(goal.id, cents, out, nowLocal().date, goal.name)
            settleMoneyGame()
        }
    }

    /** The budget game's points just paid, to celebrate; null once seen. */
    val moneyPaid = MutableStateFlow<List<MoneyGame.Payout>?>(null)

    private suspend fun settleMoneyGame() {
        val on = features.value
        if (Feature.BUDGET !in on || Feature.BUDGET_GAME !in on) return
        val paid = repository.settleMoneyGame(nowLocal().date)
        if (paid.isNotEmpty()) moneyPaid.value = paid
    }

    fun moneyPaidSeen() {
        moneyPaid.value = null
    }

    init {
        // The budget game pays for the days gone by when a new day starts, or when it's turned on.
        viewModelScope.launch {
            combine(features, today) { on, day -> (Feature.BUDGET in on && Feature.BUDGET_GAME in on) to day }
                .collectLatest { (on, _) -> if (on) settleMoneyGame() }
        }
    }

    /** Today in the budget game: the day's share, what's spent, and the money streak; null while it's off. */
    val moneyGame: StateFlow<MoneyGameView?> = combine(features, budget, savings) { on, view, _ ->
        if (Feature.BUDGET !in on || Feature.BUDGET_GAME !in on || view == null || !view.thisMonth) return@combine null
        val today = nowLocal().date
        val entries = repository.moneyThisAndLastMonth(today)
        val shares = mutableMapOf<LocalDate, Double?>()
        val shareOf = { d: LocalDate -> shares.getOrPut(Budget.monthOf(d)) { MoneyGame.share(view.plan, Budget.monthOf(d), view.changes, entries) } }
        val from = repository.moneyGameFrom() ?: today
        val share = shareOf(today)
        val spent = MoneyGame.spent(today, entries)
        val before = MoneyGame.streak(from, today.minusDays(1), shareOf, entries)
        MoneyGameView(share, spent, if (share != null && MoneyGame.day(spent, share) != MoneyGame.Day.OVER) before + 1 else before, before)
    }.state(null)

    /** Puts in the starting categories the first time the budget is opened. */
    fun startBudget() {
        viewModelScope.launch { repository.startBudget() }
    }

    fun savePay(plan: PayPlan, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.savePay(plan)
            onDone()
        }
    }

    suspend fun loadMoney(id: Long): MoneyEntry? = repository.moneyEntry(id)

    fun saveMoney(entry: MoneyEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveMoney(entry)
            onDone()
        }
    }

    fun removeMoney(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.removeMoney(id)
            onDone()
        }
    }

    fun saveMoneyCategory(category: MoneyCategory) {
        viewModelScope.launch { repository.saveMoneyCategory(category) }
    }

    fun moveMoneyCategory(id: Long, by: Int) {
        viewModelScope.launch { repository.moveMoneyCategory(id, by) }
    }

    fun removeMoneyCategory(id: Long) {
        viewModelScope.launch { repository.removeMoneyCategory(id) }
    }

    /** Changes [WorkDay.day]: other hours, or a day off with or without pay. */
    fun setWorkDay(change: WorkDay) {
        viewModelScope.launch { repository.setWorkDay(change) }
    }

    /** Puts [day] back to the usual week. */
    fun usualWorkDay(day: LocalDate) {
        viewModelScope.launch { repository.usualWorkDay(day) }
    }

    /** A reward bought with a money value counts as spent while Budget is on. */
    private suspend fun countRewardSpending(purchaseId: Long, rewardId: Long, name: String) {
        if (Feature.BUDGET !in features.value) return
        val value = repository.reward(rewardId)?.value?.takeIf { it > 0 } ?: return
        repository.rewardBought(purchaseId, name, value, nowLocal().date)
    }

    private suspend fun returnAndUncount(purchaseId: Long): Boolean {
        val returned = book.returnPurchase(purchaseId)
        if (returned) repository.rewardReturned(purchaseId)
        return returned
    }

    fun setDayEndHour(hour: Int) {
        viewModelScope.launch { repository.setDayEndHour(hour) }
    }

    fun setDefaultFinish(time: LocalTime?) {
        viewModelScope.launch { repository.setDefaultFinish(time) }
    }

    fun setDayStart(time: LocalTime?) {
        viewModelScope.launch { repository.setDayStart(time) }
    }

    fun setMorningPlan(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_MORNING_PLAN, if (on) "1" else "0") }
    }

    fun setTodayOrder(order: String) {
        viewModelScope.launch { repository.setTodayOrder(order) }
    }

    // Export and import, through files the user picks with the system dialogs.

    /** Writes everything to [uri]. Reports a plain message either way. */
    fun exportTo(file: PickedFile, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val text = repository.exportBackup()
                phone.writeText(file, text)
                phone.markBackedUp()
                refreshBackup()
                "Exported. Keep the file somewhere safe."
            } catch (e: Exception) {
                "Couldn't write the file. Nothing was exported."
            }
            onDone(message)
        }
    }

    /** Writes the points history to [uri] as a spreadsheet. Reports a plain message either way. */
    fun exportSpreadsheet(file: PickedFile, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val text = repository.historySpreadsheet()
                phone.writeText(file, text)
                "Saved. Open it in any spreadsheet app."
            } catch (e: Exception) {
                "Couldn't write the file."
            }
            onDone(message)
        }
    }

    /** The automatic backups in the chosen folder, newest first, to pick one to restore. */
    fun listAutoBackups(onList: (List<Pair<String, PickedFile>>) -> Unit) {
        viewModelScope.launch {
            onList(runCatching { phone.autoBackups() }.getOrDefault(emptyList()))
        }
    }

    /** Reads a backup from [uri] without changing anything, so the user can confirm it first. */
    fun readBackup(file: PickedFile, onRead: (Backup?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val text = phone.readText(file)
                onRead(Backup.decode(text), null)
            } catch (e: BackupError) {
                onRead(null, e.message)
            } catch (e: Exception) {
                onRead(null, "Couldn't read that file.")
            }
        }
    }

    /** Replaces everything with [backup]. */
    fun restore(backup: Backup, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                repository.restore(backup)
                "Imported. Everything now matches the backup."
            } catch (e: Exception) {
                "Couldn't import. Nothing was changed."
            }
            onDone(message)
        }
    }
}
