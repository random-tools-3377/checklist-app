package io.github.randomtools3377.checklist.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.BackupError
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.data.SETTING_WIDGET_STYLE
import io.github.randomtools3377.checklist.data.SETTING_APP_ICON
import io.github.randomtools3377.checklist.widget.WidgetStyle
import io.github.randomtools3377.checklist.AppIcons
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
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.LOCK_SHOP
import io.github.randomtools3377.checklist.data.SETTING_STREAK_TARGET
import io.github.randomtools3377.checklist.data.SETTING_STREAK_VALUE
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.ORDER_OWN
import io.github.randomtools3377.checklist.data.ORDER_RECOMMENDED
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.Backups
import io.github.randomtools3377.checklist.data.DEFAULT_SNOOZE_MINUTES
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_OWN_REASONS
import io.github.randomtools3377.checklist.data.SETTING_SNOOZE
import io.github.randomtools3377.checklist.data.SETTING_WEEKLY
import io.github.randomtools3377.checklist.reminders.Focus
import io.github.randomtools3377.checklist.reminders.FocusState
import io.github.randomtools3377.checklist.reminders.Reminders
import io.github.randomtools3377.checklist.core.BackupReminder
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SETTING_MORNING_PLAN
import io.github.randomtools3377.checklist.data.SETTING_TODAY_ORDER
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Every goal, for the Goals screen. */
data class GoalsView(val goals: List<Goal>, val steps: Map<Long, List<Step>>, val hiddenPrivate: Int = 0)

/** What the database gives the Today screen, before the lock and the streaks are applied. */
private data class Raw(val goals: List<Goal>, val steps: List<Step>, val ticks: List<Tick>, val balance: Int, val usual: Map<Long, Int>)

/** How far back streaks are counted. */
private const val STREAK_DAYS = 400

/** How many recently picked colours the colour picker offers again. */
private const val MAX_RECENT_COLORS = 6

/** The most points a week the target can ask for. */
private const val MAX_WEEKLY_TARGET = 100_000

/** How long to wait after a change before looking for new badges, so a run of changes is looked at once. */
private const val BADGE_CHECK_DELAY_MS = 800L

data class RewardRow(val reward: Reward, val usual: String?)

data class ShopView(val balance: Int, val rewards: List<RewardRow>, val bought: List<Purchase>)

/** Everything the Today screen shows. */
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
)

