package io.github.randomtools3377.checklist.tile

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.widget.DayPlans
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

/**
 * The next goal still to do today: the one Today and the Today widget show first, from the same
 * order (see DayOrder), so a tap on the tile ticks that one.
 */
object NextGoal {
    suspend fun find(context: Context): HabitRow? = DayPlans.today(context).order.next?.row
}
