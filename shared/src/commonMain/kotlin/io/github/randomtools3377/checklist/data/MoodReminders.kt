package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.MoodReminder
import io.github.randomtools3377.checklist.core.MoodReminderTime
import kotlinx.datetime.LocalDateTime

/**
 * The mood reminders due from [from] (included) to [to] (not included): one a day at the time
 * picked in Settings, while the mood check-in is on, and none for a day whose mood is already
 * given. None while it is off. Android and the computer ask for the minute that just came; the
 * iPhone asks for the days ahead when it hands its reminders over, and again on every change, so
 * today's goes once the mood is in. The reminder only asks: see [MoodReminder.TITLE].
 */
suspend fun moodReminders(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<MoodReminderTime> {
    val at = MoodReminder.decode(dao.setting(MoodReminder.SETTING)) ?: return emptyList()
    if (!Feature.MOOD.isOn(dao.setting(Feature.MOOD.key))) return emptyList()
    val times = MoodReminder.between(from, to, at, parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)))
    return times.filter { dao.mood(it.day.toEpochDay()) == null }
}
