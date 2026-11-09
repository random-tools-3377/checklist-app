package io.github.randomtools3377.checklist.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.R

/**
 * While a goal with an amount is being timed with Start and Stop, a quiet notification shows the
 * time running, with Stop. Stop opens the app to say how much was done.
 */
object TimingNotice {
    private const val CHANNEL = "timing"
    private const val ID = 902

    private fun channel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Timing")
                .setDescription("Shows while a goal is being timed with Start and Stop")
                .build(),
        )
    }

    /** Shows the timing of [title] (like "📖 Read") since [startedAt], in epoch milliseconds. */
    fun show(context: Context, goalId: Long, title: String, startedAt: Long) {
        if (!Reminders.canNotify(context)) return
        channel(context)
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN, "today"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getActivity(
            context,
            ID + 1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_STOP_TIMING, goalId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Timing. Tap Stop when you're done.")
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }
}
