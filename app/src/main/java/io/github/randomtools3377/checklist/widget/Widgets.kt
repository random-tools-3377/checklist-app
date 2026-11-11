package io.github.randomtools3377.checklist.widget

import android.content.Context

/** All the widgets that show goals and points. */
object Widgets {
    /** Redraws every widget on the home screen with fresh data. Each one is tried on its own. */
    suspend fun refreshAll(context: Context) {
        runCatching { TodayWidget.refresh(context) }
        runCatching { CalendarWidget.refresh(context) }
        runCatching { StatsWidget.refresh(context) }
        runCatching { BudgetWidget.refresh(context) }
        runCatching { QuickAddWidget.refresh(context) }
    }
}
