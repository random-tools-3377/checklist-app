package io.github.randomtools3377.checklist.ui

import kotlinx.coroutines.flow.mapLatest
import io.github.randomtools3377.checklist.data.SETTING_CALENDAR_IDS
import io.github.randomtools3377.checklist.data.SETTING_CALENDAR
import io.github.randomtools3377.checklist.calendar.PhoneCalendarInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.BackupError
import io.github.randomtools3377.checklist.core.BackupSummary
import io.github.randomtools3377.checklist.data.AutoBackupFiles
import io.github.randomtools3377.checklist.data.BackupCrypto
import io.github.randomtools3377.checklist.data.BackupKey
import io.github.randomtools3377.checklist.data.WrongPassphraseException
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.DayRecord
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
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.payFor
import io.github.randomtools3377.checklist.core.PerfectDays
import io.github.randomtools3377.checklist.core.RepeatPrice
import io.github.randomtools3377.checklist.core.Boost
import io.github.randomtools3377.checklist.core.Interest
import io.github.randomtools3377.checklist.core.GoalCheck
import io.github.randomtools3377.checklist.core.RewardCheck
import io.github.randomtools3377.checklist.core.BalanceCheck
import io.github.randomtools3377.checklist.core.SavingFor
import io.github.randomtools3377.checklist.core.Suggest
import io.github.randomtools3377.checklist.core.Ramp
import io.github.randomtools3377.checklist.core.DifficultySetting
import io.github.randomtools3377.checklist.core.Difficulty
import io.github.randomtools3377.checklist.core.DoneItem
import io.github.randomtools3377.checklist.core.DoneList
import io.github.randomtools3377.checklist.data.SETTING_STOPWATCH
import io.github.randomtools3377.checklist.data.SETTING_BACKUP_KEY
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
import io.github.randomtools3377.checklist.core.CelebrationSounds
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
import io.github.randomtools3377.checklist.core.Gap
import io.github.randomtools3377.checklist.core.GapSettings
import io.github.randomtools3377.checklist.core.Gaps
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Review
import io.github.randomtools3377.checklist.core.FocusTime
import io.github.randomtools3377.checklist.core.FocusTotals
import io.github.randomtools3377.checklist.core.GoalPage
import io.github.randomtools3377.checklist.core.GoalPageView
import io.github.randomtools3377.checklist.core.StretchDone
import io.github.randomtools3377.checklist.core.StretchDoneList
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
import io.github.randomtools3377.checklist.data.SETTING_MOOD_ONLY
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
import io.github.randomtools3377.checklist.core.PayPlans
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import io.github.randomtools3377.checklist.core.MoneyKind
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

/** The shop: [perDay] is what the user usually earns a day, for how long what is still missing takes. */
data class ShopView(val balance: Int, val rewards: List<RewardRow>, val bought: List<Purchase>, val perDay: Double? = null)

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
    /** The first look today: it waves hello, once (see [MainViewModel.greeted]). */
    val greet: Boolean = false,
)

/**
 * The mood check-in once everything due today is done: the morning's mood, and the answer once
 * given (null while it is still asked). With something [skipped] today, the question doesn't say
 * everything was done.
 */
data class MoodAfterView(val morning: Int, val after: Int?, val skipped: Boolean = false)

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
    /** Today's record so far, as the streaks count it. */
    val todayRecord: DayRecord? = null,
    /** Yesterday's record, once tracking had begun by then; for the morning card. */
    val yesterdayRecord: DayRecord? = null,
    /** The best the main streak has stood at over the days counted, never below the streak now. */
    val bestStreak: Int = 0,
    /** The last day that counted before today broke the main streak. */
    val brokeBefore: Boolean = false,
    /** For the character: the minute of the day each goal is usually ticked at, from the user's own ticks. */
    val usualMinute: Map<Long, Int> = emptyMap(),
    /** For the character: more is done today than on any earlier day this week. */
    val bestDayThisWeek: Boolean = false,
)

/** For the character: what the user has already seen, and what happened a moment ago. */
private data class CompanionSeen(
    val level: String?,
    val skills: String?,
    val waved: String?,
    val justNow: io.github.randomtools3377.checklist.core.JustNow?,
    val event: io.github.randomtools3377.checklist.core.CompanionEvent?,
)

/** For the character: the user's day, and the numbers it may mention. */
private data class CompanionKnows(
    val settings: Settings,
    val lateHours: Int,
    val week: WeekProgress?,
    val race: io.github.randomtools3377.checklist.core.RaceStanding?,
    val saving: SavingFor?,
)

/** What Today needs besides goals and ticks. */
private data class Extras(val lapses: List<Lapse>, val checks: List<Check>, val boostOn: Boolean, val events: List<Busy>, val streakBonusOn: Boolean)

/** Something just done, which the Undo bar can take back for a few seconds. */
/** [action] is the word on its button: "Undo", unless it is an offer of something else. */
class Undoable(val message: String, /** What to say went back, once it is undone. */ val undone: String? = null, val action: String = "Undo", val undo: () -> Unit)

/** How things stood just before a tick, to say what the tick changed. */
private class BeforeTick(
    val state: TodayState?,
    val quests: io.github.randomtools3377.checklist.core.DayQuests?,
    val week: WeekProgress?,
    val race: io.github.randomtools3377.checklist.core.RaceStanding?,
)

/** Which extras are on: the boost, the perfect-day bonus (0 is off) and repeat-buy prices. */
data class Boosts(val boost: Boolean = false, val perfectDayBonus: Int = 0, val repeatPrice: Boolean = false)

/** What the balance check shows: usual earnings now, and each reward and goal judged. */
data class BalanceCheckView(val perDay: Double?, val rate: Difficulty, val rewards: List<RewardCheck>, val goals: List<GoalCheck>)

/** A Start and Stop timing of a goal with an amount, running since [startedAt] (epoch milliseconds). */
data class Stopwatch(val goalId: Long, val startedAt: Long)

/** What the privacy lock covers, as chosen in Settings. */
/** Today in the budget game: its share, what's spent, and good money days in a row with and without today. */
data class MoneyGameView(val share: Double?, val spentCents: Long, val streak: Int, val streakBefore: Int, /** The user's own daily amount in cents, when set. */ val ownCents: Long? = null)

/** First-time tips: whether they're on, and the guides (by name) already shown. */
data class TipsState(val on: Boolean, val seen: Set<String>)

