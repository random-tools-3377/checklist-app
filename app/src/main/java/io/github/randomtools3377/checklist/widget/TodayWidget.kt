package io.github.randomtools3377.checklist.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.CalendarView
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Planned
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.TodayPace
import io.github.randomtools3377.checklist.core.TodoRow
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.reminders.Reminders
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.datetime.LocalDateTime

/** One goal on the timeline, ready to draw. */
data class TimelineRow(
    val goalId: Long,
    val title: String,
    val color: Int?,
    /** "9:00 am" for a set time, "~2:30 pm" for when to get to it, null when there's no telling. */
    val time: String?,
    val late: Boolean,
    /** "1 of 2", "2 of 4 · next: Stretch", "20 pages left", "Skipped". */
    val detail: String?,
    /** What its button says: "Done", "+1", "✓ Stretch", "Avoided". */
    val button: String,
    /** A routine's next item, which its button ticks. */
    val stepId: Long?,
    /** A routine, or a to-do with steps: taking it back takes back its items too. */
    val routine: Boolean,
    /** Done by a skip, so there's no tick to take back. */
    val skipped: Boolean = false,
)

/** What the timeline widget shows. [rows] are still to do, in order; [done] are finished or skipped. */
data class TimelineData(
    val rows: List<TimelineRow>,
    val done: List<TimelineRow>,
    val pace: String?,
    /** Tomorrow's goals, shown once today is done, as "8:00 am 💧 Water". */
    val tomorrow: List<String>,
    val look: WidgetLook = WidgetLook(),
) {
    val total: Int get() = rows.size + done.size
}

