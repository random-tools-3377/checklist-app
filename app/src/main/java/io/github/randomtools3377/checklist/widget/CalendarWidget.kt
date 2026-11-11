package io.github.randomtools3377.checklist.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.core.AgendaItem
import io.github.randomtools3377.checklist.core.CalendarView
import io.github.randomtools3377.checklist.core.DayCell
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.weekdayShort
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.ui.longDate
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** What the widget shows, read from the database each time it is drawn. */
data class WidgetData(val today: LocalDate, val agenda: List<AgendaItem>, val week: List<DayCell>, val month: List<DayCell>, val look: WidgetLook = WidgetLook())

/**
 * The home-screen calendar. Small, it shows today's agenda with times and tick boxes. Wider, it
 * adds this week. Taller, it adds the month.
 */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // Read again on every change while awake, so a tick made on the widget shows on it.
            val data by androidx.compose.runtime.remember { DayPlans.following(context) { load(context) } }.collectAsState(first)
            GlanceTheme { androidx.compose.runtime.CompositionLocalProvider(LocalWidgetLook provides data.look) { Content(data) } }
        }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val WIDE = DpSize(250.dp, 120.dp)
        private val TALL = DpSize(250.dp, 320.dp)

        /** Redraws every calendar widget on the home screen, if there are any. */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(CalendarWidget::class.java).isNotEmpty()) {
                CalendarWidget().updateAll(context)
            }
        }

        suspend fun load(context: Context): WidgetData {
            val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
            val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
            val today = Days.dayOf(nowLocal(), dayEnd)
            // Private goals stay off the home screen while the privacy lock covers them.
            val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
            val goals = dao.activeGoals().map { it.toCore() }.filter { !(hidePrivate && it.private) }
            val since = minOf(CalendarView.monthGridStart(today), Days.earliestOpenDay(today))
            val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
            return WidgetData(
                today = today,
                agenda = CalendarView.agenda(today, goals, ticks),
                week = CalendarView.week(today, goals, ticks),
                month = CalendarView.month(today, goals, ticks),
                look = widgetLook(dao),
            )
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

private val GoalKey = ActionParameters.Key<Long>("goal")

/** A tick box on the widget: ticks the goal, or takes the tick back if it was already done. */
class ToggleTick : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val goalId = parameters[GoalKey] ?: return
        val container = (context.applicationContext as ChecklistApplication).container
        val book = container.book
        val result = book.tickHabit(goalId)
        if (result == TickResult.AlreadyDone) {
            book.undoHabitTick(goalId)
            // A bet that tick had won is open again.
            container.bets.takeBackWins()
        }
        if (result is TickResult.Ticked && result.finished) io.github.randomtools3377.checklist.reminders.Reminders.afterDone(context, goalId)
        CalendarWidget().update(context, glanceId)
    }
}

@Composable
private fun Content(data: WidgetData) {
    val size = LocalSize.current
    val colors = widgetColors()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(10.dp),
    ) {
        val done = data.agenda.count { it.done }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                longDate(data.today),
                style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (data.agenda.isNotEmpty()) {
                Text("$done/${data.agenda.size}", style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            size.height >= 320.dp -> {
                MonthGrid(data.month)
                Spacer(GlanceModifier.height(6.dp))
            }
            size.width >= 250.dp -> {
                WeekStrip(data.week)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
        Agenda(data.agenda)
    }
}

@Composable
private fun Agenda(items: List<AgendaItem>) {
    val colors = widgetColors()
    if (items.isEmpty()) {
        Text("Nothing due today", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
        return
    }
    LazyColumn {
        items(items, itemId = { it.goal.id }) { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                // The goal's own colour, as a bar.
                item.goal.color?.let { color ->
                    Box(GlanceModifier.width(4.dp).height(22.dp).cornerRadius(2.dp).background(androidx.compose.ui.graphics.Color(color))) {}
                    Spacer(GlanceModifier.width(4.dp))
                }
                if (item.goal.target > 1 && !item.done) {
                    // Done several times a day: +1 adds one; the box would look ticked after the first.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = GlanceModifier
                            .width(40.dp).height(28.dp)
                            .cornerRadius(14.dp)
                            .background(colors.primaryContainer)
                            .clickable(actionRunCallback<ToggleTick>(actionParametersOf(GoalKey to item.goal.id))),
                    ) {
                        Text("+1", style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                } else {
                    CheckBox(
                        checked = item.done,
                        onCheckedChange = actionRunCallback<ToggleTick>(actionParametersOf(GoalKey to item.goal.id)),
                    )
                }
                Spacer(GlanceModifier.width(4.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        "${item.goal.icon} ${item.goal.name}",
                        maxLines = 1,
                        style = TextStyle(color = if (item.done) colors.onSurfaceVariant else colors.onSurface, fontSize = 14.sp),
                    )
                    val detail = buildList {
                        item.time?.let { add(timeText(it)) }
                        if (item.goal.target > 1) add("${item.count} of ${item.goal.target}")
                    }.joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(detail, style = TextStyle(color = colors.primary, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

/** A dot for a day: full when everything due was done, hollow when some is left, none when nothing was due. */
private fun dot(cell: DayCell): String = when {
    cell.due == 0 -> " "
    cell.done >= cell.due -> "●"
    else -> "○"
}

@Composable
private fun WeekStrip(week: List<DayCell>) {
    val colors = widgetColors()
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        week.forEach { cell ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier
                    .defaultWeight()
                    .then(if (cell.isToday) GlanceModifier.background(colors.primaryContainer).cornerRadius(8.dp) else GlanceModifier)
                    .padding(vertical = 3.dp),
            ) {
                Text(weekdayShort(cell.date.dayOfWeek.isoDayNumber).take(1), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp))
                Text("${cell.date.dayOfMonth}", style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(
                    if (cell.due == 0) " " else "${cell.done}/${cell.due}",
                    style = TextStyle(color = if (cell.due > 0 && cell.done >= cell.due) colors.primary else colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(month: List<DayCell>) {
    val colors = widgetColors()
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            (1..7).forEach { iso ->
                Text(
                    weekdayShort(iso).take(1),
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        month.chunked(7).forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .then(if (cell.isToday) GlanceModifier.background(colors.primaryContainer).cornerRadius(8.dp) else GlanceModifier),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${cell.date.dayOfMonth}",
                                style = TextStyle(
                                    color = if (cell.inMonth) colors.onSurface else colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            Text(dot(cell), style = TextStyle(color = colors.primary, fontSize = 8.sp, textAlign = TextAlign.Center))
                        }
                    }
                }
            }
        }
    }
}

/** The widgets' style and the app's colour, from Settings. */
suspend fun widgetLook(dao: io.github.randomtools3377.checklist.data.ChecklistDao): WidgetLook = WidgetLook(
    style = WidgetStyle.entries.firstOrNull { it.name == dao.setting(io.github.randomtools3377.checklist.data.SETTING_WIDGET_STYLE) } ?: WidgetStyle.SOLID,
    color = dao.setting(io.github.randomtools3377.checklist.data.SETTING_COLOR)?.let { io.github.randomtools3377.checklist.core.Colors.parseHex(it) },
    colourBlind = dao.setting(io.github.randomtools3377.checklist.core.ColourBlind.SETTING) == "1",
)
