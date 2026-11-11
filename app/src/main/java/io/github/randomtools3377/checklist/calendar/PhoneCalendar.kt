package io.github.randomtools3377.checklist.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Reads the phone's own calendar, only after the user turned it on in Settings and allowed it.
 * Nothing is written to it and nothing is sent anywhere: it is read on the phone, to show events
 * in the planner and on Today, and for pacing.
 */
object PhoneCalendar {
    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The calendars shown on the phone. */
    fun calendars(context: Context): List<PhoneCalendarInfo> {
        if (!allowed(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val found = mutableListOf<PhoneCalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, "${CalendarContract.Calendars.VISIBLE} = 1", null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    found += PhoneCalendarInfo(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty())
                }
            }
        }
        return found
    }

    /**
     * Events from [from] for [days] days, from the calendars in [ids] (all shown ones when empty),
     * in the phone's time zone.
     */
    fun events(context: Context, ids: Set<Long>, from: LocalDate, days: Int): List<Busy> {
        if (!allowed(context)) return emptyList()
        val zone = TimeZone.currentSystemDefault()
        val begin = LocalDateTime(from, LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
        val end = LocalDateTime(from.plusDays(days + 1), LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val found = mutableListOf<Busy>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (ids.isNotEmpty() && cursor.getLong(4) !in ids) continue
                    val allDay = cursor.getInt(3) == 1
                    // All-day events are kept in UTC by the calendar.
                    val eventZone = if (allDay) TimeZone.UTC else zone
                    val start = Instant.fromEpochMilliseconds(cursor.getLong(0)).toLocalDateTime(eventZone)
                    val stop = Instant.fromEpochMilliseconds(cursor.getLong(1)).toLocalDateTime(eventZone)
                    found += Busy(start, stop, cursor.getString(2)?.takeIf { it.isNotBlank() } ?: "Busy", allDay)
                }
            }
        }
        return found
    }
}