/**
 * Today as a timeline on the home screen, to keep going without opening the app: what's up next
 * with a big button, then the rest of the day in the order Today shows it, each with its own
 * button. Done ones fold to the bottom; once everything is done, it shows tomorrow. Small, it
 * shows only what's up next.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // Read again on every change while awake, so each tick shows straight away.
            val data by remember { DayPlans.following(context) { load(context) } }.collectAsState(first)
            GlanceTheme { androidx.compose.runtime.CompositionLocalProvider(LocalWidgetLook provides data.look) { Timeline(data) } }
        }
    }

    companion object {
        private val SMALL = DpSize(150.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 260.dp)

        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).isNotEmpty()) {
                TodayWidget().updateAll(context)
            }
        }

        suspend fun load(context: Context): TimelineData {
            val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
            val plan = DayPlans.load(context)
            val open = plan.planned.filter { !it.row.done }
            val rows = open.map { row(it.row, it) } + plan.todos.map { todo(it) }
            // To-dos done today count as done, so the total stays the same.
            val done = plan.planned.filter { it.row.done }.map { row(it.row, null) } + plan.doneTodos.map { todo(it) }
            val tomorrow = if (rows.isEmpty()) {
                CalendarView.agenda(plan.day.plusDays(1), plan.goals, plan.ticks).take(5).map { item ->
                    (item.time?.let { timeText(it) + "  " } ?: "") + "${item.goal.icon} ${item.goal.name}"
                }
            } else {
                emptyList()
            }
            return TimelineData(rows, done, paceLine(plan.pace, plan.now, rows.isEmpty()), tomorrow, widgetLook(dao))
        }

        /** A goal as a line on the timeline. */
        fun row(row: HabitRow, planned: Planned?): TimelineRow {
            val goal = row.goal
            val nextItem = row.items.firstOrNull { !it.done }
            val detail = when {
                row.done && row.skipped -> "Skipped"
                row.done -> if (goal.target > 1) "${goal.target} of ${goal.target}" else null
                row.isRoutine -> "${row.items.count { it.done }} of ${row.items.size}" + (nextItem?.let { " · next: ${it.step.name}" } ?: "")
                goal.hasAmount -> "${Amounts.format(row.amountLeft)}${goal.unit?.let { " $it" } ?: ""} left"
                goal.target > 1 -> "${row.count} of ${goal.target}"
                else -> null
            }
            val button = when {
                row.isRoutine && nextItem != null -> "✓ ${nextItem.step.name.take(14)}"
                goal.quit -> "Avoided"
                goal.target > 1 && !goal.hasAmount -> "+1"
                else -> "Done"
            }
            return TimelineRow(
                goalId = goal.id,
                title = "${goal.icon} ${goal.name}",
                color = goal.color,
                time = planned?.time?.let { (if (planned.fixed) "" else "~") + timeText(it.time) },
                late = planned?.late == true,
                detail = detail,
                button = button,
                stepId = nextItem?.step?.id.takeIf { row.isRoutine && !row.done },
                routine = row.isRoutine,
                skipped = row.done && row.skipped,
            )
        }

        /** A to-do still open today, after the goals: its button ticks it, or its next step. Or one done today. */
        fun todo(row: TodoRow): TimelineRow {
            val next = row.items.firstOrNull { !it.done }
            return TimelineRow(
                goalId = row.goal.id,
                title = "${row.goal.icon} ${row.goal.name}",
                color = row.goal.color,
                time = null,
                late = false,
                detail = if (row.items.isEmpty() || row.done) "To-do" else "${row.items.count { it.done }} of ${row.items.size} · next: ${next?.step?.name}",
                button = next?.let { "✓ ${it.step.name.take(14)}" } ?: "Done",
                stepId = next?.step?.id,
                routine = row.items.isNotEmpty(),
            )
        }

        /** "Finish by 9:00 pm · about 20 min each", or how far behind; null with nothing to pace. */
        fun paceLine(pace: TodayPace?, now: LocalDateTime, allDone: Boolean): String? {
            if (pace == null || allDone) return null
            return buildList {
                add("Finish by ${timeText(pace.finishAt.time)}")
                pace.minutesEach?.let { add("about $it min each") }
                if (pace.shortBy > 0) add("${pace.shortBy} min short")
            }.joinToString(" · ")
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val TimelineGoal = ActionParameters.Key<Long>("goal")
private val TimelineStep = ActionParameters.Key<Long>("step")
private val TimelineRoutine = ActionParameters.Key<Boolean>("routine")

/** A goal's button: ticks it once, or a routine's next item. */
class TimelineTick : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val goalId = parameters[TimelineGoal] ?: return
        val stepId = parameters[TimelineStep]?.takeIf { it > 0 }
        val book = (context.applicationContext as ChecklistApplication).container.book
        val result = if (stepId != null) book.checkItem(stepId) else book.tickHabit(goalId)
        if (result is TickResult.Ticked && result.finished) Reminders.afterDone(context, goalId)
        TodayWidget().update(context, glanceId)
    }
}

/** Tapping a done goal takes its last tick back (a routine starts again). */
class TimelineUndo : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val goalId = parameters[TimelineGoal] ?: return
        val container = (context.applicationContext as ChecklistApplication).container
        val book = container.book
        if (parameters[TimelineRoutine] == true) book.undoRoutine(goalId) else book.undoHabitTick(goalId)
        TodayWidget().update(context, glanceId)
    }
}

/** The "Done" heading's place in the list: below any goal's, and above the ids Glance keeps for itself. */
private const val DONE_HEADER = -(1L shl 40)

private fun tick(row: TimelineRow): Action =
    actionRunCallback<TimelineTick>(actionParametersOf(TimelineGoal to row.goalId, TimelineStep to (row.stepId ?: -1L)))

private fun undo(row: TimelineRow): Action =
    actionRunCallback<TimelineUndo>(actionParametersOf(TimelineGoal to row.goalId, TimelineRoutine to row.routine))

