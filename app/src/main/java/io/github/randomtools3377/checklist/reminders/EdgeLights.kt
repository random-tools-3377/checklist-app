package io.github.randomtools3377.checklist.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.R
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW_AWAY

/**
 * Lighting the edges of the screen from outside the app. A phone with edge lighting (as on
 * Samsung) lights its edges for a notification that pops up, so both of these are notifications
 * on their own types, which the user can change in the phone's settings:
 * reminders that come like a message, for "Light up the screen for reminders", and a short,
 * silent "Done" after a tick made on a widget or a reminder, which goes away by itself.
 * Neither needs a permission beyond showing notifications.
 */
object EdgeLights {
    const val REMINDER_CHANNEL = "reminders_light"
    const val CELEBRATION_CHANNEL = "celebrations"
    private const val CELEBRATION_ID = 940

    /** How long the "Done" notification stays, in milliseconds. */
    private const val GONE_AFTER = 4_000L

    /** The app's green, for phones with a notification light. */
    private const val LIGHT = 0xFF2E7D32.toInt()

    fun createChannels(context: Context) {
        val reminders = NotificationChannelCompat.Builder(REMINDER_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Reminders that light up the screen")
            .setDescription("Goals at their set times, shown like a message, with the light")
            .setLightsEnabled(true)
            .setLightColor(LIGHT)
            .setVibrationEnabled(true)
            .build()
        // Silent: it only pops up for a moment, which is what lights the edges.
        val celebrations = NotificationChannelCompat.Builder(CELEBRATION_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Celebrations")
            .setDescription("A short, silent note after a tick made away from the app, to light the edges of the screen")
            .setLightsEnabled(true)
            .setLightColor(LIGHT)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(listOf(reminders, celebrations))
    }

    /** A goal's reminder, made to come as a message would: popping up, at the top, with the light. */
    fun asMessage(builder: NotificationCompat.Builder): NotificationCompat.Builder = builder
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setLights(LIGHT, 500, 1500)

    /**
     * A tick was made on a widget or a reminder. With "Also light the edges when the app isn't on
     * screen" on, a short, silent "Done" pops up and goes by itself. A private goal behind the
     * lock isn't named.
     */
    suspend fun celebrate(context: Context, goalId: Long, result: TickResult) {
        if (result !is TickResult.Ticked || !Reminders.canNotify(context)) return
        val paid = result.points + result.bonus
        // One item of a routine isn't the routine done, and pays nothing: no celebration yet.
        if (!result.finished && paid == 0) return
        val container = (context.applicationContext as ChecklistApplication).container
        val dao = container.db.dao()
        if (dao.setting(SETTING_CELEBRATE_GLOW_AWAY) != "1") return
        val goal = container.repository.goal(goalId) ?: return
        val hidden = goal.private && dao.setting(LOCK_PRIVATE) == "1"
        val title = "Done: " + (if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}") + (if (paid > 0) ", +$paid" else "")
        createChannels(context)
        val open = PendingIntent.getActivity(
            context,
            CELEBRATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CELEBRATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(GONE_AFTER)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setLights(LIGHT, 500, 1500)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(CELEBRATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }
}
