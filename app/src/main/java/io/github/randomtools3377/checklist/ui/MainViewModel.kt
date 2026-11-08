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
data class GoalsView(val goals: List<Goal>, val steps: Map<Long, List<Step>>)

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
)

data class Settings(val dayEndHour: Int, val defaultFinish: LocalTime?, val order: String)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ChecklistApplication).container
    private val dao = container.db.dao()
    private val book = container.book
    private val repository = container.repository

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<Settings> = combine(
        dao.observeSetting(SETTING_DAY_END_HOUR),
        dao.observeSetting(SETTING_DEFAULT_FINISH),
        dao.observeSetting(SETTING_TODAY_ORDER),
    ) { hour, finish, order ->
        Settings(
            dayEndHour = parseDayEndHour(hour),
            defaultFinish = finish?.toIntOrNull()?.toTime(),
            order = if (order == ORDER_OWN) ORDER_OWN else ORDER_RECOMMENDED,
        )
    }.state(Settings(0, null, ORDER_RECOMMENDED))

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
        val since = minOf(Days.earliestOpenDay(day), day.minusDays(Usual.WINDOW_DAYS))
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
            TodayList.build(day, goals.map { it.toCore() }, steps.map { it.toCore() }, ticks, balance) to usual
        }
        combine(view, now, settings) { (todayView, usual), moment, s ->
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
            TodayState(todayView, moment, pace, plan, recommended, longPaces, s.defaultFinish != null)
        }
    }.state(null)

    val goalsView: StateFlow<GoalsView?> = combine(dao.observeGoals(), dao.observeSteps()) { goals, steps ->
        GoalsView(goals.map { it.toCore() }, steps.map { it.toCore() }.groupBy { it.goalId })
    }.state(null)

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
