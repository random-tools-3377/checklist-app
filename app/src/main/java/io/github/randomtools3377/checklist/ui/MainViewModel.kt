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
import io.github.randomtools3377.checklist.core.Entry
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
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.usualMinuteOfDay
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.Penalty
import io.github.randomtools3377.checklist.core.PenaltyTask
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.StreakCounter
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.data.LOCK_HISTORY
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
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
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
)

/** What the privacy lock covers, as chosen in Settings. */
data class Locks(val history: Boolean = false, val shop: Boolean = false, val privateGoals: Boolean = false)

data class Settings(
    val dayEndHour: Int,
    val defaultFinish: LocalTime?,
    val order: String,
    val streak: StreakSettings = StreakSettings(),
    val locks: Locks = Locks(),
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
        ) { hour, finish, order -> Triple(hour, finish, order) },
        combine(dao.observeSetting(SETTING_STREAK_TARGET), dao.observeSetting(SETTING_STREAK_VALUE)) { target, value ->
            val defaults = StreakSettings()
            StreakSettings(
                target = target?.let { runCatching { StreakTarget.valueOf(it) }.getOrNull() } ?: defaults.target,
                value = value?.toIntOrNull() ?: defaults.value,
            )
        },
        combine(dao.observeSetting(LOCK_HISTORY), dao.observeSetting(LOCK_SHOP), dao.observeSetting(LOCK_PRIVATE)) { h, sh, p ->
            Locks(history = h == "1", shop = sh == "1", privateGoals = p == "1")
        },
    ) { (hour, finish, order), streak, locks ->
        Settings(
            dayEndHour = parseDayEndHour(hour),
            defaultFinish = finish?.toIntOrNull()?.toTime(),
            order = if (order == ORDER_OWN) ORDER_OWN else ORDER_RECOMMENDED,
            streak = streak,
            locks = locks,
        )
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
        val lapsesFlow = dao.observeLapsesSince(since.toEpochDay()).map { rows -> rows.map { it.toCore() } }
        combine(view, lapsesFlow, now, settings, unlocked) { raw, lapses, moment, s, isUnlocked ->
            val hidePrivate = s.locks.privateGoals && !isUnlocked
            val shownGoals = if (hidePrivate) raw.goals.filter { !it.private } else raw.goals
            val todayView = TodayList.build(day, shownGoals, raw.steps, raw.ticks, raw.balance, lapses)
            val usual = raw.usual
            val records = StreakCounter.records(day.minusDays(STREAK_DAYS), day, raw.goals, raw.ticks.filter { it.stepId == null }, lapses)
            val streaks = StreakCounter.count(records, day, s.streak)
            val paceSettings = PaceSettings(s.dayEndHour, s.defaultFinish)
            val pace = Pace.today(moment, day, todayView.dueToday, paceSettings)
            val recommended = s.order == ORDER_RECOMMENDED
            val plan = if (recommended) {
                Order.recommend(moment, day, todayView.dueToday, paceSettings, usual, pace)
            } else {
                todayView.dueToday.map { row ->
                    val at = row.goal.at?.let { Pace.on(day, it, s.dayEndHour) }
                    Planned(row, at, fixed = at != null, late = at != null && !row.done && at < moment)
                }
            }
            val longPaces = buildMap {
                (todayView.thisWeek + todayView.thisMonth + todayView.thisYear).forEach { row ->
                    Pace.forHabit(row, day)?.let { put(row.goal.id, it) }
                }
                todayView.projects.forEach { project -> Pace.forProject(project, day)?.let { put(project.goal.id, it) } }
            }
            TodayState(
                todayView, moment, pace, plan, recommended, longPaces, s.defaultFinish != null,
                streaks, s.streak, hiddenPrivate = raw.goals.count { it.private && !it.archived } - shownGoals.count { it.private && !it.archived },
            )
        }
    }.state(null)

    val goalsView: StateFlow<GoalsView?> = combine(dao.observeGoals(), dao.observeSteps(), settings, unlocked) { goals, steps, s, isUnlocked ->
        val all = goals.map { it.toCore() }
        val shown = if (s.locks.privateGoals && !isUnlocked) all.filter { !it.private } else all
        GoalsView(shown, steps.map { it.toCore() }.groupBy { it.goalId }, hiddenPrivate = all.size - shown.size)
    }.state(null)

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

    /** Looks for misses since the last check. Called when the app comes to the front. */
    fun closeDays() {
        viewModelScope.launch { lapses.closeDays() }
    }

    suspend fun checkSkip(goalId: Long): SkipCheck = lapses.checkSkip(goalId)

    fun skip(goalId: Long, reason: Reason?, note: String?, onResult: (SkipResult) -> Unit = {}) {
        viewModelScope.launch { onResult(lapses.skip(goalId, reason, note)) }
    }

    fun explain(lapseId: Long, reason: Reason, note: String?) {
        viewModelScope.launch { lapses.explain(lapseId, reason, note) }
    }

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
        val steps = if (goal.kind == GoalKind.PROJECT) repository.steps(id) else emptyList()
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
