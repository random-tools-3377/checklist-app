package io.github.randomtools3377.checklist.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Purchase
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.TodayView
import io.github.randomtools3377.checklist.core.Usual
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import kotlinx.datetime.LocalDate

/** Every goal, for the Goals screen. */
data class GoalsView(val goals: List<Goal>, val steps: Map<Long, List<Step>>)

data class RewardRow(val reward: Reward, val usual: String?)

data class ShopView(val balance: Int, val rewards: List<RewardRow>, val bought: List<Purchase>)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ChecklistApplication).container
    private val dao = container.db.dao()
    private val book = container.book
    private val repository = container.repository

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val dayEndHour: StateFlow<Int> = dao.observeSetting(SETTING_DAY_END_HOUR).map { parseDayEndHour(it) }.state(0)

    /** The user's current day. Checked every half minute, so the list turns over when the day ends. */
    private val today: Flow<LocalDate> = combine(
        flow {
            while (true) {
                emit(nowLocal())
                delay(30_000)
            }
        },
        dao.observeSetting(SETTING_DAY_END_HOUR),
    ) { now, hour -> Days.dayOf(now, parseDayEndHour(hour)) }.distinctUntilChanged()

    val todayView: StateFlow<TodayView?> = today.flatMapLatest { day ->
        combine(
            dao.observeGoals(),
            dao.observeSteps(),
            dao.observeHabitTicksSince(Days.earliestOpenDay(day).toEpochDay()),
            dao.observeStepTicks(),
            dao.observeBalance(),
        ) { goals, steps, habitTicks, stepTicks, balance ->
            TodayList.build(
                day = day,
                goals = goals.map { it.toCore() },
                steps = steps.map { it.toCore() },
                ticks = (habitTicks + stepTicks).map { it.toCore() },
                balance = balance,
            )
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

    fun setDayEndHour(hour: Int) {
        viewModelScope.launch { repository.setDayEndHour(hour) }
    }
}
