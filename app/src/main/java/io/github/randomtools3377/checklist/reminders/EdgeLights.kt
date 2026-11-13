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
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW_AWAY
import io.github.randomtools3377.checklist.data.SETTING_TICK_ANSWER
import io.github.randomtools3377.checklist.data.tickAnswer
import io.github.randomtools3377.checklist.data.tickAnswerOn

/**
 * Lighting the edges of the screen from outside the app. A phone with edge lighting (as on
 * Samsung) lights its edges for a notification that pops up, so both of these are notifications
 * on their own types, which the user can change in the phone's settings:
 * reminders that come like a message, for "Light up the screen for reminders", and a short,
 * silent "Done" after a tick made on a widget or a reminder, which goes away by itself. That
 * same note is the answer to a tick made outside the app ("Confirm a tick made outside the app"),
 * which adds what is left today.
 * Neither needs a permission beyond showing notifications.
 */
object EdgeLights {
    const val REMINDER_CHANNEL = "reminders_light"
    const val CELEBRATION_CHANNEL = "celebrations"
    private const val CELEBRATION_ID = 940

    /** How long the "Done" notification stays, in milliseconds: long enough to read what is left. */
    private const val GONE_AFTER = 5_000L

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
            .setName("Tick confirmations")
            .setDescription("A short, silent note after a tick made on a reminder or a widget: what was ticked and what is left today")
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
     * A tick was made on a widget or a reminder. With "Confirm a tick made outside the app" on, a
     * short, silent note answers back and goes by itself: "Done: 💧 Water, +5" and "2 left today"
     * or "That's everything for today". With only "Also light the edges when the app isn't on
     * screen" on, it is just the "Done" that lights the edges. A private goal behind the lock
     * isn't named, and says nothing more.
     */
    suspend fun celebrate(context: Context, goalId: Long, result: TickResult) {
        if (result !is TickResult.Ticked || !Reminders.canNotify(context)) return
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val answers = tickAnswerOn(dao.setting(SETTING_TICK_ANSWER))
        if (!answers && dao.setting(SETTING_CELEBRATE_GLOW_AWAY) != "1") return
        // Nothing for one item of a routine: it isn't the routine done, and pays nothing.
        val answer = tickAnswer(dao, goalId, result, stakes = answers) ?: return
        createChannels(context)
        val open = PendingIntent.getActivity(
            context,
            CELEBRATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CELEBRATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(answer.title)
            .setContentText(answer.text)
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