/** What the privacy lock covers, as chosen in Settings. */
data class Locks(
    val history: Boolean = false,
    val shop: Boolean = false,
    val privateGoals: Boolean = false,
    val reasons: Boolean = false,
    val gaps: Boolean = false,
    val review: Boolean = false,
)

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
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ChecklistApplication).container
    private val dao = container.db.dao()
    private val book = container.book
    private val repository = container.repository

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
            combine(dao.observeSetting(LOCK_GAPS), dao.observeSetting(LOCK_REVIEW)) { g, r -> g to r },
        ) { h, sh, p, r, (g, rv) ->
            Locks(history = h == "1", shop = sh == "1", privateGoals = p == "1", reasons = r == "1", gaps = g == "1", review = rv == "1")
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
        ) { lapses, checks -> lapses to checks }
        combine(view, lapsesFlow, now, settings, unlocked) { raw, (lapses, checks), moment, s, isUnlocked ->
            val hidePrivate = s.locks.privateGoals && !isUnlocked
            val shownGoals = if (hidePrivate) raw.goals.filter { !it.private } else raw.goals
            val todayView = TodayList.build(day, shownGoals, raw.steps, raw.ticks, raw.balance, lapses, checks)
            val usual = raw.usual
            val records = StreakCounter.records(day.minusDays(STREAK_DAYS), day, raw.goals, raw.ticks.filter { it.stepId == null }, lapses)
            val streaks = StreakCounter.count(records, day, s.streak)
            val paceSettings = PaceSettings(s.dayEndHour, s.defaultFinish, s.dayStart)
            val pace = Pace.today(moment, day, todayView.dueToday, paceSettings)
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
            TodayState(
                todayView, moment, pace, plan, recommended, longPaces, s.defaultFinish != null,
                streaks, s.streak, hiddenPrivate = raw.goals.count { it.private && !it.archived } - shownGoals.count { it.private && !it.archived },
                nextSlots = nextSlots,
                hasDayStart = s.dayStart != null,
                lastTicks = (todayView.dueToday + todayView.thisWeek + todayView.thisMonth + todayView.thisYear).mapNotNull { row ->
                    raw.ticks.filter { it.stepId == null && it.countsToward(row.goal, row.window.start) }.maxByOrNull { it.at }?.let { row.goal.id to it }
                }.toMap(),
            )
        }
    }.state(null)

    val goalsView: StateFlow<GoalsView?> = combine(dao.observeGoals(), dao.observeSteps(), settings, unlocked) { goals, steps, s, isUnlocked ->
        val all = goals.map { it.toCore() }
        val shown = if (s.locks.privateGoals && !isUnlocked) all.filter { !it.private } else all
        GoalsView(shown, steps.map { it.toCore() }.groupBy { it.goalId }, hiddenPrivate = all.size - shown.size)
    }.state(null)

    // How the app looks

    val appearance: StateFlow<Appearance> = combine(
        listOf(
            SETTING_THEME, SETTING_COLOR, SETTING_RECENT_COLORS, SETTING_TEXT_SIZE, SETTING_CARD_SIZE,
            SETTING_TODAY_LAYOUT, SETTING_BACKDROP, SETTING_BACKDROP_DIM, SETTING_PHOTO_STAMP,
            SETTING_TODAY_SECTIONS, SETTING_HIDDEN_TABS, SETTING_FIRST_TAB,
            SETTING_FONT, SETTING_CELEBRATE_SOUND, SETTING_CELEBRATE_BUZZ, SETTING_CONFETTI, SETTING_DEFAULT_GIF,
            SETTING_APP_ICON, SETTING_WIDGET_STYLE,
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
            tabs = TabChoice.decode(v[10], v[11]),
            font = FontChoice.entries.firstOrNull { it.name == v[12] } ?: FontChoice.PHONE,
            celebrate = CelebrationStyle(
                sound = v[13] == "1",
                buzz = v[14] != "0",
                confetti = ConfettiAmount.entries.firstOrNull { it.name == v[15] } ?: ConfettiAmount.SOME,
                defaultGif = v[16]?.takeIf { Celebration.isValid(it) },
            ),
            icon = AppIcons.of(v[17]),
            widgetStyle = WidgetStyle.entries.firstOrNull { it.name == v[18] } ?: WidgetStyle.SOLID,
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
    fun pickPhoto(uri: android.net.Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { container.backgroundPhoto.add(getApplication<android.app.Application>(), uri) }
            if (ok) {
                repository.setSetting(SETTING_PHOTO_STAMP, System.currentTimeMillis().toString())
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
            Badges.earned(day, records, streak, goals, steps, ticks, entries, target)
        }
        val kept = Badges.decode(dao.setting(SETTING_BADGES))
        val merged = Badges.merge(kept, earned)
        if (merged.size > kept.size) repository.setSetting(SETTING_BADGES, Badges.encode(merged))
    }

    // Skips, misses and penalties

    private val lapses = container.lapses

    /** Misses still waiting for a reason. They come first when the app opens. */
    val unexplained: StateFlow<List<Lapse>?> = dao.observeUnexplained().map { rows -> rows.map { it.toCore() } }.state(null)

    /** The penalty task owed right now, if any. It comes next, and fills the screen until done. */
    val openPenalty: StateFlow<Penalty?> = dao.observeOpenPenalties().map { rows -> rows.firstOrNull()?.toCore() }.state(null)

    val penaltyTasks: StateFlow<List<PenaltyTask>> = dao.observePenaltyTasks().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /** Goals skipped or missed in the last two weeks, which a penalty can be paid off by making up. */
    val makeUpChoices: StateFlow<List<Goal>> = today.flatMapLatest { day ->
        combine(dao.observeLapsesSince(day.minusDays(14).toEpochDay()), dao.observeGoals()) { recent, goals ->
            val ids = recent.filter { !it.forgotten }.map { it.goalId }.toSet()
            goals.map { it.toCore() }.filter { it.id in ids && it.kind == GoalKind.HABIT }
        }
    }.state(emptyList())

    /** Looks for misses and gaps since the last check. Called when the app comes to the front. */
    fun closeDays() {
        viewModelScope.launch { container.closeDays() }
        autoBackup()
        refreshFocus()
    }

    // The focus timer

    val focus = MutableStateFlow<FocusState?>(null)

    private fun refreshFocus() {
        focus.value = Focus.current(getApplication())
        viewModelScope.launch { Reminders.reschedule(getApplication()) }
    }

    fun startFocus(goalId: Long, minutes: Int) {
        Focus.start(getApplication(), goalId, minutes)
        refreshFocus()
    }

    fun pauseFocus() {
        Focus.pause(getApplication())
        refreshFocus()
    }

    fun resumeFocus() {
        Focus.resume(getApplication())
        refreshFocus()
    }

    fun stopFocus() {
        Focus.stop(getApplication())
        refreshFocus()
    }

    /** Time's up, or "Done now": the goal is ticked. */
    fun finishFocus(onResult: (TickResult) -> Unit) {
        val goalId = focus.value?.goalId ?: return
        stopFocus()
        tickHabit(goalId, onResult)
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
            val context = getApplication<Application>()
            val state = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val last = Backups.lastBackup(context)
                val hasData = dao.activeGoals().isNotEmpty()
                val due = if (Backups.reminderOn(context) && Backups.laterUntil(context) < now &&
                    BackupReminder.due(last, Backups.firstSeen(context), now, hasData)
                ) {
                    last?.let { BackupReminder.daysSince(it, now) } ?: -1
                } else {
                    null
                }
                BackupState(last, Backups.folderName(context), Backups.folder(context) != null, folderGone.value, Backups.reminderOn(context), due)
            }
            backupState.value = state
        }
    }

    private fun autoBackup() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                Backups.autoBackupIfDue(context, Days.dayOf(nowLocal(), settings.value.dayEndHour)) { repository.exportBackup() }
            }
            if (result == Backups.AutoResult.FolderGone) folderGone.value = true
            if (result == Backups.AutoResult.Saved) folderGone.value = false
            refreshBackup()
        }
    }

    /** "Later" on Today's backup reminder: it comes back tomorrow. */
    fun backupLater() {
        Backups.later(getApplication(), System.currentTimeMillis() + 86_400_000L)
        refreshBackup()
    }

    fun setBackupReminder(on: Boolean) {
        Backups.setReminderOn(getApplication(), on)
        refreshBackup()
    }

    /** Picks the automatic backup's folder (null turns it off), then backs up there right away. */
    fun setBackupFolder(uri: Uri?) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { runCatching { Backups.setFolder(context, uri) } }
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
        viewModelScope.launch { container.gaps.explain(gapId, reason, note, reasonText) }
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
            dao.observeGoals(),
            dao.observeHabitTicksSince(windowsFrom.toEpochDay()),
            dao.observeLapsesSince(windowsFrom.toEpochDay()),
            dao.observeEntries(from.toEpochDay(), to.toEpochDay()),
            dao.observeGaps(),
        ) { goals, ticks, lapsesRows, entries, gapRows ->
            Review.summarize(
                from = from,
                to = to,
                today = todayDay,
                goals = goals.map { it.toCore() },
                ticks = ticks.map { it.toCore() },
                lapses = lapsesRows.map { it.toCore() },
                entries = entries.map { it.toCore() },
                gaps = gapRows.map { it.toCore() },
            )
        }
    }.state(null)

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
        combine(dao.observeBalance(), dao.observeRewards(), dao.observeUnusedPurchases(), pointsPerDay) { balance, rewards, bought, perDay ->
            ShopView(
                balance = balance,
                rewards = rewards.map { RewardRow(it.toCore(), Usual.describe(it.price, perDay)) },
                bought = bought.map { it.toCore() },
            )
        }
    }.state(null)

    /** Every change to the balance, newest first. */
    val history: StateFlow<List<Entry>?> = dao.observeEntries().map { rows -> rows.map { it.toCore() } }.state(null)

    // Ticking and the shop. Results come back on the main thread.

    fun tickHabit(goalId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch { onResult(book.tickHabit(goalId)) }
    }

    fun undoHabitTick(goalId: Long) {
        viewModelScope.launch { book.undoHabitTick(goalId) }
    }

    /** Ticks one item of a routine; the last one ticks the routine. */
    fun checkItem(stepId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch { onResult(book.checkItem(stepId)) }
    }

    fun uncheckItem(stepId: Long) {
        viewModelScope.launch { book.uncheckItem(stepId) }
    }

    /** "Done" on a routine: every item still open, and so the routine. */
    fun finishRoutine(goalId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch { onResult(book.finishRoutine(goalId)) }
    }

    fun undoRoutine(goalId: Long) {
        viewModelScope.launch { book.undoRoutine(goalId) }
    }

    fun tickStep(stepId: Long, onResult: (TickResult) -> Unit = {}) {
        viewModelScope.launch { onResult(book.tickStep(stepId)) }
    }

    fun undoStep(stepId: Long) {
        viewModelScope.launch { book.undoStep(stepId) }
    }

    fun buy(rewardId: Long, onResult: (BuyResult) -> Unit = {}) {
        viewModelScope.launch { onResult(book.buy(rewardId)) }
    }

    fun use(purchaseId: Long) {
        viewModelScope.launch { book.use(purchaseId) }
    }

    fun returnPurchase(purchaseId: Long) {
        viewModelScope.launch { book.returnPurchase(purchaseId) }
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

    fun saveProject(draft: ProjectDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveProject(draft)
            onDone()
        }
    }

    /** Goals the user removed, to bring back. */
    val removedGoals: StateFlow<List<Goal>> = dao.observeRemovedGoals().map { rows -> rows.map { it.toCore() } }.state(emptyList())

    /** The categories the goals use, offered in the goal editor. */
    val categories: StateFlow<List<String>> = dao.observeGoals().map { rows -> Categories.of(rows.map { it.toCore() }) }.state(emptyList())

    fun duplicateGoal(id: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch { repository.duplicateGoal(id)?.let(onDone) }
    }

    fun restoreGoal(id: Long) {
        viewModelScope.launch { repository.restoreGoal(id) }
    }

    fun removeGoal(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.removeGoal(id)
            onDone()
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
    fun exportTo(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val text = repository.exportBackup()
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val stream = resolver.openOutputStream(uri, "wt") ?: error("no stream")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                Backups.markBackedUp(getApplication())
                refreshBackup()
                "Exported. Keep the file somewhere safe."
            } catch (e: Exception) {
                "Couldn't write the file. Nothing was exported."
            }
            onDone(message)
        }
    }

    /** Reads a backup from [uri] without changing anything, so the user can confirm it first. */
    fun readBackup(uri: Uri, onRead: (Backup?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val stream = resolver.openInputStream(uri) ?: error("no stream")
                    stream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
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
