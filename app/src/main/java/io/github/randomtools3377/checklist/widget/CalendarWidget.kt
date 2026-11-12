package io.github.randomtools3377.checklist.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import io.github.randomtools3377.checklist.calendar.PhoneCalendar
import io.github.randomtools3377.checklist.core.AgendaDay
import io.github.randomtools3377.checklist.core.AgendaLine
import io.github.randomtools3377.checklist.core.CalendarView
import io.github.randomtools3377.checklist.core.DayCell
import io.github.randomtools3377.checklist.core.DayTone
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.LineKind
import io.github.randomtools3377.checklist.core.LineState
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.weekdayShort
import io.github.randomtools3377.checklist.data.CalendarBoard
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.calendarBoard
import io.github.randomtools3377.checklist.data.calendarChoice
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.ui.OPEN_NEW_GOAL
import io.github.randomtools3377.checklist.ui.OPEN_NEW_TODO
import io.github.randomtools3377.checklist.ui.longDate
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * What the widget shows, read from the database each time it is drawn: the agenda of the next
 * days (today first) and the month, worked out in the shared code (see [calendarBoard]).
 */
data class WidgetData(val board: CalendarBoard, val look: WidgetLook = WidgetLook()) {
    val today: LocalDate get() = board.today
    val days: List<AgendaDay> get() = board.days
    val month: List<DayCell> get() = board.month
}

