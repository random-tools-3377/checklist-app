package io.github.randomtools3377.checklist.tile

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Order
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.ORDER_OWN
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SETTING_TODAY_ORDER
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The quick settings tile: it shows the next goal due today, in Today's order, and a tap ticks
 * it. Goals behind the privacy lock are left out. Nothing goes over the network.
 */
class NextGoalTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { show(NextGoal.find(applicationContext)) }
    }

    override fun onClick() {
        super.onClick()
        val tick = {
            scope.launch {
                val next = NextGoal.find(applicationContext) ?: return@launch
                val book = (applicationContext as ChecklistApplication).container.book
                val result = if (next.isRoutine) book.finishRoutine(next.goal.id) else book.tickHabit(next.goal.id)
                if (result is io.github.randomtools3377.checklist.core.TickResult.Ticked && result.finished) {
                    io.github.randomtools3377.checklist.reminders.Reminders.afterDone(applicationContext, next.goal.id)
                }
                show(NextGoal.find(applicationContext))
            }
            Unit
        }
        if (isLocked) unlockAndRun { tick() } else tick()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun show(next: HabitRow?) = withContext(Dispatchers.Main) {
        val tile = qsTile ?: return@withContext
        val name = next?.let { "${it.goal.icon} ${it.goal.name}" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = "Tick next"
            tile.subtitle = name ?: "That's all for today"
        } else {
            tile.label = name ?: "That's all for today"
        }
        tile.state = if (next == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}

/** The next goal still to do today, in the order Today shows them. */
object NextGoal {
    suspend fun find(context: Context): HabitRow? {
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val now = nowLocal()
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val day = Days.dayOf(now, dayEnd)
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val goals = dao.activeGoals().map { it.toCore() }.filter { !(hidePrivate && it.private) }
        val since = Days.earliestOpenDay(day)
        val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
        val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
        val steps = dao.allSteps().map { it.toCore() }
        val view = TodayList.build(day, goals, steps, ticks, 0, lapses)
        // What still needs doing: not done, and not skipped.
        val open = view.dueToday.filter { !it.settled }
        if (open.isEmpty()) return null
        if (dao.setting(SETTING_TODAY_ORDER) == ORDER_OWN) return open.first()
        val settings = PaceSettings(
            dayEndHour = dayEnd,
            defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime(),
            dayStart = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime(),
        )
        val pace = Pace.today(now, day, view.dueToday, settings)
        return Order.recommend(now, day, view.dueToday, settings, emptyMap(), pace).firstOrNull { !it.row.settled }?.row ?: open.first()
    }
}
