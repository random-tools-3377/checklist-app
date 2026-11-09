package io.github.randomtools3377.checklist.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.glance.text.TextStyle
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.StreakCounter
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.WeekProgress
import io.github.randomtools3377.checklist.core.WeeklyTarget
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_STREAK_TARGET
import io.github.randomtools3377.checklist.data.SETTING_STREAK_VALUE
import io.github.randomtools3377.checklist.data.SETTING_WEEKLY_TARGET
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.ui.pointsText

/** What the balance and streaks widget shows, read from the database each time it is drawn. */
data class StatsData(val balance: Int, val streaks: Streaks, val due: Int, val done: Int, val week: WeekProgress?, val look: WidgetLook = WidgetLook())

/**
 * The balance and streaks widget. Small, it shows the balance, the main streak and how much of
 * today is done. Wider, it adds every streak.
 */
class StatsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { androidx.compose.runtime.CompositionLocalProvider(LocalWidgetLook provides data.look) { StatsContent(data) } } }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val WIDE = DpSize(250.dp, 120.dp)

        /** How far back streaks are counted, as on Today. */
        private const val STREAK_DAYS = 400

        /** Redraws every balance and streaks widget on the home screen, if there are any. */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(StatsWidget::class.java).isNotEmpty()) {
                StatsWidget().updateAll(context)
            }
        }

        suspend fun load(context: Context): StatsData {
            val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
            val today = Days.dayOf(nowLocal(), parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)))
            // Private goals stay off the home screen while the privacy lock covers them, counts included.
            val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
            val goals = dao.activeGoals().map { it.toCore() }.filter { !(hidePrivate && it.private) }
            val since = minOf(today.minusDays(STREAK_DAYS), Days.earliestOpenDay(today))
            val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
            val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
            val defaults = StreakSettings()
            val streak = StreakSettings(
                target = dao.setting(SETTING_STREAK_TARGET)?.let { runCatching { StreakTarget.valueOf(it) }.getOrNull() } ?: defaults.target,
                value = dao.setting(SETTING_STREAK_VALUE)?.toIntOrNull() ?: defaults.value,
            )
            val records = StreakCounter.records(since, today, goals, ticks, lapses)
            val todayRecord = records.lastOrNull()
            val target = dao.setting(SETTING_WEEKLY_TARGET)?.toIntOrNull() ?: 0
            val week = if (target <= 0) null else {
                val monday = Days.periodStart(today, Period.WEEKLY)
                WeeklyTarget.progress(dao.entries(monday.toEpochDay(), today.toEpochDay()).map { it.toCore() }, today, target)
            }
            return StatsData(
                balance = dao.balance(),
                streaks = StreakCounter.count(records, today, streak),
                due = todayRecord?.due ?: 0,
                done = todayRecord?.done ?: 0,
                week = week,
                look = widgetLook(dao),
            )
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

@Composable
private fun StatsContent(data: StatsData) {
    val colors = widgetColors()
    val wide = LocalSize.current.width >= 250.dp
    Row(
        verticalAlignment = Alignment.Top,
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text("Balance", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
            Text(
                pointsText(data.balance),
                maxLines = 1,
                style = TextStyle(color = if (data.balance < 0) colors.error else colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (data.streaks.done > 0) "🔥 ${days(data.streaks.done)} in a row" else "No streak yet",
                maxLines = 1,
                style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (data.due > 0) {
                LinearProgressIndicator(
                    progress = (data.done.toFloat() / data.due).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = colors.primary,
                    backgroundColor = colors.secondaryContainer,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text("${data.done} of ${data.due} done today", maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
            } else {
                Text("Nothing due today", maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
            }
            data.week?.let { week ->
                Text(
                    if (week.reached) "🎯 Week's target reached" else "🎯 ${week.earned} of ${week.target} this week",
                    maxLines = 1,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
        if (wide) {
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    if (data.streaks.allRunning) "Streaks · all running!" else "Streaks",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                )
                StreakLine("Done", data.streaks.done)
                StreakLine("No misses", data.streaks.noMisses)
                StreakLine("No skips", data.streaks.noSkips)
                StreakLine("In the limit", data.streaks.withinLimit)
                StreakLine("Perfect days", data.streaks.perfect)
            }
        }
    }
}

@Composable
private fun StreakLine(label: String, days: Int) {
    val colors = widgetColors()
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
        Text("$days", style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}
