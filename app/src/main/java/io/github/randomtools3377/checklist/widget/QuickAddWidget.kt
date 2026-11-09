package io.github.randomtools3377.checklist.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.ui.OPEN_QUICK_TODO

/** A small widget that adds a to-do in one tap: it opens the app with a box to type it in. */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = widgetLook((context.applicationContext as ChecklistApplication).container.db.dao())
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, OPEN_QUICK_TODO)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        provideContent {
            GlanceTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalWidgetLook provides look) {
                    val colors = widgetColors()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(20.dp)
                            .background(colors.widgetBackground)
                            .clickable(actionStartActivity(open)),
                    ) {
                        Text("＋", style = TextStyle(color = colors.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                        Text("To-do", style = TextStyle(color = colors.onSurface, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
