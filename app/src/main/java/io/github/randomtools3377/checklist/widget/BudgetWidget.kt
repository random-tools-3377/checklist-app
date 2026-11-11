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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.DayPay
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.MoneyMonth
import io.github.randomtools3377.checklist.core.Pay
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.data.LOCK_BUDGET
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.ui.billsComingUp
import io.github.randomtools3377.checklist.ui.centsText
import io.github.randomtools3377.checklist.ui.minutesText
import io.github.randomtools3377.checklist.ui.moneyText
import io.github.randomtools3377.checklist.ui.shortDate
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** What the budget widget shows: why it can't, or today's pay, the month, today's spending and the next bill. */
sealed interface BudgetWidgetData {
    val look: WidgetLook

    data class Off(override val look: WidgetLook, val why: String) : BudgetWidgetData

    data class Shown(
        override val look: WidgetLook,
        val now: LocalDateTime,
        val today: DayPay,
        val month: MoneyMonth,
        val spentTodayCents: Long,
        val nextBill: String?,
    ) : BudgetWidgetData
}

/**
 * The budget widget. Small, it shows today's pay filling up as the work day goes on. Wider, it
 * adds the month's pay, what's been spent today, what's left and the next bill. It redraws when
 * money, pay or work days change, and every half hour; it says when it was last drawn.
 */
class BudgetWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            val data by androidx.compose.runtime.remember { DayPlans.following(context) { load(context) } }.collectAsState(first)
            GlanceTheme { androidx.compose.runtime.CompositionLocalProvider(LocalWidgetLook provides data.look) { BudgetContent(data) } }
        }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)

        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(BudgetWidget::class.java).isNotEmpty()) {
                BudgetWidget().updateAll(context)
            }
        }

        suspend fun load(context: Context): BudgetWidgetData {
            val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
            val look = widgetLook(dao)
            if (!Feature.BUDGET.isOn(dao.setting(Feature.BUDGET.key))) return BudgetWidgetData.Off(look, "Turn on Budget in Settings → Features.")
            // The budget stays off the home screen while the privacy lock covers it.
            if (dao.setting(LOCK_BUDGET) == "1") return BudgetWidgetData.Off(look, "🔒 The budget is locked.")
            val plan = PayPlan.decode(dao.setting(PayPlan.SETTING))?.takeIf { it.complete }
                ?: return BudgetWidgetData.Off(look, "Set up your pay in the Budget tab.")
            val now = nowLocal()
            val changes = dao.allWorkDays().map { it.toCore() }.associateBy { it.day }
            val first = Budget.monthOf(now.date)
            val money = dao.allMoney().map { it.toCore() }.filter { it.day >= first }
            val month = Budget.month(first, plan, changes, money, dao.moneyCategories().map { it.toCore() }, now)
            val spentToday = money.filter { it.day == now.date && it.kind == io.github.randomtools3377.checklist.core.MoneyKind.SPENT }.sumOf { it.cents }
            val next = billsComingUp(dao.allBills().map { it.toCore() }, now.date).firstOrNull()?.let { (bill, day) ->
                "${bill.name} · ${if (day == now.date) "today" else shortDate(day)} · ${centsText(bill.cents)}"
            }
            return BudgetWidgetData.Shown(look, now, Pay.day(plan, now.date, changes[now.date]), month, spentToday, next)
        }
    }
}

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}

private fun clock(minute: Int) = timeText(LocalTime((minute / 60) % 24, minute % 60))

@Composable
private fun BudgetContent(data: BudgetWidgetData) {
    val colors = widgetColors()
    val wide = LocalSize.current.width >= 250.dp
    val open = Intent(androidx.glance.LocalContext.current, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN, "budget")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(16.dp).padding(12.dp).clickable(actionStartActivity(open)),
    ) {
        when (data) {
            is BudgetWidgetData.Off -> Column(GlanceModifier.defaultWeight()) {
                Text("💼 Budget", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text(data.why, style = TextStyle(color = colors.onSurface, fontSize = 13.sp))
            }
            is BudgetWidgetData.Shown -> {
                Column(GlanceModifier.defaultWeight()) {
                    val pay = data.today
                    val hours = pay.hours
                    Text("💼 Today", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                    when {
                        pay.paidOff -> Text("Day off, paid", maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        hours == null -> Text("A day off", maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        else -> {
                            val done = pay.done(data.now)
                            Text(
                                moneyText(pay.earned(data.now), alwaysCents = true),
                                maxLines = 1,
                                style = TextStyle(color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            )
                            Text("of ${moneyText(pay.total, alwaysCents = true)}", maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                            Spacer(GlanceModifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = done.toFloat(),
                                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                                color = colors.primary,
                                backgroundColor = colors.secondaryContainer,
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                when {
                                    done <= 0.0 -> "Starts at ${clock(hours.start)}"
                                    done >= 1.0 -> "Done for the day"
                                    else -> "${minutesText(pay.minutesLeft(data.now))} to go"
                                },
                                maxLines = 1,
                                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                            )
                        }
                    }
                    Text("As of ${timeText(data.now.time)}", maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp))
                }
                if (wide) {
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("This month", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                        data.month.pay?.let { pay ->
                            Text(
                                "${moneyText(pay.earned)} of ${moneyText(pay.total)}",
                                maxLines = 1,
                                style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            )
                            LinearProgressIndicator(
                                progress = if (pay.total > 0) (pay.earned / pay.total).toFloat().coerceIn(0f, 1f) else 0f,
                                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                                color = colors.primary,
                                backgroundColor = colors.secondaryContainer,
                            )
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        Line("Spent today", centsText(data.spentTodayCents))
                        Line("Left now", moneyText(data.month.leftNow))
                        data.nextBill?.let {
                            Text("🧾 $it", maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val colors = widgetColors()
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
        Text(value, maxLines = 1, style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}
