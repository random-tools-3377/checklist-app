package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.DeadlineReminder
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.ui.moneyText
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** The bills and regular money in that remind, while Budget is on. */
private suspend fun remindingBills(dao: ChecklistDao): List<Bill> {
    if (!Feature.BUDGET.isOn(dao.setting(Feature.BUDGET.key))) return emptyList()
    return dao.allBills().map { it.toCore() }.filter { !it.archived && it.remindDays != null }
}

/** When bill reminders come: when the user's day starts, or at 9 in the morning until that is set. */
private suspend fun billTime(dao: ChecklistDao): LocalTime = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime() ?: Alerts.BILL_TIME

/**
 * When bill reminders come from [from] (included) to [to] (not included): the chosen number of
 * days before each bill, or regular money in, falls due.
 */
suspend fun billTimes(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<Alert> {
    val bills = remindingBills(dao)
    return if (bills.isEmpty()) emptyList() else Alerts.bills(bills, from, to, billTime(dao))
}

/**
 * The bill reminders due from [from] (included) to [to] (not included), in words, like "Rent is
 * due tomorrow, 650". While the privacy lock covers the budget each only says "A bill is due".
 * They are shaped like deadline reminders, so every phone and the computer show them the same way.
 */
suspend fun billReminders(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<DeadlineReminder> {
    val bills = remindingBills(dao)
    if (bills.isEmpty()) return emptyList()
    return Alerts.billReminders(bills, from, to, billTime(dao), hidden = dao.setting(LOCK_BUDGET) == "1") { moneyText(it / 100.0) }
}