@Composable
private fun Timeline(data: TimelineData) {
    val colors = widgetColors()
    val size = LocalSize.current
    val roomy = size.height >= 200.dp
    Column(
        modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(16.dp).padding(10.dp),
    ) {
        // How far along today is; tapping it opens the app.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())) {
            Text("Today", style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            if (data.total > 0) {
                Text(
                    if (data.rows.isEmpty()) "All ${data.total} done" else "${data.done.size} of ${data.total} · ${data.rows.size} to go",
                    style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        if (data.total > 0) {
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = data.done.size.toFloat() / data.total,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = colors.primary,
                backgroundColor = colors.secondaryContainer,
            )
        }
        if (roomy) data.pace?.let { Text(it, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp)) }
        Spacer(GlanceModifier.height(6.dp))

        val next = data.rows.firstOrNull()
        if (next == null) {
            Text(
                if (data.total == 0) "Nothing due today" else "🎉 All done for today!",
                style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            if (data.tomorrow.isNotEmpty() && size.height >= 150.dp) {
                Spacer(GlanceModifier.height(6.dp))
                Text("Tomorrow", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                data.tomorrow.forEach { line -> Text(line, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 13.sp)) }
            }
            return@Column
        }
        UpNext(next)
        if (!roomy) {
            if (data.rows.size > 1) {
                Spacer(GlanceModifier.height(4.dp))
                Text("Then: ${data.rows[1].title}", maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
            }
            return@Column
        }
        Spacer(GlanceModifier.height(6.dp))
        LazyColumn {
            items(data.rows.drop(1), itemId = { it.goalId }) { row -> Line(row) }
            if (data.done.isNotEmpty()) {
                item(itemId = DONE_HEADER) {
                    Text(
                        "Done · tap to take back",
                        modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
                items(data.done, itemId = { -it.goalId - 1 }) { row -> DoneLine(row) }
            }
        }
    }
}

/** What's up next, big, with its button. */
@Composable
private fun UpNext(row: TimelineRow) {
    val colors = widgetColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().background(colors.primaryContainer).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                "Up next" + (row.time?.let { " · $it" } ?: "") + if (row.late) " · late" else "",
                maxLines = 1,
                style = TextStyle(color = if (row.late) colors.error else colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Text(row.title, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            row.detail?.let { Text(it, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp)) }
        }
        Spacer(GlanceModifier.width(8.dp))
        Pill(row.button, tick(row), big = true)
    }
}

/** A goal later in the day: its time, name and button. */
@Composable
private fun Line(row: TimelineRow) {
    val colors = widgetColors()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        row.color?.let { color ->
            Box(GlanceModifier.width(3.dp).height(26.dp).cornerRadius(2.dp).background(androidx.compose.ui.graphics.Color(color))) {}
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(
            row.time ?: "",
            maxLines = 1,
            style = TextStyle(color = if (row.late) colors.error else colors.onSurfaceVariant, fontSize = 11.sp),
            modifier = GlanceModifier.width(58.dp),
        )
        Column(GlanceModifier.defaultWeight()) {
            Text(row.title, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 14.sp))
            row.detail?.let { Text(it, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp)) }
        }
        Spacer(GlanceModifier.width(6.dp))
        Pill(row.button, tick(row), big = false)
    }
}

/** A finished goal, crossed off; tapping it takes it back. A skipped one has no tick to take back. */
@Composable
private fun DoneLine(row: TimelineRow) {
    val colors = widgetColors()
    val line = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = if (row.skipped) line else line.clickable(undo(row))) {
        Text("✓", style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(18.dp))
        Text(
            row.title + (row.detail?.let { " · $it" } ?: ""),
            maxLines = 1,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough),
        )
    }
}

/** A rounded button. */
@Composable
private fun Pill(label: String, onClick: Action, big: Boolean) {
    val colors = widgetColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .height(if (big) 40.dp else 30.dp)
            .cornerRadius(if (big) 20.dp else 15.dp)
            .background(if (big) colors.primary else colors.primaryContainer)
            .padding(horizontal = if (big) 16.dp else 12.dp)
            .clickable(onClick),
    ) {
        Text(
            label,
            maxLines = 1,
            style = TextStyle(color = if (big) colors.onPrimary else colors.primary, fontSize = if (big) 15.sp else 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}