/** The Budget tab's month: [first] is its first day. */
data class BudgetView(
    val first: LocalDate,
    val thisMonth: Boolean,
    /** The pay as it is now. */
    val plan: PayPlan?,
    val changes: Map<LocalDate, WorkDay>,
    val entries: List<MoneyEntry>,
    val categories: List<MoneyCategory>,
    /** The pay over time, so each day is worked out with the pay it had. */
    val plans: PayPlans? = null,
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

    /**
     * The goals in the lists. The limits of "at most" goals aren't kept in the goals themselves
     * (see AtMostLimits), so the goals are read again whenever a limit changes, with the limits
     * in place first.
     */
    private val goalRows: Flow<List<io.github.randomtools3377.checklist.data.GoalEntity>> =
        combine(dao.observeGoals(), dao.observeSetting(io.github.randomtools3377.checklist.core.AtMost.SETTING)) { rows, limits ->
            io.github.randomtools3377.checklist.data.AtMostLimits.current = io.github.randomtools3377.checklist.core.AtMost.decode(limits)
            rows
        }
            // One shared run for every screen. Run once per reader, a reader that saw the goals
            // change before the limits could put the old limits back after another had the new
            // ones, and a goal just made "at most" then showed as an ordinary habit.
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

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
    private val privateIds: Flow<Set<Long>> = combine(goalRows, dao.observeRemovedGoals()) { shown, removed ->
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
            goalRows,
            dao.observeSteps(),
            dao.observeHabitTicksSince(since.toEpochDay()),
            dao.observeStepTicks(),
            dao.observeBalance(),
        ) { goals, steps, habitTicks, stepTicks, balance ->
            val ticks = (habitTicks + stepTicks).map { it.toCore() }
            val usual = io.github.randomtools3377.checklist.data.DayOrders.usualMinutes(habitTicks, day)
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
            val hidePrivate = s.locks.privateGoals && !isUnlocked
            val shownGoals = if (hidePrivate) raw.goals.filter { !it.private } else raw.goals
            // The order, the pace and what's next come from the one place the widgets, the tile
            // and the morning plan ask too (DayOrder). It also gives goals with an amount the
            // time what is left takes, so pacing and the order can plan for it.
            val built = TodayList.build(day, shownGoals, raw.steps, raw.ticks, raw.balance, lapses, checks)
            val paceSettings = PaceSettings(s.dayEndHour, s.defaultFinish, s.dayStart)
            val recommended = s.order == ORDER_RECOMMENDED
            val ordered = io.github.randomtools3377.checklist.core.DayOrder.of(
                moment, day, built.dueToday, paceSettings, own = !recommended, usualMinute = raw.usual, busy = events, ticks = raw.ticks,
            )
            val todayView = built.copy(
                dueToday = ordered.rows,
                thisWeek = io.github.randomtools3377.checklist.core.DayOrder.timed(built.thisWeek, raw.ticks),
                thisMonth = io.github.randomtools3377.checklist.core.DayOrder.timed(built.thisMonth, raw.ticks),
                thisYear = io.github.randomtools3377.checklist.core.DayOrder.timed(built.thisYear, raw.ticks),
            )
            val busy = ordered.busy
            val pace = ordered.pace
            val plan = ordered.planned
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
                usualMinute = raw.usual,
                bestDayThisWeek = Days.periodStart(day, Period.WEEKLY).let { monday ->
                    val earlier = tracked.filter { it.day >= monday && it.day < day && it.due > 0 }
                    val done = records.lastOrNull()?.takeIf { it.day == day }?.done ?: 0
                    earlier.isNotEmpty() && done > earlier.maxOf { it.done }
                },
                todayRecord = records.lastOrNull()?.takeIf { it.day == day },
                yesterdayRecord = tracked.lastOrNull { it.day == day.minusDays(1) },
                bestStreak = maxOf(io.github.randomtools3377.checklist.core.BestStreaks.of(records, s.streak).done, streaks.done),
                brokeBefore = tracked.lastOrNull { it.day < day && StreakCounter.mainDay(it, s.streak) != null }
                    ?.let { StreakCounter.mainDay(it, s.streak) == false } == true,
            )
        }
    }.state(null)

    val goalsView: StateFlow<GoalsView?> = combine(goalRows, dao.observeSteps(), settings, unlocked, today) { goals, steps, s, isUnlocked, day ->
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

    /** What the calendar widget shows ("Calendar widget shows" in Appearance); the widget redraws when one changes. */
    val calendarShows: StateFlow<io.github.randomtools3377.checklist.core.CalendarShows> = combine(
        io.github.randomtools3377.checklist.core.CalendarShow.entries.map { dao.observeSetting(it.key) },
    ) { v ->
        io.github.randomtools3377.checklist.core.CalendarShows(
            io.github.randomtools3377.checklist.core.CalendarShow.entries.filterIndexed { i, show -> show.isOn(v[i]) }.toSet(),
        )
    }.state(io.github.randomtools3377.checklist.core.CalendarShows())

    fun setCalendarShow(show: io.github.randomtools3377.checklist.core.CalendarShow, on: Boolean) {
        viewModelScope.launch { repository.setSetting(show.key, show.valueOf(on)) }
    }

    /** "Character on the balance and streaks widget" in Appearance: off to start; the widget redraws when it changes. */
    val widgetCharacter: StateFlow<Boolean> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.Checkins.WIDGET_FACE).map { it == "1" }.state(false)

    fun setWidgetCharacter(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Checkins.WIDGET_FACE, if (on) "1" else "0") }
    }

    // The optional AI, in the separate Checklist AI app.

    /** How Checklist AI stands; null until checked. */
    val aiStatus = MutableStateFlow<io.github.randomtools3377.checklist.data.AiStatus?>(null)

    fun refreshAi() {
        viewModelScope.launch { aiStatus.value = phone.aiStatus() }
    }

    /**
     * One question for the AI. The app stops waiting after two minutes, or when the job this is
     * called from is dropped ("Stop waiting"); Checklist AI finishes on its own and the answer
     * is thrown away. A failure never carries the engine's own words past [AiTrouble.plain].
     */
    private suspend fun ai(system: String, prompt: String, schema: String?, maxTokens: Int): Result<String> {
        val asked = viewModelScope.async { phone.askAi(system, prompt, schema, maxTokens) }
        return kotlinx.coroutines.withTimeoutOrNull(io.github.randomtools3377.checklist.core.AiTrouble.TIMEOUT_MILLIS) { asked.await() }
            ?: Result.failure(IllegalStateException(io.github.randomtools3377.checklist.core.AiTrouble.TIMED_OUT))
    }

    private fun trouble(e: Throwable): String = io.github.randomtools3377.checklist.core.AiTrouble.plain(e.message)

    private var aiWarmed = false

    /** Loads the AI the first time one of its cards shows, so the first real answer comes sooner. */
    fun warmAi() {
        if (aiWarmed) return
        viewModelScope.launch {
            val status = aiStatus.value ?: phone.aiStatus().also { aiStatus.value = it }
            if (!status.usable || aiWarmed) return@launch
            aiWarmed = true
            runCatching { phone.askAi("Reply with OK.", "OK?", null, 16) }
        }
    }

    /**
     * Reads a goal from [sentence]: with [useAi], the AI's reading put right by the app's own;
     * without, or when the AI can't answer, the app's own reading with the name as typed.
     * [onResult] gets the goal, whether the AI took part, and a note when something went wrong.
     */
    fun goalFromSentence(sentence: String, useAi: Boolean = true, onResult: (io.github.randomtools3377.checklist.core.GoalIdea?, Boolean, String?) -> Unit): Job =
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.GoalFromSentence
            val own = io.github.randomtools3377.checklist.core.SentenceReader.idea(sentence)
            if (!useAi) return@launch onResult(own, false, null)
            ai(ask.SYSTEM, ask.prompt(sentence), ask.SCHEMA, ask.MAX_TOKENS).fold(
                onSuccess = { answer -> ask.parse(answer, sentence)?.let { onResult(it, true, null) } ?: onResult(own, false, null) },
                onFailure = { e -> onResult(own, false, trouble(e)) },
            )
        }

    /**
     * Asks the AI for steps for a project or to-do called [name], steered by [hint], different
     * from the steps [existing] and those already [shown]; [onResult] gets them, or why there are none.
     */
    fun projectSteps(
        name: String,
        existing: List<String> = emptyList(),
        shown: List<String> = emptyList(),
        hint: String = "",
        onResult: (List<String>, String?) -> Unit,
    ): Job = viewModelScope.launch {
        val ask = io.github.randomtools3377.checklist.core.ProjectSteps
        ai(ask.SYSTEM, ask.prompt(name, existing, shown, hint), ask.SCHEMA, ask.MAX_TOKENS).fold(
            onSuccess = { answer -> onResult(ask.parse(answer, existing + shown), null) },
            onFailure = { e -> onResult(emptyList(), trouble(e)) },
        )
    }

    /** The notes on a week that were written and kept, in this app only. */
    val coachNotes: StateFlow<List<io.github.randomtools3377.checklist.core.KeptNote>> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.CoachNotes.SETTING).map { io.github.randomtools3377.checklist.core.CoachNotes.decode(it) }.state(emptyList())

    /**
     * Asks the AI for a note on the week in [summary], with what else the app knows in [more];
     * a note that comes is kept for that week. [onResult] gets it, or why there isn't one.
     */
    fun coachNote(summary: ReviewSummary, hideReasons: Boolean, more: io.github.randomtools3377.checklist.core.WeekMore = io.github.randomtools3377.checklist.core.WeekMore(), onResult: (String?, String?) -> Unit): Job =
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.CoachNote
            ai(ask.SYSTEM, ask.prompt(summary, hideReasons, more), null, ask.MAX_TOKENS).fold(
                onSuccess = { answer ->
                    val text = ask.clean(answer)
                    if (text != null) {
                        val kept = io.github.randomtools3377.checklist.core.CoachNotes.decode(dao.setting(io.github.randomtools3377.checklist.core.CoachNotes.SETTING))
                        val note = io.github.randomtools3377.checklist.core.KeptNote(summary.from, text, reasons = !hideReasons && summary.reasons.isNotEmpty())
                        repository.setSetting(io.github.randomtools3377.checklist.core.CoachNotes.SETTING, io.github.randomtools3377.checklist.core.CoachNotes.encode(io.github.randomtools3377.checklist.core.CoachNotes.put(kept, note)))
                    }
                    onResult(text, null)
                },
                onFailure = { e -> onResult(null, trouble(e)) },
            )
        }

    /** What else a note on the week starting [weekStart] can mention: the week before, the streak, the target, new badges, and the app's own suggestion. */
    fun weekMore(summary: ReviewSummary): io.github.randomtools3377.checklist.core.WeekMore {
        val thisWeek = thisWeek.value?.from == summary.from
        val earned = badges.value.filter { (_, day) -> day >= summary.from && day <= summary.to }.keys.filter { it.shownWith(features.value) }
        return io.github.randomtools3377.checklist.core.WeekMore(
            lastWeek = lastWeek.value?.takeIf { thisWeek },
            streak = todayState.value?.streaks?.done?.takeIf { thisWeek },
            target = weekProgress.value?.takeIf { thisWeek },
            badges = earned.map { it.title },
            advice = io.github.randomtools3377.checklist.core.WeekAdvices.pick(summary, balanceCheck.value?.goals.orEmpty()),
        )
    }

    /** This Monday-to-Sunday week so far, whatever the review is showing: for "How am I doing?" and Ask. */
    val thisWeek: StateFlow<ReviewSummary?> = today.flatMapLatest { day ->
        val (from, to) = Review.range(day, Period.WEEKLY)
        val windowsFrom = Days.earliestOpenDay(from).minusDays(7)
        combine(
            combine(goalRows, hidingPrivate) { goals, hide -> goals to hide },
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

    /**
     * Puts the facts of an Ask [answer] into words with the AI. [onSaid] gets the AI's words, or
     * null when it can't answer or slipped on a number, so the app's own answer shows instead.
     * Nothing asked or answered is kept anywhere.
     */
    fun askInWords(question: String, answer: io.github.randomtools3377.checklist.core.AskAnswer, earlier: List<io.github.randomtools3377.checklist.core.AskTurn>, onSaid: (String?) -> Unit): Job =
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.Asker
            val prompt = ask.prompt(question, answer, earlier) ?: return@launch onSaid(null)
            onSaid(ai(ask.SYSTEM, prompt, null, ask.MAX_TOKENS).getOrNull()?.let { ask.clean(it, answer.facts, question) })
        }

    /** "Say it in words": the facts of [answer] as a friendly paragraph from the AI, or null. */
    fun sayInWords(answer: io.github.randomtools3377.checklist.core.AskAnswer, onSaid: (String?, String?) -> Unit): Job = viewModelScope.launch {
        val ask = io.github.randomtools3377.checklist.core.Asker
        val prompt = ask.wordsPrompt(answer) ?: return@launch onSaid(null, null)
        ai(ask.SYSTEM_WORDS, prompt, null, ask.MAX_TOKENS).fold(
            onSuccess = { said -> onSaid(ask.clean(said, answer.facts), null) },
            onFailure = { e -> onSaid(null, trouble(e)) },
        )
    }

    /**
     * After a goal made with "Describe it" or from an idea is saved: one small next step in the
     * bar at the bottom, like a reminder a little before its time. Leaving the bar alone is "No thanks".
     */
    fun offerFollowUp(name: String) {
        viewModelScope.launch {
            val goals = dao.allGoals().map { it.toCore() }.filter { !it.archived }
            val goal = goals.filter { it.name == name }.maxByOrNull { it.id } ?: return@launch
            val offer = io.github.randomtools3377.checklist.core.FollowUp.pick(goal, goals, features.value) ?: return@launch
            undoBar.tryEmit(
                Undoable(offer.question, action = offer.button) {
                    viewModelScope.launch {
                        when (offer) {
                            is io.github.randomtools3377.checklist.core.FollowUp.After -> repository.setGoalAfter(offer.goalId, offer.afterId)
                            is io.github.randomtools3377.checklist.core.FollowUp.RemindAt -> repository.setGoalReminder(offer.goalId) { it.copy(onTime = true) }
                            is io.github.randomtools3377.checklist.core.FollowUp.RemindBefore -> repository.setGoalReminder(offer.goalId) { it.copy(onTime = true, minutesBefore = offer.minutes) }
                        }
                        runCatching { phone.rescheduleReminders() }
                    }
                },
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

    /** The glow round the edges and the user's own celebration sounds. */
    val celebrationExtras: StateFlow<CelebrationExtras> = combine(
        (
            listOf(
                io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW,
                io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW_AWAY,
                CelebrationSounds.GOALS_SETTING,
            ) + CelebrationSounds.Kind.entries.map { it.setting }
            ).map { dao.observeSetting(it) },
    ) { v ->
        CelebrationExtras.of(
            glow = v[0],
            glowAway = v[1],
            sounds = CelebrationSounds.Kind.entries.mapIndexed { i, kind -> kind to v[3 + i] }.toMap(),
            goalSounds = v[2],
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CelebrationExtras())

    fun setEdgeGlow(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW, if (on) "1" else "0") }
    }

    /** Android: a tick made from a widget or a notification also lights the phone's edges. */
    fun setEdgeGlowAway(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW_AWAY, if (on) "1" else "0") }
    }

    /** The user's own sound for one kind of celebration, already copied in; null puts the app's sound back. */
    fun setOwnSound(kind: CelebrationSounds.Kind, file: String?) {
        viewModelScope.launch { repository.setCelebrationSound(kind, file) }
    }

    /** A goal's own celebration sound, for its editor. */
    suspend fun goalSound(goalId: Long): String? = repository.goalSound(goalId)

    /** Android: reminders light up the screen, and set off the phone's own edge lighting where it has it. */
    val reminderLight: StateFlow<Boolean> =
        dao.observeSetting(io.github.randomtools3377.checklist.data.SETTING_REMINDER_LIGHT).map { it == "1" }.state(false)

    fun setReminderLight(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_REMINDER_LIGHT, if (on) "1" else "0") }
    }

    /** Android: the short, silent note after a tick made on a reminder or a widget; on unless turned off. */
    val tickAnswer: StateFlow<Boolean> =
        dao.observeSetting(io.github.randomtools3377.checklist.data.SETTING_TICK_ANSWER).map { io.github.randomtools3377.checklist.data.tickAnswerOn(it) }.state(true)

    fun setTickAnswer(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_TICK_ANSWER, if (on) "1" else "0") }
    }

    // Late nudges (see Nudges)

    /** "Nudges when something is late" in Settings: on unless turned off. */
    val nudgesOn: StateFlow<Boolean> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.Nudges.SETTING).map { io.github.randomtools3377.checklist.core.Nudges.isOn(it) }.state(true)

    fun setNudgesOn(on: Boolean) {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Nudges.SETTING, if (on) "1" else "0") }
    }

    /** The kinds of nudge message that are switched on (all of them to start). */
    val nudgeTones: StateFlow<Set<io.github.randomtools3377.checklist.core.NudgeTone>> =
        dao.observeSetting(io.github.randomtools3377.checklist.core.Nudges.TONES).map { text ->
            if (text == null) io.github.randomtools3377.checklist.core.NudgeTone.entries.toSet()
            else text.split(",").mapNotNull { name -> io.github.randomtools3377.checklist.core.NudgeTone.entries.firstOrNull { it.name == name.trim() } }.toSet()
        }.state(io.github.randomtools3377.checklist.core.NudgeTone.entries.toSet())

    fun setNudgeTone(tone: io.github.randomtools3377.checklist.core.NudgeTone, on: Boolean) {
        val tones = if (on) nudgeTones.value + tone else nudgeTones.value - tone
        viewModelScope.launch {
            repository.setSetting(io.github.randomtools3377.checklist.core.Nudges.TONES, io.github.randomtools3377.checklist.core.Nudges.encodeTones(tones))
        }
    }

    /** Late nudges as they are set up now: the switch, the numbers from "Points and rules", and the goals stopped for today. */
    private val nudgePlan: Flow<io.github.randomtools3377.checklist.core.NudgePlan> = combine(
        dao.observeSetting(io.github.randomtools3377.checklist.core.Nudges.SETTING),
        dao.observeSetting(io.github.randomtools3377.checklist.core.Nudges.STOPPED),
        dao.observeSetting(io.github.randomtools3377.checklist.core.Rules.SETTING),
    ) { on, stopped, rules ->
        io.github.randomtools3377.checklist.core.Nudges.plan(on, stopped, io.github.randomtools3377.checklist.core.Rules.decode(rules))
    }

    /** The goals on Today that are late with nudges still to come, whose cards offer "No more nudges today". */
    val nudging: StateFlow<Set<Long>> = combine(todayState, nudgePlan, settings) { screen, plan, s ->
        if (screen == null || !plan.on) return@combine emptySet()
        screen.view.dueToday.filter { row ->
            !row.settled && io.github.randomtools3377.checklist.core.Nudges.canStop(row.goal, screen.view.day, screen.now, row.count, s.dayEndHour, plan)
        }.map { it.goal.id }.toSet()
    }.state(emptySet())

    /** "No more nudges today": this goal's late nudges stop until the day ends. */
    fun stopNudges(goalId: Long) {
        viewModelScope.launch {
            io.github.randomtools3377.checklist.data.stopNudges(dao, goalId, currentDay())
            runCatching { phone.rescheduleReminders() }
        }
    }

    /**
     * The goal whose tick was just handed back to the screen, for the celebration that follows:
     * its own sound, and its colour for the glow round the edges.
     */
    var celebrated: Goal? = null
        private set

    private suspend fun celebrating(goalId: Long?) {
        celebrated = goalId?.let { repository.goal(it) }
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

    /**
     * How far along each badge with something to count is (see [Badges.progress]), for the bars
     * on the badges page. Worked out with the badges themselves, a moment after each change.
     */
    val badgeProgress: StateFlow<Map<Badge, io.github.randomtools3377.checklist.core.BadgeProgress>> get() = badgeProgressNow
    private val badgeProgressNow = MutableStateFlow<Map<Badge, io.github.randomtools3377.checklist.core.BadgeProgress>>(emptyMap())

    fun badgesSeen() {
        viewModelScope.launch {
            val earned = Badges.decode(dao.setting(SETTING_BADGES)).keys
            repository.setSetting(SETTING_BADGES_SEEN, earned.joinToString(",") { it.name })
        }
    }

    init {
        // Looks for new badges a moment after each tick, undo or change of day.
        viewModelScope.launch {
            // Money, moods and the extras turned on too, for the badges of the Budget and the mood check-in.
            // The extras are read here, not from [features]: that is made further down, after this runs.
            val extras = combine(Feature.entries.map { dao.observeSetting(it.key) }) { values -> values.toList() }
            val more = combine(dao.observeSavingMoney(), dao.observeMoodsSince(Long.MIN_VALUE), extras) { money, moods, on -> Triple(money.size, moods.size, on) }
                .distinctUntilChanged()
            combine(today, dao.observeBalance(), combine(dao.observeTickCount(), more) { _, _ -> }, settings, dao.observeSetting(SETTING_WEEKLY_TARGET)) { day, _, _, s, target ->
                Triple(day, s.streak, target?.toIntOrNull() ?: 0)
            }.collectLatest { (day, streak, target) ->
                delay(BADGE_CHECK_DELAY_MS)
                findBadges(day, streak, target)
            }
        }
    }

    private suspend fun findBadges(day: LocalDate, streak: StreakSettings, target: Int) {
        val on = features.value
        val ticks = dao.allTicks().map { it.toCore() }
        // The Budget's and the mood check-in's badges need no tick: only their extras on.
        val moods = if (Feature.MOOD in on) dao.allMoods().map { it.toCore() } else emptyList()
        val budget = Feature.BUDGET in on
        if (ticks.isEmpty() && moods.isEmpty() && !budget) {
            // Nothing to count (a fresh start, or everything erased): no bars left over from before.
            badgeProgressNow.value = emptyMap()
            return
        }
        val money = if (budget) dao.allMoney().map { it.toCore() } else emptyList()
        val savingGoals = if (budget) dao.allSavingGoals().map { it.toCore() } else emptyList()
        val goals = dao.allGoals().map { it.toCore() }
        val steps = dao.allSteps().map { it.toCore() }
        val entries = dao.allEntries().map { it.toCore() }
        val lapses = dao.allLapses().map { it.toCore() }
        // Comebacks count from the day tracking began.
        val since = io.github.randomtools3377.checklist.core.Comebacks.since(dao.setting(io.github.randomtools3377.checklist.core.Lapses.TRACKING_SINCE))
        val earned = withContext(Dispatchers.Default) {
            val from = maxOf(ticks.minOfOrNull { it.day } ?: day, day.minusDays(STREAK_DAYS))
            val records = StreakCounter.records(from, day, goals.filter { !it.archived }, ticks.filter { it.stepId == null }, lapses)
            badgeProgressNow.value = Badges.progress(
                day, records, streak, goals, steps, ticks, entries, target,
                on = on, moods = moods, money = money, savingGoals = savingGoals,
            )
            Badges.earned(
                day, records, streak, goals, steps, ticks, entries, target, character = Feature.CHARACTER in on,
                on = on, moods = moods, money = money, savingGoals = savingGoals, trackingSince = since,
            )
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
        combine(dao.observeLapsesSince(day.minusDays(14).toEpochDay()), goalRows) { recent, goals ->
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

    /**
     * "Stop without ticking", or a break ended early. The time focused so far is kept; a break
     * is never counted.
     */
    fun stopFocus() {
        val state = phone.takeFocus()
        val now = nowMillis()
        refreshFocus()
        if (state != null && !state.isBreak) viewModelScope.launch { repository.recordFocus(state, now) }
    }

    /** Drops the timer and keeps nothing of it: after a restore, its goal id can mean another goal. */
    private fun dropFocus() {
        phone.stopFocus()
        breakOffer.value = null
        refreshFocus()
    }

    /**
     * Time's up, or "Done now": the goal is ticked, and the time really focused is kept. The
     * timer is taken from the phone as it is now, so if the phone's alarm already ended it, and
     * ticked the goal and kept the time, neither is done again. With "Break after a focus
     * session" on, a break is offered next. A break that ran out only ends.
     */
    fun finishFocus(onResult: (TickResult) -> Unit) {
        val shown = focus.value
        val state = phone.takeFocus()
        val now = nowMillis()
        refreshFocus()
        if ((state ?: shown)?.isBreak != false) return
        viewModelScope.launch {
            if (state != null) repository.recordFocus(state, now)
            breakOffer.value = repository.focusBreakMinutes().takeIf { it > 0 }
        }
        if (state != null) tickHabit(state.goalId, onResult)
    }

    // The break after a focus session

    /** "Break after a focus session" in Settings, in minutes; 0 while it is off. */
    val focusBreak: StateFlow<Int> = dao.observeSetting(FocusTime.BREAK_SETTING).map { FocusTime.breakMinutes(it) }.state(0)

    fun setFocusBreak(minutes: Int) {
        viewModelScope.launch { repository.setSetting(FocusTime.BREAK_SETTING, FocusTime.breakMinutes(minutes.toString()).toString()) }
    }

    /** A break of this many minutes is on offer, a focus session having just ended; null when none is. */
    val breakOffer = MutableStateFlow<Int?>(null)

    /** "Start a 5 min break": a plain countdown, kept by the phone like the focus timer. */
    fun startBreak() {
        val minutes = breakOffer.value ?: return
        breakOffer.value = null
        phone.startBreak(minutes)
        refreshFocus()
    }

    fun skipBreak() {
        breakOffer.value = null
    }

    /** A tab to open, asked for by a notification (the weekly summary opens the review). */
    val openTab = MutableStateFlow<String?>(null)

    // The weekly summary

    val weeklySummary: StateFlow<Boolean> = dao.observeSetting(SETTING_WEEKLY).map { it != "0" }.state(true)

    fun setWeeklySummary(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_WEEKLY, if (on) "1" else "0") }
    }

    // The evening check

    /** When the evening check comes, or null while it is off (see EveningCheck). */
    val eveningCheck: StateFlow<LocalTime?> = dao.observeSetting(io.github.randomtools3377.checklist.core.EveningCheck.SETTING)
        .map { io.github.randomtools3377.checklist.core.EveningCheck.decode(it) }.state(null)

    /** Sets when the evening check comes; null turns it off. A time that isn't before the day ends isn't taken. */
    fun setEveningCheck(time: LocalTime?) {
        viewModelScope.launch {
            val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
            if (time != null && !io.github.randomtools3377.checklist.core.EveningCheck.allowed(time, dayEnd)) return@launch
            repository.setSetting(io.github.randomtools3377.checklist.core.EveningCheck.SETTING, io.github.randomtools3377.checklist.core.EveningCheck.encode(time))
        }
    }

    // Best streaks

    /**
     * The best run of each streak ever, for the review's stats: from every day there is, read
     * off the same day records as the current streaks on Today. Only worked out while the review
     * is looked at.
     */
    val bestStreaks: StateFlow<Streaks?> = everyDay { records, _, streak, _ ->
        io.github.randomtools3377.checklist.core.BestStreaks.of(records, streak)
    }.state(null)

    /**
     * The days that were comebacks (see Comebacks), for the count in the review's stats: back on
     * the day's target right after a day that fell short, from the day tracking began. Only
     * worked out while the review is looked at.
     */
    val comebacks: StateFlow<List<LocalDate>> = everyDay { records, day, streak, since ->
        io.github.randomtools3377.checklist.core.Comebacks.days(records, day, streak, since)
    }.state(emptyList())

    /** Something worked out from every day's record there is, up to today, with the main streak's target and the day tracking began. */
    private fun <T> everyDay(work: (List<io.github.randomtools3377.checklist.core.DayRecord>, LocalDate, StreakSettings, LocalDate?) -> T): Flow<T> =
        today.flatMapLatest { day ->
            combine(
                goalRows,
                dao.observeHabitTicksSince(Long.MIN_VALUE),
                dao.observeLapsesSince(Long.MIN_VALUE),
                settings.map { it.streak }.distinctUntilChanged(),
                dao.observeSetting(io.github.randomtools3377.checklist.core.Lapses.TRACKING_SINCE),
            ) { goals, tickRows, lapseRows, streak, since ->
                withContext(Dispatchers.Default) {
                    val ticks = tickRows.map { it.toCore() }
                    val lapses = lapseRows.map { it.toCore() }
                    val from = listOfNotNull(ticks.minOfOrNull { it.periodStart }, lapses.minOfOrNull { it.periodStart }, day).min()
                    val records = StreakCounter.records(from, day, goals.map { it.toCore() }, ticks, lapses)
                    work(records, day, streak, io.github.randomtools3377.checklist.core.Comebacks.since(since))
                }
            }
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
        /** The folder was there, but today's automatic backup couldn't be saved; it tries again. */
        val saveFailed: Boolean = false,
    )

    private val folderGone = MutableStateFlow(false)
    private val autoBackupFailed = MutableStateFlow(false)
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
                BackupState(
                    last, phone.backupFolderName(), phone.backupFolder() != null, folderGone.value, phone.backupReminderOn(), due,
                    saveFailed = autoBackupFailed.value,
                )
            }
            backupState.value = state
        }
    }

    private fun autoBackup() {
        viewModelScope.launch {
            val result = runCatching {
                phone.autoBackupIfDue(Days.dayOf(nowLocal(), settings.value.dayEndHour)) { backupText() }
            }.getOrDefault(io.github.randomtools3377.checklist.data.AutoBackupResult.NotDue)
            when (result) {
                io.github.randomtools3377.checklist.data.AutoBackupResult.FolderGone -> {
                    folderGone.value = true
                    autoBackupFailed.value = false
                }
                io.github.randomtools3377.checklist.data.AutoBackupResult.Failed -> autoBackupFailed.value = true
                io.github.randomtools3377.checklist.data.AutoBackupResult.Saved -> {
                    folderGone.value = false
                    autoBackupFailed.value = false
                }
                io.github.randomtools3377.checklist.data.AutoBackupResult.NotDue -> {}
            }
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
            autoBackupFailed.value = false
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

    /**
     * The misses inside the gap waiting for its check-in that still stand, oldest first. The
     * check-in lists them, each with its own "I forgot to tick it"; one said to be a forgotten
     * tick drops out, and when none is left the gap goes away.
     */
    val gapMisses: StateFlow<List<Lapse>> = dao.observeUnansweredGaps().flatMapLatest { rows ->
        val gap = rows.firstOrNull() ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList())
        dao.observeLapsesSince(gap.fromDay).map { lapses ->
            lapses.map { it.toCore() }.filter { it.gapId == gap.id && it.kind == io.github.randomtools3377.checklist.core.LapseKind.MISS && !it.forgotten }
        }
    }.state(emptyList())

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
            // Removed goals too, so their points keep their category; they aren't counted as due.
            combine(goalRows, dao.observeRemovedGoals(), hidingPrivate) { active, removed, hide -> (active + removed) to hide },
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

    /**
     * Moods from the start of the stretch before the one being reviewed, however far back it is:
     * the review's mood part compares the two.
     */
    val reviewMoods: StateFlow<List<Mood>> = combine(reviewPeriod, reviewDay, today) { period, day, todayDay ->
        Review.range(Review.range(day ?: todayDay, period).first.minusDays(1), period).first
    }.distinctUntilChanged().flatMapLatest { from ->
        dao.observeMoodsSince(from.toEpochDay()).map { rows -> rows.map { it.toCore() } }
    }.state(emptyList())

    /** Focus time in the stretch being reviewed: all of it and each goal's. Private goals are left out while locked. */
    val reviewFocus: StateFlow<FocusTotals?> = combine(reviewPeriod, reviewDay, today) { period, day, todayDay ->
        Review.range(day ?: todayDay, period)
    }.distinctUntilChanged().flatMapLatest { (from, to) ->
        combine(
            dao.observeFocusSessions(from.toEpochDay(), to.toEpochDay()),
            goalRows,
            dao.observeRemovedGoals(),
            hidingPrivate,
        ) { rows, active, removed, hide ->
            FocusTime.totals(rows.map { it.toCore() }, (active + removed).map { it.toCore() }, from, to, hidePrivate = hide)
        }
    }.state(null)

    /**
     * Opens [day] in the review's Day view, from a tap on the month calendar or a day chart. A day
     * still to come isn't opened.
     */
    fun reviewOpenDay(day: LocalDate) {
        val today = Days.dayOf(nowLocal(), settings.value.dayEndHour)
        if (day > today) return
        reviewDay.value = if (day == today) null else day
        reviewPeriod.value = Period.DAILY
    }

    /** Day, Week, Month or Year, keeping the date being looked at. */
    fun reviewShow(period: Period) {
        reviewPeriod.value = period
    }

    /**
     * What was finished in the stretch being reviewed: projects achieved, to-dos done, a missed
     * project deadline, and what goals with an amount came to. Private goals are left out while
     * they are locked, as in the rest of the review.
     */
    val reviewDone: StateFlow<StretchDone?> = combine(reviewPeriod, reviewDay, today) { period, day, todayDay ->
        Review.range(day ?: todayDay, period)
    }.distinctUntilChanged().flatMapLatest { (from, to) ->
        combine(
            combine(goalRows, hidingPrivate) { goals, hide -> goals to hide },
            dao.observeSteps(),
            dao.observeStepTicks(),
            dao.observeHabitTicksSince(Days.earliestOpenDay(from).toEpochDay()),
            dao.observeLapsesSince(from.toEpochDay()),
        ) { (goals, hide), steps, stepTicks, habitTicks, lapseRows ->
            StretchDoneList.of(
                from, to,
                goals = goals.map { it.toCore() },
                steps = steps.map { it.toCore() },
                ticks = (stepTicks + habitTicks).map { it.toCore() },
                lapses = lapseRows.map { it.toCore() },
                hidePrivate = hide,
            )
        }
    }.state(null)

    // A page for each goal

    /**
     * Everything [goalId]'s own page shows, kept up to date; null while it loads or once the goal
     * is gone. Its reasons, notes and privacy are the screen's to hide (see GoalPageScreen).
     */
    fun goalPage(goalId: Long): Flow<GoalPageView?> = today.flatMapLatest { day ->
        combine(
            combine(goalRows, dao.observeRemovedGoals()) { shown, removed -> (shown + removed).firstOrNull { it.id == goalId }?.toCore() },
            dao.observeSteps(),
            combine(dao.observeHabitTicksSince(Long.MIN_VALUE), dao.observeStepTicks()) { habit, step -> habit + step },
            dao.observeLapsesSince(Long.MIN_VALUE),
            dao.observeEntries(),
        ) { goal, steps, tickRows, lapseRows, entryRows ->
            if (goal == null) return@combine null
            withContext(Dispatchers.Default) {
                GoalPage.of(
                    goal, day,
                    steps = steps.filter { it.goalId == goalId }.map { it.toCore() },
                    ticks = tickRows.filter { it.goalId == goalId }.map { it.toCore() },
                    lapses = lapseRows.filter { it.goalId == goalId }.map { it.toCore() },
                    entries = entryRows.filter { it.goalId == goalId }.map { it.toCore() },
                )
            }
        }
    }

    // The daily mood check-in

    /** Today's mood, if it was given. */
    val todayMood: StateFlow<Mood?> = today.flatMapLatest { day -> dao.observeMood(day.toEpochDay()).map { it?.toCore() } }.state(null)

    /** Whether Today asks how the user feels: on, not answered and not put off today. */
    val askMood: StateFlow<Boolean> = combine(today, todayMood, dao.observeSetting(SETTING_MOOD), dao.observeSetting(SETTING_MOOD_SKIPPED)) { day, mood, on, skipped ->
        Feature.MOOD.isOn(on) && mood == null && skipped != day.toString()
    }.state(false)

    fun setMood(score: Int, note: String? = null) {
        viewModelScope.launch {
            val day = currentDay()
            // With everything already done, this first check-in is the day's only one.
            val allDone = todayState.value?.view?.takeIf { it.day == day }?.let { MoodInsights.allDone(it.dueToday) } == true
            repository.setMood(day, score, note, afterAllDone = allDone)
            // Today's mood reminder isn't needed any more.
            runCatching { phone.rescheduleReminders() }
        }
    }

    /** Whether the check-in was put off today with "Not today", so Today offers "Check in now". */
    val moodPutOff: StateFlow<Boolean> = combine(today, todayMood, dao.observeSetting(SETTING_MOOD), dao.observeSetting(SETTING_MOOD_SKIPPED)) { day, mood, on, skipped ->
        Feature.MOOD.isOn(on) && mood == null && skipped == day.toString()
    }.state(false)

    /** "Check in now": takes "Not today" back, so the check-in is asked again. */
    fun checkInNow() {
        viewModelScope.launch { repository.setSetting(SETTING_MOOD_SKIPPED, "") }
    }

    /** Today's mood asked to open in full, like from the mood reminder. */
    val moodOpen = MutableStateFlow(false)

    /** When the daily mood reminder comes, or null while it is off (see MoodReminder). */
    val moodReminder: StateFlow<LocalTime?> = dao.observeSetting(io.github.randomtools3377.checklist.core.MoodReminder.SETTING)
        .map { io.github.randomtools3377.checklist.core.MoodReminder.decode(it) }.state(null)

    /** Sets when the mood reminder comes; null turns it off. */
    fun setMoodReminder(time: LocalTime?) {
        viewModelScope.launch {
            repository.setSetting(io.github.randomtools3377.checklist.core.MoodReminder.SETTING, io.github.randomtools3377.checklist.core.MoodReminder.encode(time))
            phone.rescheduleReminders()
        }
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
        viewModelScope.launch {
            val result = lapses.skip(goalId, reason, note, reasonText)
            onResult(result)
            // Right after a skip, the Undo bar takes it back, as if it never happened. Over the
            // limit the penalty task fills the screen instead, and offers "Take back the skip" itself.
            if (result is SkipResult.Skipped && result.penaltyId == null) {
                val skipId = result.lapse.id
                noteJustNow(io.github.randomtools3377.checklist.core.JustNow(io.github.randomtools3377.checklist.core.JustNow.Kind.SKIP, repository.goal(goalId)?.let(::sayable)))
                offerUndo("Skipped ${result.lapse.label}", undone = "Skip taken back: ${result.lapse.label}") {
                    noteJustNow(null)
                    lapses.takeBackSkipById(skipId)
                }
            }
        }
    }

    /**
     * "Take back the skip": today's skip of a goal goes, as if it never happened, with the
     * allowance it used, its penalty points and open penalty task over the limit, and what it
     * did to the streaks. For a goal counted over a week, month or year, one skip of its times.
     */
    fun takeBackSkip(goalId: Long) {
        viewModelScope.launch { lapses.takeBackSkip(goalId) }
    }

    /** The skip a penalty task is owed for, while it can still be taken back (it was made today). */
    suspend fun skipBehind(penalty: Penalty): Lapse? = lapses.skipBehind(penalty)

    /** Takes back that very skip, from its penalty task's screen: the task is dropped with it. */
    fun takeBackThisSkip(skip: Lapse) {
        viewModelScope.launch { lapses.takeBackSkipById(skip.id) }
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

    // Misses put right later, from the review and the points history

    /** The goals to quit, removed ones too: a miss made by "I slipped" on one is worded as a slip. */
    val quitGoals: StateFlow<Set<Long>> = combine(goalRows, dao.observeRemovedGoals()) { shown, removed ->
        (shown + removed).filter { it.quit }.map { it.id }.toSet()
    }.state(emptySet())

    /** The "at most" goals, removed ones too: a miss of one is worded as going over its limit. */
    val atMostGoals: StateFlow<Set<Long>> = combine(goalRows, dao.observeRemovedGoals()) { shown, removed ->
        (shown + removed).map { it.toCore() }.filter { it.atMost }.map { it.id }.toSet()
    }.state(emptySet())

    /**
     * The misses that still stand in the stretch being reviewed, oldest first, to open and put
     * right. Those of private goals are left out while they are locked, as in the rest of the review.
     */
    val reviewMisses: StateFlow<List<Lapse>> = combine(reviewPeriod, reviewDay, today) { period, day, todayDay ->
        Review.range(day ?: todayDay, period)
    }.distinctUntilChanged().flatMapLatest { (from, to) ->
        combine(dao.observeLapsesSince(from.toEpochDay()), privateIds, hidingPrivate) { rows, private, hide ->
            rows.map { it.toCore() }.filter {
                it.kind == io.github.randomtools3377.checklist.core.LapseKind.MISS && !it.forgotten && it.day <= to && !(hide && it.goalId in private)
            }
        }
    }.state(emptyList())

    /** The skip or miss opened to be put right, by its id: from the review or a penalty's line in the points history. */
    val openMiss = MutableStateFlow<Long?>(null)

    /** What the box for a past skip or miss shows (see [PastMissDialog]); null when it is gone. */
    suspend fun loadMiss(lapseId: Long): PastMiss? {
        val lapse = dao.lapse(lapseId)?.toCore() ?: return null
        val goal = repository.goal(lapse.goalId)
        return PastMiss(
            lapse = lapse,
            private = goal?.private == true,
            slip = io.github.randomtools3377.checklist.core.Lapses.isSlip(lapse, goal?.quit == true, settings.value.dayEndHour),
            // An "at most" goal that went over its limit wasn't a forgotten tick.
            canForget = io.github.randomtools3377.checklist.core.Lapses.canForgetLater(lapse, currentDay()) && goal?.atMost != true,
            over = goal?.atMost == true && lapse.kind == io.github.randomtools3377.checklist.core.LapseKind.MISS,
        )
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

    /**
     * What the user actually earns a day, from their own history (see [Usual.pointsPerDay]).
     * The shop, the balance check, the reward editor and Today all take the days a price takes
     * from this one figure, through [Usual.days].
     */
    val usualPerDay: StateFlow<Double?> = today.flatMapLatest { day ->
        combine(
            dao.observeEarnedByDay(day.minusDays(Usual.WINDOW_DAYS).toEpochDay()),
            dao.observeFirstEarningDay(),
        ) { earned, first ->
            Usual.pointsPerDay(
                earnedByDay = earned.associate { it.day.toDay() to it.amount },
                firstDay = first?.toDay(),
                today = day,
            )
        }
    }.state(null)

    val shopView: StateFlow<ShopView?> = today.flatMapLatest { day ->
        val pointsPerDay = usualPerDay
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
                perDay = perDay,
            )
        }
    }.state(null)

    /** The id of the reward starred as the one being saved for, or null. */
    val starredReward: StateFlow<Long?> = dao.observeSetting(SavingFor.SETTING).map { SavingFor.starred(it) }.state(null)

    /**
     * The starred reward for Today's balance card: at what it costs now, against the balance and
     * the usual earnings. Null with no star, and while the shop is behind the privacy lock.
     */
    val savingFor: StateFlow<SavingFor?> =
        combine(shopView, dao.observeSetting(SavingFor.SETTING), combine(settings, unlocked) { s, open -> s.locks.shop && !open }) { shop, setting, locked ->
            if (shop == null || locked) null else SavingFor.of(setting, shop.rewards.map { it.reward }, shop.balance, shop.perDay)
        }.state(null)

    /** Stars [rewardId] as the reward being saved for, or takes its star off. */
    fun setSavingFor(rewardId: Long, on: Boolean) {
        viewModelScope.launch { repository.setSavingFor(rewardId, on) }
    }

    /** Whether [rewardId] is the starred reward, for its editor. */
    suspend fun isSavingFor(rewardId: Long): Boolean = rewardId != 0L && SavingFor.starred(dao.setting(SavingFor.SETTING)) == rewardId

    /** Every change to the balance, newest first. Private goals' entries aren't named while they are locked. */
    val history: StateFlow<List<Entry>?> = combine(dao.observeEntries(), privateIds, hidingPrivate) { rows, ids, hide ->
        rows.map { it.toCore() }.let { entries -> if (hide) io.github.randomtools3377.checklist.core.PrivateGoals.unnamed(entries, ids) else entries }
    }.state(null)

    // Ticking and the shop. Results come back on the main thread.

    /** What the Undo bar offers to take back: the last tick, skip, removed goal or purchase. */
    val undoBar = kotlinx.coroutines.flow.MutableSharedFlow<Undoable>(extraBufferCapacity = 4)

    private fun offerUndo(message: String, undone: String? = null, undo: suspend () -> Unit) {
        undoBar.tryEmit(Undoable(message, undone) { viewModelScope.launch { undo() } })
    }

    /** How things stand now, taken just before a tick (see [offerTickUndo]). */
    private fun beforeTick() = BeforeTick(todayState.value, quests.value, weekProgress.value, race.value)

    /**
     * What a tick of [goal] is changing, from how things stood [before] it (see TickNews). The
     * goal's own run and times done are looked up only when the tick finished it.
     */
    private suspend fun tickMoment(goal: Goal, result: TickResult.Ticked, before: BeforeTick?): io.github.randomtools3377.checklist.core.TickMoment {
        val state = before?.state
        val day = state?.view?.day ?: currentDay()
        val view = state?.view
        val row = view?.let { v -> (v.dueToday + v.limits + v.thisWeek + v.thisMonth + v.thisYear).firstOrNull { it.goal.id == goal.id } }
        val onList = state?.plan?.any { it.row.goal.id == goal.id } == true
        // The goal's own history: how many times in a row, and in all.
        val runs = if (result.finished && goal.isHabitLike && goal.repeat != null && !goal.atMost) {
            withContext(Dispatchers.Default) {
                val ticks = dao.habitTicksSince(Long.MIN_VALUE).filter { it.goalId == goal.id }.map { it.toCore() }
                val lapses = dao.lapses(goal.id, Long.MIN_VALUE, Long.MAX_VALUE).map { it.toCore() }
                val times = GoalPage.times(goal, day, ticks, lapses)
                val last = times.lastOrNull()?.takeIf { it.state == io.github.randomtools3377.checklist.core.TimeState.DONE }
                if (last == null) null else Triple(GoalPage.runs(times, day).first, GoalPage.runs(times.dropLast(1), day).second, times.count { it.state == io.github.randomtools3377.checklist.core.TimeState.DONE })
            }
        } else {
            null
        }
        val now = nowLocal()
        return io.github.randomtools3377.checklist.core.TickMoment(
            finished = result.finished,
            paid = result.points + result.bonus,
            onTodaysList = onList,
            leftBefore = state?.plan?.count { !it.row.settled } ?: 0,
            countsToday = row?.window?.end == day,
            record = state?.todayRecord,
            streak = state?.streakSettings ?: settings.value.streak,
            streakBefore = state?.streaks?.done ?: 0,
            quests = before?.quests?.takeIf { it.day == day }?.list.orEmpty(),
            area = io.github.randomtools3377.checklist.core.SkillArea.of(goal),
            goalId = goal.id,
            skippedBefore = row?.skipped == true,
            firstTickToday = row?.count == 0,
            time = LocalTime(now.hour, now.minute),
            dayEndHour = settings.value.dayEndHour,
            week = before?.week,
            race = before?.race,
            run = runs?.first,
            bestRunBefore = runs?.second,
            timesDone = runs?.third,
        )
    }

    /** For the character: what was ticked or skipped a moment ago, gone after a few seconds or on Undo. */
    private val justNow = MutableStateFlow<io.github.randomtools3377.checklist.core.JustNow?>(null)

    /** For the character: something in the game that happened a moment ago (a lucky tick, a quest, a hit on the boss). */
    private val gameEvent = MutableStateFlow<io.github.randomtools3377.checklist.core.CompanionEvent?>(null)
    private var justNowJob: kotlinx.coroutines.Job? = null
    private var gameEventJob: kotlinx.coroutines.Job? = null

    private fun noteJustNow(just: io.github.randomtools3377.checklist.core.JustNow?) {
        justNowJob?.cancel()
        justNow.value = just
        if (just != null) {
            justNowJob = viewModelScope.launch {
                delay(io.github.randomtools3377.checklist.core.Checkins.JUST_NOW_MS)
                justNow.value = null
            }
        }
    }

    private fun noteGameEvent(event: io.github.randomtools3377.checklist.core.CompanionEvent?) {
        gameEventJob?.cancel()
        gameEvent.value = event
        if (event != null) {
            gameEventJob = viewModelScope.launch {
                delay(io.github.randomtools3377.checklist.core.Checkins.JUST_NOW_MS)
                gameEvent.value = null
            }
        }
    }

    /** A goal's name for the character to say: never a private goal's while they are locked. */
    private fun sayable(goal: Goal): String? = goal.name.takeIf { !(goal.private && settings.value.locks.privateGoals && !unlocked.value) }

    /** Whether [goalId] is running late on Today right now: asked before it is ticked. */
    private fun runningLate(goalId: Long): Boolean = todayState.value?.plan?.any { it.row.goal.id == goalId && it.late } == true

    private suspend fun offerTickUndo(
        goalId: Long,
        result: TickResult,
        luck: io.github.randomtools3377.checklist.data.Repository.LuckPaid? = null,
        late: Boolean = false,
        before: BeforeTick? = null,
        undo: suspend () -> Unit,
    ) {
        if (result !is TickResult.Ticked) return
        // A goal done: the next in its habit chain is reminded.
        if (result.finished) phone.afterDone(goalId)
        val goal = repository.goal(goalId) ?: return
        val name = goal.name
        // The character says a line about it for a few seconds; Undo takes the line back too.
        noteJustNow(io.github.randomtools3377.checklist.core.JustNow(io.github.randomtools3377.checklist.core.JustNow.Kind.TICK, sayable(goal), late = late && result.finished, finished = result.finished))
        // Only a lucky tick is noted here: a plain tick must not wipe a quest it has just finished, which is noted on its own.
        luck?.let { noteGameEvent(io.github.randomtools3377.checklist.core.CompanionEvent.Lucky(it.points, it.double)) }
        val paid = result.points + result.bonus
        val lucky = luck?.let { if (it.double) "🍀 lucky, twice the points: +${it.points}" else "🍀 lucky chest: +${it.points}" }
        // The one thing the tick changed, in words (see TickNews). A private goal is never named
        // by it while private goals are locked.
        val moment = runCatching { tickMoment(goal, result, before) }.getOrNull()
        val named = !(goal.private && settings.value.locks.privateGoals && !unlocked.value)
        val news = io.github.randomtools3377.checklist.core.TickNews
        val phrase = moment?.let { news.phrase(it, name.takeIf { named }) }
        offerUndo(news.line(name, paid, lucky, phrase), undone = news.undone(name, paid + (luck?.points ?: 0), moment)) {
            noteJustNow(null)
            noteGameEvent(null)
            undo()
        }
    }

    fun tickHabit(goalId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val late = runningLate(goalId)
            val before = beforeTick()
            val result = book.tickHabit(goalId)
            celebrating(goalId)
            onResult(result)
            // Lucky ticks: now and then a tick pays extra. Undoing the tick takes the extra back.
            val luck = if (result is TickResult.Ticked && Feature.LUCKY in features.value) {
                repository.luckyTick(goalId, currentDay(), result.points)
            } else {
                null
            }
            offerTickUndo(goalId, result, luck, late, before) {
                book.undoHabitTick(goalId)
                luck?.let { repository.removeEntry(it.entryId) }
            }
        }
    }

    /**
     * Adds to an "at most" goal as it happens: one, or [amount] of its unit. Going over its
     * limit makes today a miss now, and the misses screen asks why. Undo takes it back, and the
     * miss with it once the day is back at or under the limit.
     */
    fun addUse(goalId: Long, amount: Double = 1.0) {
        viewModelScope.launch {
            val result = lapses.use(goalId, amount) as? io.github.randomtools3377.checklist.core.UseResult.Added ?: return@launch
            val goal = repository.goal(goalId) ?: return@launch
            offerUndo("${goal.name} · ${io.github.randomtools3377.checklist.core.AtMost.text(result.used, goal)}", undone = "${goal.name}: the last one taken back") { lapses.undoUse(goalId) }
        }
    }

    /** Takes back the latest thing added to an "at most" goal today. */
    fun undoUse(goalId: Long) {
        viewModelScope.launch { lapses.undoUse(goalId) }
    }

    /**
     * "That's all for today" on an "at most" goal still within its limit: the day is done now,
     * and pays its points. It can be taken back, and going over afterwards takes it back too.
     */
    fun confirmAtMost(goalId: Long) {
        viewModelScope.launch {
            val before = beforeTick()
            val result = book.tickHabit(goalId)
            celebrating(goalId)
            offerTickUndo(goalId, result, before = before) { book.undoHabitTick(goalId) }
        }
    }

    /** "I slipped" on a goal to quit: today is a miss now, and the misses screen asks why. */
    fun slip(goalId: Long) {
        viewModelScope.launch {
            lapses.slip(goalId)
        }
    }

    /**
     * Takes back the goal's latest tick, with what it paid: a lucky extra goes with its tick.
     */
    fun undoHabitTick(goalId: Long) {
        viewModelScope.launch {
            book.undoHabitTick(goalId)
        }
    }

    /** Enters how much of a goal's amount was done; a timing from Start and Stop ends with it. */
    fun logAmount(goalId: Long, amount: Double, seconds: Int?, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val late = runningLate(goalId)
            val before = beforeTick()
            val result = book.logAmount(goalId, amount, seconds)
            if (seconds != null) repository.setSetting(SETTING_STOPWATCH, "")
            celebrating(goalId)
            onResult(result)
            offerTickUndo(goalId, result, late = late, before = before) {
                book.undoHabitTick(goalId)
            }
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
            goalRows,
            dao.observeSteps(),
            // Step ticks too, so projects and to-dos show the steps already done, and a finished
            // project isn't listed as if nothing was done.
            combine(dao.observeHabitTicksSince(since.toEpochDay()), dao.observeStepTicks()) { habitTicks, stepTicks -> habitTicks + stepTicks },
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
            val step = dao.step(stepId)
            celebrating(step?.goalId)
            onResult(result)
            if (step != null && result is TickResult.Ticked) {
                offerUndo("${step.name} ticked", undone = "${step.name} taken back") {
                    book.uncheckItem(stepId)
                }
            }
        }
    }

    fun uncheckItem(stepId: Long) {
        viewModelScope.launch {
            book.uncheckItem(stepId)
        }
    }

    /** "Done" on a routine: every item still open, and so the routine. */
    fun finishRoutine(goalId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val late = runningLate(goalId)
            val before = beforeTick()
            val result = book.finishRoutine(goalId)
            celebrating(goalId)
            onResult(result)
            offerTickUndo(goalId, result, late = late, before = before) {
                book.undoRoutine(goalId)
            }
        }
    }

    fun undoRoutine(goalId: Long) {
        viewModelScope.launch {
            book.undoRoutine(goalId)
        }
    }

    fun tickStep(stepId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.tickStep(stepId)
            val step = dao.step(stepId)
            celebrating(step?.goalId)
            onResult(result)
            if (step != null && result is TickResult.Ticked) {
                val paid = result.points + result.bonus
                offerUndo(
                    if (paid > 0) "${step.name} · +$paid" else "${step.name} ticked",
                    undone = io.github.randomtools3377.checklist.core.TickNews.undone(step.name, paid),
                ) { book.undoStep(stepId) }
            }
        }
    }

    fun undoStep(stepId: Long) {
        viewModelScope.launch { book.undoStep(stepId) }
    }

    fun buy(rewardId: Long, fromSaving: Long? = null, onResult: (BuyResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = book.buy(rewardId)
            onResult(result)
            if (result is BuyResult.Bought) {
                val name = result.prize ?: repository.reward(rewardId)?.name ?: return@launch
                countRewardSpending(result.purchaseId, rewardId, name, fromSaving)
                offerUndo("Bought $name · −${result.price}", undone = "$name returned · +${result.price}") { returnAndUncount(result.purchaseId) }
            }
        }
    }

    fun use(purchaseId: Long) {
        viewModelScope.launch {
            if (!book.use(purchaseId)) return@launch
            val name = dao.purchase(purchaseId)?.name ?: return@launch
            offerUndo("Used $name", undone = "$name is back with your rewards") { dao.unmarkUsed(purchaseId) }
        }
    }

    /** Rewards already used, latest first, for the list under Bought. */
    val usedRewards: StateFlow<List<io.github.randomtools3377.checklist.core.Purchase>> =
        dao.observeUsedPurchases().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /**
     * What is left this month of the Rewards category's limit, in cents (below zero once over);
     * null without the Budget, or while that category has no limit.
     */
    val rewardsLimitLeft: StateFlow<Long?> = today.flatMapLatest { day ->
        val first = io.github.randomtools3377.checklist.core.Budget.monthOf(day)
        val last = io.github.randomtools3377.checklist.core.Pay.monthDays(day).last()
        combine(dao.observeMoneyCategories(), dao.observeMoney(first.toEpochDay(), last.toEpochDay()), dao.observeSetting(Feature.BUDGET.key)) { categories, money, on ->
            val rewards = categories.firstOrNull { it.role == io.github.randomtools3377.checklist.core.CategoryRole.REWARDS.name && !it.archived }
            val limit = rewards?.limitCents
            if (!Feature.BUDGET.isOn(on) || rewards == null || limit == null) {
                null
            } else {
                limit - money.filter { it.kind == io.github.randomtools3377.checklist.core.MoneyKind.SPENT.name && it.categoryId == rewards.id }.sumOf { it.cents }
            }
        }
    }.state(null)

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
            repository.saveHabit(draft, currentDay())
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
            goalRows,
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
        dao.observeSetting(MoneyPrices.RATE), goalRows, treatBudget, today,
    ) { stored, goals, budget, day ->
        MoneyPrices.next(MoneyPrices.decode(stored), MoneyPrices.fresh(goals.map { it.toCore() }, day, budget))
    }.state(null)

    init {
        // Prices that follow money keep in step with what the goals pay, and with the budget.
        viewModelScope.launch {
            combine(goalRows, treatBudget, dao.observeRewards(), today) { _, _, _, day -> day }
                .collectLatest { day -> repository.keepMoneyPricesInStep(day) }
        }
    }

    /** First-time tips: off unless turned on in Help, and the guides already shown. Null until read. */
    val tips: StateFlow<TipsState?> = combine(dao.observeSetting(SETTING_TIPS_ON), dao.observeSetting(SETTING_TIPS_SEEN)) { on, seen ->
        TipsState(on = on == "1", seen = seen.orEmpty().split(",").filter { it.isNotBlank() }.toSet())
    }.state(null)

    fun tipSeen(topic: HelpTopic) {
        val seen = (tips.value?.seen.orEmpty() + topic.name).sorted().joinToString(",")
        viewModelScope.launch { repository.setSetting(SETTING_TIPS_SEEN, seen) }
    }

    fun setTipsOn(on: Boolean) {
        viewModelScope.launch { repository.setSetting(SETTING_TIPS_ON, if (on) "1" else "0") }
    }

    fun showTipsAgain() {
        viewModelScope.launch {
            repository.setSetting(SETTING_TIPS_SEEN, "")
            repository.setSetting(SETTING_TIPS_ON, "1")
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
        val perDay = usualPerDay
        val shown = combine(goalRows, settings, unlocked) { goals, s, isUnlocked ->
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
        goalRows, dao.observeSteps(), dao.observeStepTicks(), dao.observeTodoTicks(), combine(settings, unlocked) { s, u -> s to u },
    ) { goals, steps, stepTicks, todoTicks, (s, isUnlocked) ->
        val all = goals.map { it.toCore() }
        val shown = if (s.locks.privateGoals && !isUnlocked) all.filter { !it.private } else all
        DoneList.of(shown, steps.map { it.toCore() }, (stepTicks + todoTicks).map { it.toCore() })
    }.state(emptyList())

    /**
     * The optional features turned on (see Feature). Off until the user turns them on. The AI
     * helper counts only on a phone that has it, so its cards stay away elsewhere.
     */
    val features: StateFlow<Set<Feature>> = combine(Feature.entries.map { dao.observeSetting(it.key) }) { values ->
        Feature.entries.filterIndexed { i, feature -> feature.isOn(values[i]) && (feature != Feature.AI || phone.hasAi) }.toSet()
    }.state(emptySet())

    /** Whether [feature] is on, read now, for before [features] has been loaded. */
    suspend fun featureOn(feature: Feature): Boolean = feature.isOn(dao.setting(feature.key))

    /** The character's level and skills, while the character is on; null when it is off. */
    val character: StateFlow<io.github.randomtools3377.checklist.core.CharacterSheet?> =
        combine(features, dao.observeBalance(), goalRows, dao.observeSetting(io.github.randomtools3377.checklist.core.Paths.SETTING)) { on, _, _, path ->
            if (Feature.CHARACTER in on) io.github.randomtools3377.checklist.core.Paths.decode(path) to true else null to false
        }.mapLatest { (path, on) ->
            if (!on) null else withContext(Dispatchers.Default) {
                io.github.randomtools3377.checklist.core.Experience.of(dao.allEntries().map { it.toCore() }, dao.allGoals().map { it.toCore() }, path)
            }
        }.state(null)

    /**
     * The character's path, its titles earned, and the title shown, while the character is on.
     * A title once earned is kept, like a badge, even when the history it came from has gone.
     */
    val characterTitles: StateFlow<CharacterTitles?> =
        combine(character, dao.observeSetting(io.github.randomtools3377.checklist.core.Paths.SETTING), dao.observeSetting(io.github.randomtools3377.checklist.core.Titles.SETTING), settings) { sheet, path, title, s ->
            sheet?.let { Triple(it, io.github.randomtools3377.checklist.core.Paths.decode(path), title) to s }
        }.mapLatest { found ->
            found?.let { (picked, s) ->
                val (sheet, path, title) = picked
                withContext(Dispatchers.Default) {
                    val T = io.github.randomtools3377.checklist.core.Titles
                    val day = currentDay()
                    val from = day.minusDays(STREAK_DAYS)
                    val goals = dao.allGoals().map { it.toCore() }
                    val ticks = dao.allTicks().map { it.toCore() }
                    val records = StreakCounter.records(from, day, goals.filter { !it.archived }, ticks.filter { it.stepId == null }, dao.allLapses().map { it.toCore() })
                    // An "at most" goal isn't ticked at a time of day: its day is done when it ends.
                    val limited = goals.filter { it.atMost }.map { it.id }.toSet()
                    val now = T.earned(
                        ticks.filter { it.goalId !in limited }, dao.allEntries().map { it.toCore() }, records, sheet, s.streak, { at -> at.toLocalDateTime().time }, s.dayEndHour,
                    )
                    val kept = T.decodeKept(dao.setting(T.EARNED))
                    val all = T.keep(kept, now, day)
                    if (all != kept) repository.setSetting(T.EARNED, T.encodeKept(all))
                    val earned = all.keys
                    CharacterTitles(path, earned, title, T.shown(title, path, earned))
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
                        // An "at most" goal isn't one of the day's goals to tick.
                        val limited = state.view.limits.map { it.goal.id }.toSet()
                        val todays = ticks.map { it.toCore() }.filter { it.day == day && it.goalId !in limited }
                        val earned = entries.map { it.toCore() }
                            .filter { it.kind in io.github.randomtools3377.checklist.core.Experience.KINDS && it.goalId != null }
                            .sumOf { it.amount }
                        // The day's quests are made once and kept all day, so changing the goals doesn't reshuffle them.
                        val made = repository.questsOf(day, rows.map { it.goal })
                        val dayEndHour = settings.value.dayEndHour
                        val list = io.github.randomtools3377.checklist.core.Quests.shown(made, rows).map { quest ->
                            io.github.randomtools3377.checklist.core.Quests.progress(quest, rows, todays, { at -> at.toLocalDateTime().time }, earned, dayEndHour)
                        }
                        io.github.randomtools3377.checklist.core.DayQuests(day, list)
                    }
                }
            }.state(null)

    init {
        // A finished quest pays its bonus once.
        viewModelScope.launch {
            // The character notices a quest finished while the app is open.
            var seen: Pair<LocalDate, Int>? = null
            quests.collect { day ->
                day?.list?.filter { it.complete }?.forEach { repository.payQuest(day.day, it.quest) }
                if (day != null) {
                    val complete = day.list.count { it.complete }
                    seen?.let { (on, before) ->
                        if (on == day.day && complete > before) noteGameEvent(io.github.randomtools3377.checklist.core.CompanionEvent.QuestDone(day.list.size - complete))
                    }
                    seen = day.day to complete
                }
            }
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

    /**
     * While behind in the race: the fewest of today's goals that would take the lead. Only goals
     * on Today's list are picked, so a private goal is never named while it is locked.
     */
    val raceCatchUp: StateFlow<io.github.randomtools3377.checklist.core.CatchUp?> =
        combine(race, todayState, currentDifficulty) { standing, state, rate ->
            if (standing == null || state == null) null else io.github.randomtools3377.checklist.core.Race.catchUp(standing, state.plan.map { it.row }) { row ->
                val each = io.github.randomtools3377.checklist.core.payFor(row.goal.points, rate, state.boostedGoalId == row.goal.id, state.streakPercent)
                // A routine or a goal with an amount pays once it is done; a counted one for each tick left.
                each * (if (row.isRoutine || row.goal.hasAmount) 1 else row.left.coerceAtLeast(1))
            }
        }.state(null)

    /** The run of perfect days through yesterday, while combos are on; null when off. */
    val comboCount: StateFlow<Int?> = combine(features, dao.observeSetting(io.github.randomtools3377.checklist.core.Combos.COUNT)) { on, count ->
        if (Feature.COMBOS in on) count?.toIntOrNull() ?: 0 else null
    }.state(null)

    /** Today's line about the combo (see Combos.line), in the numbers set in "Points and rules". */
    val comboLine: StateFlow<String?> = combine(comboCount, todayState, rules) { combo, state, _ ->
        combo?.let { io.github.randomtools3377.checklist.core.Combos.line(it, state?.todayRecord) }
    }.state(null)

    /** The closest win on Today, as one short line (see NearWins); null when nothing is near. */
    val nearWin: StateFlow<String?> = combine(
        todayState, boosts, comboCount, combine(dao.observeGoalTickCount(), badges) { ticks, earned -> ticks to earned.keys }, combine(weekProgress, rules) { week, _ -> week },
    ) { state, boost, combo, (ticks, earned), week ->
        state?.let {
            io.github.randomtools3377.checklist.core.NearWins.line(
                it.todayRecord, it.streakSettings, it.streaks.done, perfectBonus = boost.perfectDayBonus, combo = combo, ticks = ticks, earnedBadges = earned, week = week,
            )
        }
    }.state(null)

    /**
     * The morning card: what yesterday came to and what was paid overnight, until it is closed.
     * Hidden while the points history is behind the lock.
     */
    val overnight: StateFlow<String?> = combine(
        todayState, dao.observeSetting(io.github.randomtools3377.checklist.core.Overnight.SETTING), dao.observeSetting(io.github.randomtools3377.checklist.core.Overnight.SEEN), settings, unlocked,
    ) { state, paid, seen, s, isUnlocked ->
        val O = io.github.randomtools3377.checklist.core.Overnight
        when {
            state == null || (s.locks.history && !isUnlocked) || seen == state.view.day.toString() -> null
            else -> O.line(state.view.day, state.yesterdayRecord, O.decode(paid), state.streakSettings, state.streaks.done)
        }
    }.state(null)

    fun overnightSeen() {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Overnight.SEEN, currentDay().toString()) }
    }

    /**
     * The recap once everything due today is done; null while something is left. What stands out
     * against other days is left out while the points history or the review is locked.
     */
    val dayRecap: StateFlow<io.github.randomtools3377.checklist.core.DayRecap?> = combine(
        todayState,
        today.flatMapLatest { day -> dao.observeEntries(LocalDate(day.year, day.monthNumber, 1).toEpochDay(), day.toEpochDay()) },
        boosts, combine(comboCount, rules) { combo, _ -> combo }, combine(settings, unlocked) { s, isUnlocked -> (s.locks.history || s.locks.review) && !isUnlocked },
    ) { state, entries, boost, combo, locked ->
        state?.let {
            io.github.randomtools3377.checklist.core.DayRecaps.of(
                it.view.day, it.plan.map { planned -> planned.row }, it.todayRecord, entries.map { e -> e.toCore() }, it.streakSettings, it.streaks,
                perfectBonus = boost.perfectDayBonus, combo = combo, highlights = !locked,
            )
        }
    }.state(null)

    /** Tomorrow's first few goals, for the recap and a day off; private ones are left out while locked. */
    val comingUp: StateFlow<io.github.randomtools3377.checklist.core.ComingUp?> =
        planDays.map { days -> io.github.randomtools3377.checklist.core.Upcoming.of(days.getOrNull(1)) }.state(null)

    /** This week's boss and the items in hand, while items and bosses are on; null when off. */
    val boss: StateFlow<io.github.randomtools3377.checklist.core.BossView?> =
        combine(features, today, dao.observeBalance(), dao.observeSetting(io.github.randomtools3377.checklist.core.Bosses.FIGHT), goalRows) { on, day, _, _, _ ->
            if (Feature.BOSSES in on) day else null
        }.mapLatest { day -> day?.let { repository.bossView(it) } }.state(null)

    /** Uses every item in hand on this week's boss; [onDone] says what it did. */
    fun attackBoss(onDone: (io.github.randomtools3377.checklist.core.Attack?) -> Unit) {
        viewModelScope.launch {
            val attack = repository.attackBoss(currentDay())
            attack?.let { noteGameEvent(io.github.randomtools3377.checklist.core.CompanionEvent.BossHit(it.dealt, it.beaten)) }
            onDone(attack)
        }
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
            combine(todayMood, askMood) { mood, ask -> mood to ask },
            combine(
                dao.observeSetting(io.github.randomtools3377.checklist.core.Checkins.LEVEL_SEEN),
                dao.observeSetting(io.github.randomtools3377.checklist.core.Checkins.SKILLS_SEEN),
                dao.observeSetting(io.github.randomtools3377.checklist.core.Checkins.WAVED),
                justNow,
                gameEvent,
            ) { level, skills, waved, just, event -> CompanionSeen(level, skills, waved, just, event) },
            combine(settings, rules, weekProgress, race, savingFor) { s, rules, week, race, saving -> CompanionKnows(s, rules.companionLateHours, week, race, saving) },
        ) { (sheet, talks), state, (mood, ask), seen, knows ->
            if (sheet == null || state == null) return@combine null
            val checkins = io.github.randomtools3377.checklist.core.Checkins
            val level = sheet.level.level
            val seenLevel = seen.level?.toIntOrNull()
            if (seenLevel == null) {
                // The first look: start from here, no cheer.
                repository.setSetting(checkins.LEVEL_SEEN, level.toString())
            }
            // Each skill's level is cheered once too, with the gear it brought.
            val seenSkills = checkins.decodeSkills(seen.skills)
            if (seenSkills == null) {
                repository.setSetting(checkins.SKILLS_SEEN, checkins.encodeSkills(io.github.randomtools3377.checklist.core.SkillArea.entries.associateWith { sheet.skillLevel(it).level }))
            }
            // A goal skipped today counts neither as done nor as left: it isn't done, and nothing is left of it.
            val rows = state.view.dueToday.filter { !it.skipped }
            val left = rows.filter { !it.done && !it.goal.quit && !it.goal.hasAmount }
            val day = state.view.day
            val s = knows.settings
            val say = checkins.say(
                io.github.randomtools3377.checklist.core.CompanionFacts(
                    day = day,
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
                    skippedToday = state.view.dueToday.count { it.skipped },
                    dayStart = s.dayStart,
                    dayEndHour = s.dayEndHour,
                    lateHours = knows.lateHours,
                    justNow = seen.justNow,
                    event = seen.event,
                    skillUp = checkins.skillUp(sheet, seenSkills),
                    shortBy = state.pace?.shortBy,
                    streakRule = state.streakSettings,
                    shieldSaved = state.shields?.saved?.contains(day.minusDays(1)) == true,
                    hints = state.plan.filter { !it.row.settled }.associate { planned ->
                        planned.row.goal.id to io.github.randomtools3377.checklist.core.SuggestHint(state.usualMinute[planned.row.goal.id], planned.late)
                    },
                    level = level,
                    toNextLevel = sheet.level.toNext,
                    gearNear = checkins.gearNear(sheet),
                    weekEarned = knows.week?.earned,
                    weekTarget = knows.week?.target,
                    raceAhead = knows.race?.ahead,
                    // The starred reward is only named while the shop isn't locked (savingFor is null then).
                    savingName = knows.saving?.reward?.name,
                    savingLeft = knows.saving?.left,
                    bestDayThisWeek = state.bestDayThisWeek,
                ),
            )
            // With everything done, or nothing due, it offers ideas for the area furthest behind.
            val restful = !say.levelUp && !say.askMood && (rows.isEmpty() || rows.all { it.done })
            CompanionView(
                say, talks, say.suggestion?.let { goal -> left.firstOrNull { it.goal.id == goal.id } },
                ideaArea = if (restful) io.github.randomtools3377.checklist.core.Ideas.weakest(sheet) else null,
                greet = seen.waved != day.toString(),
            )
        }.state(null)

    /** The character has waved hello today. */
    fun greeted() {
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.core.Checkins.WAVED, currentDay().toString()) }
    }

    // Mood and your goals

    /**
     * Which habits, and which things noted at the check-ins, go with a better mood and which with
     * a lower one, over the last 90 days (see MoodInsights). Private goals are left out while
     * they are locked.
     */
    val moodFindings: StateFlow<MoodFindings> = today.flatMapLatest { day ->
        val from = day.minusDays(MoodInsights.DAYS)
        combine(
            goalRows,
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
        combine(
            today, todayState, todayMood, features,
            combine(dao.observeSetting(SETTING_MOOD_AFTER_CLOSED), dao.observeSetting(SETTING_MOOD_ONLY)) { closed, only -> closed to only },
        ) { day, state, mood, on, (closed, only) ->
            if (Feature.MOOD !in on || mood == null) return@combine null
            val allDone = state != null && state.view.day == day && MoodInsights.allDone(state.view.dueToday)
            // A first check-in given after everything was done is the day's only one: not asked again.
            if (!MoodInsights.asksLater(mood, allDone, onlyOne = only == day.toString(), closed = closed == day.toString())) return@combine null
            MoodAfterView(mood.score, mood.after, skipped = state != null && state.view.day == day && state.view.dueToday.any { it.skipped })
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

    /** A day filled in from the journal's "Add a day", or today's from its box on Today; a day that has a mood is changed. */
    fun saveMoodDay(mood: Mood) {
        viewModelScope.launch {
            val day = currentDay()
            if (!repository.addMoodDay(mood, day)) {
                repository.editMoodDay(mood)
            } else if (mood.day == day) {
                // Today's, given in full: nothing more to ask under it, and with everything
                // already done it is the day's only mood.
                repository.setSetting(SETTING_MOOD_MORE_CLOSED, day.toString())
                val allDone = todayState.value?.view?.takeIf { it.day == day }?.let { MoodInsights.allDone(it.dueToday) } == true
                if (allDone && mood.after == null) repository.setSetting(SETTING_MOOD_ONLY, day.toString())
                runCatching { phone.rescheduleReminders() }
            }
        }
    }

    /** Takes a day out of the journal. */
    fun deleteMoodDay(day: LocalDate) {
        viewModelScope.launch {
            repository.deleteMoodDay(day)
            runCatching { phone.rescheduleReminders() }
        }
    }

    /**
     * On a morning that started low: one gentle line naming a reward to look forward to, one the
     * balance already covers or the closest. Null with an empty shop, and while the shop is locked.
     */
    val lookForward: StateFlow<String?> =
        combine(todayMood, shopView, combine(settings, unlocked) { s, open -> s.locks.shop && !open }) { mood, shop, locked ->
            if (mood == null || mood.after != null || mood.score > 2 || shop == null || locked) return@combine null
            MoodInsights.lookForward(shop.rewards.map { it.reward }, shop.balance)?.let { MoodInsights.lookForwardLine(it, shop.balance, PointsLabel.name) }
        }.state(null)

    /** The raises made from "Mood and your goals" that still stand, by goal (see MoodRaises). */
    val moodRaises: StateFlow<Map<Long, io.github.randomtools3377.checklist.core.MoodRaise>> =
        combine(dao.observeSetting(io.github.randomtools3377.checklist.core.MoodRaises.SETTING), goalRows) { text, goals ->
            io.github.randomtools3377.checklist.core.MoodRaises
                .standing(io.github.randomtools3377.checklist.core.MoodRaises.decode(text), goals.map { it.toCore() })
                .associateBy { it.goalId }
        }.state(emptyMap())

    /** "Pay N points" on "Mood and your goals". */
    fun raiseGoalPoints(id: Long, points: Int) {
        viewModelScope.launch { repository.raiseGoalPoints(id, points) }
    }

    /** Undo after "Pay N points": the old points come back. */
    fun undoRaise(id: Long) {
        viewModelScope.launch { repository.undoRaise(id) }
    }

    /** Whether "Mood and your goals" marks what comes only from days something was noted on. */
    val moodHighlight: StateFlow<Boolean> = dao.observeSetting(MoodInsights.HIGHLIGHT_SETTING).map { it == "1" }.state(false)

    fun setMoodHighlight(on: Boolean) {
        viewModelScope.launch { repository.setSetting(MoodInsights.HIGHLIGHT_SETTING, if (on) "1" else "0") }
    }

    /** On a morning that started low (1 or 2): a goal still to do today that usually lifts the user. */
    val moodStart: StateFlow<Goal?> = combine(todayState, todayMood, moodFindings) { state, mood, findings ->
        if (state == null || mood == null || mood.after != null || mood.score > 2) return@combine null
        val view = state.view
        MoodInsights.startWith(findings, view.dueToday + view.thisWeek + view.thisMonth + view.thisYear)
    }.state(null)

    /** The goal [moodStart] names, as Today lists it, so it can be ticked where it is mentioned. */
    val moodStartRow: StateFlow<HabitRow?> = combine(todayState, moodStart) { state, goal ->
        val view = state?.view ?: return@combine null
        goal?.let { g -> (view.dueToday + view.thisWeek + view.thisMonth + view.thisYear).firstOrNull { it.goal.id == g.id } }
    }.state(null)

    /** Puts a goal right after another in a habit chain, from "Mood and your goals". */
    fun setGoalAfter(id: Long, after: Long) {
        viewModelScope.launch { repository.setGoalAfter(id, after) }
    }

    // Ideas to level up a skill area

    /** Every goal not removed, for the ideas: what the user does already. */
    val currentGoals: StateFlow<List<Goal>> = goalRows.map { rows -> rows.map { it.toCore() }.filter { !it.archived } }.state(emptyList())

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
    fun aiIdeas(area: io.github.randomtools3377.checklist.core.SkillArea, shown: List<String>, onResult: (List<io.github.randomtools3377.checklist.core.AreaIdea>, String?) -> Unit): Job =
        viewModelScope.launch {
            val ask = io.github.randomtools3377.checklist.core.AreaIdeasFromAi
            val level = character.value?.skillLevel(area)?.level ?: 1
            val prompt = ask.prompt(area, level, currentGoals.value, shown)
            ai(ask.SYSTEM, prompt, ask.SCHEMA, ask.MAX_TOKENS).fold(
                onSuccess = { answer ->
                    val ideas = ask.parse(answer, area, shown + currentGoals.value.map { it.name })
                    onResult(ideas, if (ideas.isEmpty()) "The AI's ideas didn't come out right this time. The app's own ideas are above, or try again." else null)
                },
                onFailure = { e -> onResult(emptyList(), trouble(e) + " The app's own ideas are above.") },
            )
        }

    /** The new level has been cheered. */
    fun levelSeen() {
        viewModelScope.launch {
            character.value?.let { sheet ->
                repository.setSetting(io.github.randomtools3377.checklist.core.Checkins.LEVEL_SEEN, sheet.level.level.toString())
                repository.setSetting(
                    io.github.randomtools3377.checklist.core.Checkins.SKILLS_SEEN,
                    io.github.randomtools3377.checklist.core.Checkins.encodeSkills(io.github.randomtools3377.checklist.core.SkillArea.entries.associateWith { sheet.skillLevel(it).level }),
                )
            }
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
            combine(goalRows, hidingPrivate) { goals, hide -> goals to hide },
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
            // The budget game, turned back on (it or Budget), starts again from today: the days
            // it was off aren't paid. This goes first, before the game sees it's on.
            if (on && (feature == Feature.BUDGET || feature == Feature.BUDGET_GAME)) {
                val other = if (feature == Feature.BUDGET) Feature.BUDGET_GAME else Feature.BUDGET
                if (!feature.isOn(dao.setting(feature.key)) && other.isOn(dao.setting(other.key))) repository.restartMoneyGame(nowLocal().date)
            }
            repository.setSetting(feature.key, if (on) feature.onValue else "0")
            // Shields count from the day they are first turned on, never earlier.
            if (on && feature == Feature.SHIELDS && dao.setting(io.github.randomtools3377.checklist.core.Shields.FROM) == null) {
                repository.setSetting(io.github.randomtools3377.checklist.core.Shields.FROM, currentDay().toString())
            }
        }
    }

    /**
     * The first-run setup's step while it shows, or null (see FirstRun): for someone brand new,
     * until it is done or skipped. Under the app's own tests only when a test starts it.
     */
    val setupStep: StateFlow<SetupStep?> = combine(
        dao.observeSetting(FirstRun.SETTING), dao.observeSetting(SETTING_GUIDE), goalRows, dao.observeRemovedGoals(),
    ) { setup, guide, goals, removed ->
        FirstRun.step(setup, guide, nothingYet = goals.isEmpty() && removed.isEmpty(), underTest = phone.underTest)
    }.state(null)

    /** Moves the setup on to [step], or ends it with null. */
    fun setupTo(step: SetupStep?) {
        viewModelScope.launch { repository.setSetting(FirstRun.SETTING, step?.name ?: FirstRun.DONE) }
    }

    /**
     * The setup's first step: turns on the extras of the [parts] picked (and off again any picked
     * before and switched off since), then on to the first goal.
     */
    fun setupParts(parts: List<SetupPart>) {
        FirstRun.changes(parts, features.value).forEach { (feature, on) -> setFeature(feature, on) }
        setupTo(SetupStep.GOAL)
    }

    /** A first goal from the setup, added with one tap. */
    fun addFirstGoal(goal: FirstGoal) {
        viewModelScope.launch { repository.saveHabit(FirstRun.draft(goal, currentDay())) }
    }

    /** The setup's last step: when the day starts and ends, if changed; then Today. */
    fun finishSetup(start: LocalTime?, endHour: Int) {
        viewModelScope.launch {
            val now = settings.value
            if (start != null && start != now.dayStart) repository.setDayStart(start)
            if (endHour != now.dayEndHour) repository.setDayEndHour(endHour)
            repository.setSetting(FirstRun.SETTING, FirstRun.DONE)
        }
    }

    /** "Start from scratch" at the end of the tour, for someone new. */
    fun closeWelcome() {
        viewModelScope.launch { repository.setSetting(SETTING_GUIDE, "done") }
    }

    /** Adds the tour's example goals and rewards, with Undo, which takes them away again. */
    fun addExamples(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val added = repository.addExamples(currentDay())
            offerUndo("Examples added", undone = "Examples taken away again") { repository.removeExamples(added) }
            onDone()
        }
    }

    /**
     * Adds a to-do from the box on Today; [onAdded] gets its id, to open it for a date. Added while
     * Today shows one [category], it takes that category, so it stays on screen.
     */
    fun addTodo(name: String, category: String? = null, onAdded: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch { onAdded(repository.saveTodo(TodoDraft(id = 0, name = name.trim(), category = category))) }
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
    val categories: StateFlow<List<String>> = goalRows.map { rows -> Categories.of(rows.map { it.toCore() }) }.state(emptyList())

    fun duplicateGoal(id: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch { repository.duplicateGoal(id, currentDay())?.let(onDone) }
    }

    fun restoreGoal(id: Long) {
        viewModelScope.launch { repository.restoreGoal(id, currentDay()) }
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
            offerUndo(if (ids.size == 1) "Removed 1 goal" else "Removed ${ids.size} goals", undone = if (ids.size == 1) "1 goal brought back" else "${ids.size} goals brought back") { ids.forEach { repository.restoreGoal(it, currentDay(), freshStart = false) } }
        }
    }

    fun removeGoal(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val name = repository.goal(id)?.name
            repository.removeGoal(id)
            onDone()
            if (name != null) offerUndo("Removed $name", undone = "$name brought back") { repository.restoreGoal(id, currentDay(), freshStart = false) }
        }
    }

    /** Moves a goal to the place of [besideId], the goal shown next to it in the list. */
    fun moveGoalTo(id: Long, besideId: Long) {
        viewModelScope.launch { repository.moveGoalTo(id, besideId) }
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

    /** The pay over time: each plan with the day it applies from. */
    val payPlans: StateFlow<PayPlans?> = dao.observeSetting(PayPlan.SETTING).map { PayPlans.decode(it) }.state(null)

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
                payPlans,
                dao.observeWorkDays(first.toEpochDay(), days.last().toEpochDay()),
                dao.observeMoney(first.toEpochDay(), days.last().toEpochDay()),
                moneyCategories,
            ) { plans, changed, money, categories ->
                BudgetView(
                    first = first,
                    thisMonth = first == Budget.monthOf(nowLocal().date),
                    plan = plans?.latest,
                    plans = plans,
                    changes = changed.map { it.toCore() }.associateBy { it.day },
                    entries = money.map { it.toCore() },
                    categories = categories,
                )
            }
        }.state(null)

    /**
     * What was left at the end of last month and hasn't been moved into savings or let go yet:
     * that month's first day, and the amount in cents. Null when nothing was left, when that
     * month had no money typed in, or once it has been dealt with.
     */
    val leftover: StateFlow<Pair<LocalDate, Long>?> = today.flatMapLatest { day ->
        val lastFirst = Budget.monthOf(Budget.monthOf(day).minusDays(1))
        val days = Pay.monthDays(lastFirst)
        combine(
            payPlans,
            dao.observeWorkDays(lastFirst.toEpochDay(), days.last().toEpochDay()),
            dao.observeMoney(lastFirst.toEpochDay(), days.last().toEpochDay()),
            moneyCategories,
            dao.observeSetting(io.github.randomtools3377.checklist.data.SETTING_LEFTOVER_DONE),
        ) { plan, changed, money, categories, done ->
            if (done == lastFirst.toString() || money.isEmpty()) return@combine null
            val month = Budget.month(lastFirst, plan, changed.map { it.toCore() }.associateBy { it.day }, money.map { it.toCore() }, categories, nowLocal())
            Budget.cents(month.leftAtEnd).takeIf { it > 0 }?.let { lastFirst to it }
        }
    }.state(null)

    /** Sets the Money game's daily line to the user's own amount, or back to the worked-out share with null. */
    fun setMoneyGameDaily(cents: Long?) {
        viewModelScope.launch { repository.setSetting(MoneyGame.DAILY, cents?.takeIf { it > 0 }?.toString().orEmpty()) }
    }

    /** Moves last month's leftover into [goalId], or into the plain Savings account with none. */
    fun saveLeftover(goalId: Long?) {
        val (first, cents) = leftover.value ?: return
        viewModelScope.launch {
            repository.saveLeftover(goalId, cents, Pay.monthDays(first).last(), "Left over")
            repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_LEFTOVER_DONE, first.toString())
        }
    }

    /** Leaves last month's leftover where it is, and stops asking about it. */
    fun dismissLeftover() {
        val first = leftover.value?.first ?: return
        viewModelScope.launch { repository.setSetting(io.github.randomtools3377.checklist.data.SETTING_LEFTOVER_DONE, first.toString()) }
    }

    /** Every changed work day, whatever month the Budget tab shows; null while loading. */
    val workDays: StateFlow<Map<LocalDate, WorkDay>?> = dao.observeWorkDays(Long.MIN_VALUE, Long.MAX_VALUE)
        .map { rows -> rows.map { it.toCore() }.associateBy { it.day } }.state(null)

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
            // Its reminder, if it has one, is set (or taken away) straight away.
            runCatching { phone.rescheduleReminders() }
            onDone()
        }
    }

    fun removeBill(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.removeBill(id)
            runCatching { phone.rescheduleReminders() }
            onDone()
        }
    }

    fun saveSavingGoal(goal: SavingGoal) {
        viewModelScope.launch { repository.saveSavingGoal(goal) }
    }

    /** Removes a saving goal, taking out what's still in it first when [takeOut]. */
    fun removeSavingGoal(id: Long, takeOut: Boolean = false) {
        viewModelScope.launch { repository.removeSavingGoal(id, if (takeOut) nowLocal().date else null) }
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
    val moneyGame: StateFlow<MoneyGameView?> = combine(features, budget, combine(savings, dao.observeSetting(MoneyGame.DAILY)) { _, own -> own?.toLongOrNull()?.takeIf { it > 0 } }, bills, workDays) { on, view, own, _, _ ->
        if (Feature.BUDGET !in on || Feature.BUDGET_GAME !in on || view == null || !view.thisMonth) return@combine null
        // From the same money, work days and bills as the days paid, back to the day the game started.
        val game = repository.moneyGameToday(nowLocal().date)
        val share = game.share
        val before = game.streakBefore
        MoneyGameView(share, game.spentCents, if (share != null && MoneyGame.day(game.spentCents, share) != MoneyGame.Day.OVER) before + 1 else before, before, own)
    }.state(null)

    /** Whether the privacy lock covers the budget, read now, for before [settings] has been loaded. */
    suspend fun budgetLocked(): Boolean = dao.setting(LOCK_BUDGET) == "1"

    /** Puts in the starting categories the first time the budget is opened. */
    fun startBudget() {
        viewModelScope.launch { repository.startBudget() }
    }

    /** Saves the pay from [from] on, or from the start with null (see [Repository.savePay]). */
    fun savePay(plan: PayPlan, from: LocalDate?, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.savePay(plan, from)
            onDone()
        }
    }

    suspend fun loadMoney(id: Long): MoneyEntry? = repository.moneyEntry(id)

    fun saveMoney(entry: MoneyEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = repository.saveMoney(entry)
            onDone()
            // New spending answers with where that leaves the user, and can be taken back.
            if (entry.id == 0L) spendingNote(id)?.let { note -> offerUndo(note) { repository.removeMoney(id) } }
        }
    }

    /**
     * What the Undo bar says after the spending [id] was typed in (see SpendingNote): what's
     * left of its category's limit, and with the budget game on how today stands against its
     * line. Null for money in or into savings, and while the budget is locked: nothing of it
     * shows then.
     */
    private suspend fun spendingNote(id: Long): String? {
        val saved = repository.moneyEntry(id) ?: return null
        if (saved.kind != MoneyKind.SPENT && !saved.spentFromSavings) return null
        if (budgetLocked() && !unlocked.value) return null
        val today = nowLocal().date
        val category = saved.categoryId?.let { wanted -> dao.moneyCategories().firstOrNull { it.id == wanted }?.toCore() }
        val month = Budget.monthOf(saved.day)
        val inCategory = dao.observeMoney(month.toEpochDay(), Pay.monthDays(month).last().toEpochDay()).first()
            .map { it.toCore() }.filter { it.kind == MoneyKind.SPENT && it.categoryId == saved.categoryId }.sumOf { it.cents }
        val on = features.value
        val game = if (Feature.BUDGET in on && Feature.BUDGET_GAME in on) repository.moneyGameToday(today) else null
        return io.github.randomtools3377.checklist.core.SpendingNote.after(
            saved, today, category, inCategory, game?.share?.let { Budget.cents(it) }, game?.spentCents ?: 0,
        ) { cents -> moneyText(cents / 100.0) }
    }

    /** The category of the spending typed in last, for the Spent box to start on; null when there is none in use. */
    suspend fun lastSpentCategory(): Long? {
        val last = dao.lastSpentCategory() ?: return null
        return dao.moneyCategories().firstOrNull { it.id == last && !it.archived }?.id
    }

    /** The amounts usually entered for [goalId], for one-tap buttons (see [Amounts.usual]). */
    suspend fun usualAmounts(goalId: Long): List<Double> =
        Amounts.usual(dao.latestAmounts(goalId, Amounts.USUAL_FROM).map { it.toCore() })

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
    private suspend fun countRewardSpending(purchaseId: Long, rewardId: Long, name: String, fromSaving: Long? = null) {
        if (Feature.BUDGET !in features.value) return
        val value = repository.reward(rewardId)?.value?.takeIf { it > 0 } ?: return
        repository.rewardBought(purchaseId, name, value, nowLocal().date, fromSaving)
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

    /**
     * Whether backup files are protected with a passphrase. The key made from it is kept on this
     * device only, with the app's own data (never the passphrase, and never in a backup file),
     * so exports and automatic backups can be protected without asking each time.
     */
    val backupProtected: StateFlow<Boolean> = dao.observeSetting(SETTING_BACKUP_KEY)
        .map { BackupKey.decode(it) != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private suspend fun backupKey(): BackupKey? = BackupKey.decode(dao.setting(SETTING_BACKUP_KEY))

    /**
     * Turns the passphrase on, or changes it. Files saved from now on need it to be opened. The
     * device's own crypto is checked first (see [BackupCrypto.works]).
     */
    fun setBackupPassphrase(passphrase: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val key = runCatching {
                withContext(Dispatchers.Default) {
                    BackupCrypto.newKey(passphrase).takeIf { BackupCrypto.works(passphrase) }
                }
            }.getOrNull()
            if (key != null) repository.setSetting(SETTING_BACKUP_KEY, key.encode())
            onDone(key != null)
        }
    }

    /** Turns the passphrase off: files saved from now on are plain. Files already saved keep theirs. */
    fun clearBackupPassphrase() {
        viewModelScope.launch { repository.setSetting(SETTING_BACKUP_KEY, "") }
    }

    /** The backup file's text, protected with the passphrase when one is set. */
    private suspend fun backupText(): String {
        val text = repository.exportBackup()
        val key = backupKey() ?: return text
        return withContext(Dispatchers.Default) { BackupCrypto.seal(text, key) }
    }

    /** Writes everything to [uri]. Reports a plain message either way. */
    fun exportTo(file: PickedFile, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val protected = backupKey() != null
                val text = backupText()
                phone.writeText(file, text)
                phone.markBackedUp()
                refreshBackup()
                if (protected) "Exported, protected with your passphrase. Keep the file somewhere safe."
                else "Exported. Keep the file somewhere safe."
            } catch (e: NotSavedException) {
                "Not saved. Nothing was exported."
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
            } catch (e: NotSavedException) {
                "Not saved."
            } catch (e: Exception) {
                "Couldn't write the file."
            }
            onDone(message)
        }
    }

    private fun saveSheet(file: PickedFile, onDone: (String) -> Unit, text: suspend () -> String) {
        viewModelScope.launch {
            val message = try {
                phone.writeText(file, text())
                "Saved. Open it in any spreadsheet app."
            } catch (e: NotSavedException) {
                "Not saved."
            } catch (e: Exception) {
                "Couldn't write the file."
            }
            onDone(message)
        }
    }

    /** Writes the goal record, each goal each day, to [file] as a spreadsheet. */
    fun exportGoalRecord(file: PickedFile, onDone: (String) -> Unit) = saveSheet(file, onDone) { repository.goalRecordSpreadsheet(currentDay()) }

    /** Writes the mood journal to [file] as a spreadsheet. */
    fun exportMoodJournal(file: PickedFile, onDone: (String) -> Unit) = saveSheet(file, onDone) { repository.moodSpreadsheet() }

    /** Writes the budget of the month starting on [first] to [file] as a spreadsheet. */
    fun exportBudgetMonth(first: LocalDate, file: PickedFile, onDone: (String) -> Unit) = saveSheet(file, onDone) { repository.budgetSpreadsheet(first, nowLocal()) }

    /** The automatic backups in the chosen folder, newest first, to pick one to restore. */
    fun listAutoBackups(onList: (List<Pair<String, PickedFile>>) -> Unit) {
        viewModelScope.launch {
            onList(runCatching { phone.autoBackups() }.getOrDefault(emptyList()))
        }
    }

    /** What reading a backup file found. */
    sealed interface BackupRead {
        /** A backup, with what it holds and what this device holds now, to confirm before anything changes. */
        data class Ready(val backup: Backup, val file: BackupSummary, val device: BackupSummary) : BackupRead

        /** A protected file: its [text] waits for the passphrase (see [openBackup]). */
        data class Protected(val text: String) : BackupRead

        data class Failed(val message: String) : BackupRead
    }

    private suspend fun ready(backup: Backup): BackupRead = BackupRead.Ready(backup, BackupSummary.of(backup), repository.summary())

    /** Reads a backup from [file] without changing anything, so the user can confirm it first. */
    fun readBackup(file: PickedFile, onRead: (BackupRead) -> Unit) {
        viewModelScope.launch {
            val read = try {
                val text = phone.readText(file)
                if (BackupCrypto.isEncrypted(text)) BackupRead.Protected(text) else ready(Backup.decode(text))
            } catch (e: BackupError) {
                BackupRead.Failed(e.message ?: "Couldn't read that file.")
            } catch (e: Exception) {
                BackupRead.Failed("Couldn't read that file.")
            }
            onRead(read)
        }
    }

    /** Opens the protected file [text] with [passphrase]. A wrong passphrase says so plainly. */
    fun openBackup(text: String, passphrase: String, onRead: (BackupRead) -> Unit) {
        viewModelScope.launch {
            val read = try {
                ready(Backup.decode(withContext(Dispatchers.Default) { BackupCrypto.open(text, passphrase) }))
            } catch (e: WrongPassphraseException) {
                BackupRead.Failed(BackupCrypto.WRONG_PASSPHRASE)
            } catch (e: BackupError) {
                BackupRead.Failed(e.message ?: "Couldn't read that file.")
            } catch (e: Exception) {
                BackupRead.Failed("Couldn't read that file.")
            }
            onRead(read)
        }
    }

    /**
     * Replaces everything with [backup]. With an automatic backup folder set, what is here now is
     * saved there first as a "before import" copy, to undo a wrong file with "Restore an
     * automatic backup"; if that copy can't be saved, nothing is replaced.
     */
    fun restore(backup: Backup, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val copied = saveBeforeImport()
                if (copied == false) {
                    onDone("Couldn't save a copy in your backup folder first, so nothing was changed. Tap \"Export first\", or choose the folder again.")
                    return@launch
                }
                // A bet still open in an older backup gives its stake back.
                parts.restore(backup)
                // What this phone keeps by goal id stops: after a restore, an id can mean another goal.
                phone.forgetSnoozes()
                dropFocus()
                if (copied == true) "Imported. What was here before is in your backup folder: \"Restore an automatic backup\" lists it as \"Before import\"."
                else "Imported. Everything now matches the backup."
            } catch (e: Exception) {
                "Couldn't import. Nothing was changed."
            }
            onDone(message)
        }
    }

    /**
     * The "before import" copy: true when saved, false when it couldn't be, and null when there
     * is no folder, nothing here to keep, or this device doesn't keep one.
     */
    private suspend fun saveBeforeImport(): Boolean? {
        if (!phone.canSaveBeforeImport || phone.backupFolder() == null) return null
        if (repository.summary().isEmpty) return null
        return try {
            phone.saveBeforeImport(AutoBackupFiles.beforeImportName(nowLocal()), backupText())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * "Erase everything and start fresh": everything the app holds goes, settings included, and
     * the backup passphrase with them. Backup files already saved, and the folder chosen for
     * them, are left alone.
     */
    fun eraseEverything(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                repository.eraseEverything()
                // What this device keeps by goal id stops too, then its reminders are set again (none).
                phone.forgetSnoozes()
                dropFocus()
                runCatching { phone.rescheduleReminders() }
                refreshBackup()
                "Erased. The app is as new."
            } catch (e: Exception) {
                "Couldn't erase. Nothing was changed."
            }
            onDone(message)
        }
    }
}