/**
 * The home-screen calendar, like a calendar app's own. Small, it shows today's agenda with times
 * and tick boxes. Wider, the agenda of the next days under day headers, with the phone's calendar
 * events (when that is turned on and allowed), project deadlines and dated to-dos in the same
 * list. Taller, the month, each day with how much was due and done, and today's agenda below.
 * It keeps nothing of its own between redraws, so the month is always the current one.
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
            val now = nowLocal()
            val today = Days.dayOf(now, parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)))
            // The phone's calendar, only while it is turned on in Settings and still allowed.
            val (calendarOn, calendars) = calendarChoice(dao)
            val events = if (calendarOn && PhoneCalendar.allowed(context)) {
                withContext(Dispatchers.IO) {
                    runCatching { PhoneCalendar.events(context, calendars, today, CalendarView.AGENDA_DAYS) }.getOrDefault(emptyList())
                }
            } else {
                emptyList()
            }
            return WidgetData(calendarBoard(dao, now, events), widgetLook(dao))
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
        }
        if (result is TickResult.Ticked && result.finished) io.github.randomtools3377.checklist.reminders.Reminders.afterDone(context, goalId)
        runCatching { io.github.randomtools3377.checklist.reminders.EdgeLights.celebrate(context, goalId, result) }
        CalendarWidget().update(context, glanceId)
    }
}

/** Opens the app, on the screen [open] names (see MainActivity.EXTRA_OPEN), or where it was with null. */
@Composable
private fun openApp(open: String?): Intent {
    val intent = Intent(LocalContext.current, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (open != null) intent.putExtra(MainActivity.EXTRA_OPEN, open)
    return intent
}

@Composable
private fun Content(data: WidgetData) {
    val size = LocalSize.current
    val colors = widgetColors()
    val today = data.days.firstOrNull { it.date == data.today }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                longDate(data.today),
                maxLines = 1,
                style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(null))),
            )
            if (today != null && today.due > 0) {
                Text(
                    "${today.done}/${today.due}",
                    style = TextStyle(color = if (today.done >= today.due) colors.good else colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            // "+" adds a to-do while To-dos on Today is turned on, and a goal otherwise.
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .width(28.dp).height(28.dp)
                    .cornerRadius(14.dp)
                    .background(colors.primaryContainer)
                    .clickable(actionStartActivity(openApp(if (data.board.addsTodo) OPEN_NEW_TODO else OPEN_NEW_GOAL))),
            ) {
                Text("+", style = TextStyle(color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            size.height >= 320.dp -> {
                MonthGrid(data.month, data.look.colourBlind)
                Spacer(GlanceModifier.height(6.dp))
                Agenda(listOfNotNull(today), headers = false)
            }
            size.width >= 250.dp -> Agenda(data.days, headers = true)
            else -> Agenda(listOfNotNull(today), headers = false)
        }
    }
}

/** One row of the agenda's list: a day's header, one of its lines, or "Nothing due today". */
private data class AgendaRow(val header: String? = null, val line: AgendaLine? = null)

/** The agenda as one list. With [headers], each day comes under its own, like "Tomorrow" or "Fri 14". */
private fun agendaRows(days: List<AgendaDay>, headers: Boolean): List<AgendaRow> = days.flatMap { day ->
    val lines = if (day.lines.isEmpty()) listOf(AgendaRow()) else day.lines.map { AgendaRow(line = it) }
    if (headers) listOf(AgendaRow(header = day.header)) + lines else lines
}

/** When a line is: "9:00 am", an event's "10:00 am – 11:00 am", or nothing. */
private fun whenText(line: AgendaLine): String? {
    val from = line.time?.let { timeText(it) } ?: return null
    return line.until?.let { "$from – ${timeText(it)}" } ?: from
}

/** What stands where the tick box would be, for a line that can't be ticked here. */
private fun mark(line: AgendaLine): String = when {
    line.state == LineState.DONE -> "✓"
    line.state == LineState.SKIPPED -> "–"
    line.kind == LineKind.DEADLINE -> "🚩"
    else -> "·"
}

@Composable
private fun Agenda(days: List<AgendaDay>, headers: Boolean) {
    val colors = widgetColors()
    val rows = agendaRows(days, headers)
    if (rows.isEmpty()) {
        Text("Nothing due today", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
        return
    }
    LazyColumn {
        items(rows) { row ->
            val line = row.line
            when {
                row.header != null -> Text(
                    row.header,
                    style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                )
                line == null -> Text(
                    "Nothing due today",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                )
                else -> AgendaLineRow(line)
            }
        }
    }
}

@Composable
private fun AgendaLineRow(line: AgendaLine) {
    val colors = widgetColors()
    val goalId = line.goalId
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // The goal's own colour, as a bar.
        line.color?.let { color ->
            Box(GlanceModifier.width(4.dp).height(22.dp).cornerRadius(2.dp).background(androidx.compose.ui.graphics.Color(color))) {}
            Spacer(GlanceModifier.width(4.dp))
        }
        if (line.canTick && goalId != null && line.plusOne) {
            // Done several times: +1 adds one; the box would look ticked after the first.
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .width(40.dp).height(28.dp)
                    .cornerRadius(14.dp)
                    .background(colors.primaryContainer)
                    .clickable(actionRunCallback<ToggleTick>(actionParametersOf(GoalKey to goalId))),
            ) {
                Text("+1", style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        } else if (line.canTick && goalId != null) {
            CheckBox(
                checked = line.state == LineState.DONE,
                onCheckedChange = actionRunCallback<ToggleTick>(actionParametersOf(GoalKey to goalId)),
            )
        } else {
            // Nothing to tick here: another day's, an event, a deadline, a skipped or a private goal.
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.width(28.dp).height(22.dp)) {
                Text(
                    mark(line),
                    style = TextStyle(
                        color = if (line.state == LineState.DONE) colors.good else colors.onSurfaceVariant,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        Spacer(GlanceModifier.width(4.dp))
        Column(GlanceModifier.defaultWeight()) {
            val settled = line.state == LineState.DONE || line.state == LineState.SKIPPED
            Text(
                line.title,
                maxLines = 1,
                style = TextStyle(color = if (settled) colors.onSurfaceVariant else colors.onSurface, fontSize = 14.sp),
            )
            // Its time, how far along it is, and how it stands: late in red, and in words too.
            val detail = listOfNotNull(whenText(line), line.detail, line.stateWord).joinToString(" · ")
            if (detail.isNotEmpty()) {
                val color = when (line.state) {
                    LineState.LATE -> colors.error
                    LineState.DONE -> colors.good
                    LineState.SKIPPED -> colors.onSurfaceVariant
                    LineState.OPEN -> colors.primary
                }
                Text(detail, maxLines = 1, style = TextStyle(color = color, fontSize = 12.sp))
            }
        }
    }
}

/**
 * The month: each day with its number (and 🚩 when a project is due), and under it how much was
 * due and done, in the good colour once everything is done and the bad one for a past day with
 * something left. With [marks], the colour-blind friendly look, ✓, ◐ and ✕ say it too.
 */
@Composable
private fun MonthGrid(month: List<DayCell>, marks: Boolean) {
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
        // A last week that is all next month's is left out.
        month.chunked(7).filter { week -> week.any { it.inMonth } }.forEach { week ->
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
                                "${cell.date.dayOfMonth}" + if (cell.deadlines > 0) "🚩" else "",
                                maxLines = 1,
                                style = TextStyle(
                                    color = if (cell.inMonth) colors.onSurface else colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            Text(
                                cell.label(marks).ifEmpty { " " },
                                maxLines = 1,
                                style = TextStyle(
                                    color = when (cell.tone) {
                                        DayTone.GOOD -> colors.good
                                        DayTone.BAD -> colors.error
                                        DayTone.PLAIN -> colors.onSurfaceVariant
                                    },
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                ),
                            )
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
