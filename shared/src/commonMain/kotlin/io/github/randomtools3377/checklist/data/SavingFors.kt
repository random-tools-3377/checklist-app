package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Boost
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.PointsName
import io.github.randomtools3377.checklist.core.RepeatPrice
import io.github.randomtools3377.checklist.core.SavingFor
import io.github.randomtools3377.checklist.core.Usual
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The reward the user starred as the thing they are saving for, read straight from the database
 * for what sits outside the app's own screens, like the morning plan. It reads what the shop
 * reads and asks the same [SavingFor] and [Usual], so the numbers are the shop's.
 */
object SavingFors {
    /** What the user usually earns a day, from their own history before [day] (see [Usual.pointsPerDay]). */
    suspend fun usualPerDay(dao: ChecklistDao, day: LocalDate): Double? = Usual.pointsPerDay(
        earnedByDay = dao.observeEarnedByDay(day.minusDays(Usual.WINDOW_DAYS).toEpochDay()).first().associate { it.day.toDay() to it.amount },
        firstDay = dao.observeFirstEarningDay().first()?.toDay(),
        today = day,
    )

    /**
     * The starred reward on [day], at what it costs now (repeat buys this week counted, as in
     * the shop). Null with no star, once the reward has left the shop, and while the shop is
     * behind the privacy lock: locked things stay out of reminders.
     */
    suspend fun load(dao: ChecklistDao, day: LocalDate, dayEndHour: Int): SavingFor? {
        if (dao.setting(LOCK_SHOP) == "1") return null
        val id = SavingFor.starred(dao.setting(SavingFor.SETTING)) ?: return null
        val reward = dao.activeRewards().firstOrNull { it.id == id }?.toCore() ?: return null
        val weekStart = LocalDateTime(Boost.weekOf(day), LocalTime(dayEndHour, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val times = dao.observePurchasesSince(weekStart).first().count { it.rewardId == id && it.returnedAt == null }
        val now = RepeatPrice.now(reward.price, times, Feature.REPEAT_PRICE.isOn(dao.setting(RepeatPrice.SETTING)))
        return SavingFor(reward.copy(price = now), dao.balance(), usualPerDay(dao, day))
    }

    /** The morning plan's one short line about it, in the user's own word for points; null when there is nothing to say. */
    suspend fun morningLine(dao: ChecklistDao, day: LocalDate, dayEndHour: Int): String? =
        load(dao, day, dayEndHour)?.morningLine(PointsName.decode(dao.setting(SETTING_POINTS_NAME)))
}
