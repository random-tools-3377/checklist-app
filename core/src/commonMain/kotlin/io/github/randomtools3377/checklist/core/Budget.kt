package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/** How pay is counted: so much an hour, a day, a week, a month or a year. */
@Serializable
enum class PayUnit(val label: String, val each: String) {
    HOUR("Hourly", "an hour"),
    DAY("Daily", "a day"),
    WEEK("Weekly", "a week"),
    MONTH("Monthly", "a month"),
    YEAR("Yearly", "a year"),
}

private const val MINUTES_A_DAY = 24 * 60

/**
 * One day's work, from [start] to [stop] in minutes after midnight, with an unpaid break. A
 * shift that stops at or before its start ends the next day.
 */
@Serializable
data class WorkHours(val start: Int, val stop: Int, val breakMinutes: Int = 0) {
    /** From start to stop, in minutes. */
    val span: Int get() = if (stop > start) stop - start else stop + MINUTES_A_DAY - start

    /** The minutes paid for: the span less the break. */
    val paid: Int get() = (span - breakMinutes).coerceAtLeast(0)
}

/**
 * The user's pay: [amount] each [unit], of which [takeHome] percent is kept after tax, and the
 * usual week, Monday first, where a null day is a day off.
 */
@Serializable
data class PayPlan(
    val amount: Double,
    val unit: PayUnit,
    val week: List<WorkHours?> = List(7) { null },
    val takeHome: Int = 100,
) {
    /** The usual hours on [day], or null on a usual day off. */
    fun usual(day: LocalDate): WorkHours? = week.getOrNull(day.dayOfWeek.isoDayNumber - 1)

    /** Paid minutes in a usual week. */
    val weekMinutes: Int get() = week.sumOf { it?.paid ?: 0 }

    /** Whether there is enough to work pay out: an amount, and hours unless paid by the hour. */
    val complete: Boolean get() = amount > 0 && weekMinutes > 0

    companion object {
        /** The setting it is kept in, as JSON. */
        const val SETTING = "budget_pay"

        private val json = Json { ignoreUnknownKeys = true }

        fun encode(plan: PayPlan): String = json.encodeToString(serializer(), plan)

        /** The pay as it is now: the latest plan, whether the setting holds one plan or dated ones (see [PayPlans]). */
        fun decode(text: String?): PayPlan? = PayPlans.decode(text)?.latest

        /** One plan on its own, as the setting held it before pay could change from a date. */
        internal fun decodeOne(text: String): PayPlan? = try {
            json.decodeFromString(serializer(), text).takeIf { plan -> plan.week.size == 7 }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** A pay plan and the day it applies from; null for from the start. */
@Serializable
data class DatedPlan(val from: LocalDate? = null, val plan: PayPlan)

/**
 * The pay over time: each plan with the day it applies from, earliest first. A pay rise, a new tax
 * percentage or a new usual week is a new plan from a date, so the days before it keep the pay
 * they had. The earliest plan also covers every day before it.
 */
@Serializable
data class PayPlans(val plans: List<DatedPlan>) {
    /** The pay as it is now. */
    val latest: PayPlan get() = plans.last().plan

    /** The plan in force on [day]. */
    fun on(day: LocalDate): PayPlan = (plans.lastOrNull { it.from == null || it.from <= day } ?: plans.first()).plan

    /** Whether any plan has enough to work pay out. */
    val complete: Boolean get() = plans.any { it.plan.complete }

    /**
     * These plans with [plan] applying from [from] on, or from the start with null. Plans that
     * began on or after [from] give way to it; the days before keep the pay they had.
     */
    fun changed(plan: PayPlan, from: LocalDate?): PayPlans {
        if (from == null) return of(plan)
        val before = plans.filter { it.from == null || it.from < from }.ifEmpty { listOf(DatedPlan(null, plans.first().plan)) }
        return PayPlans(if (before.last().plan == plan) before else before + DatedPlan(from, plan))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** One plan for every day. */
        fun of(plan: PayPlan): PayPlans = PayPlans(listOf(DatedPlan(null, plan)))

        /** As the [PayPlan.SETTING] setting holds it: a single plan stays in the older, plain form. */
        fun encode(plans: PayPlans): String =
            if (plans.plans.size == 1) PayPlan.encode(plans.latest) else json.encodeToString(serializer(), plans)

        /** Reads the setting: dated plans, or the single plan older versions and backups hold. */
        fun decode(text: String?): PayPlans? {
            val stored = text?.takeIf { it.isNotBlank() } ?: return null
            PayPlan.decodeOne(stored)?.let { return of(it) }
            val read = try {
                json.decodeFromString(serializer(), stored)
            } catch (e: SerializationException) {
                return null
            } catch (e: IllegalArgumentException) {
                return null
            }
            val plans = read.plans.filter { it.plan.week.size == 7 }
            if (plans.isEmpty()) return null
            // Earliest first, the one from the start before any with a date.
            return PayPlans(plans.sortedBy { it.from?.toEpochDays() ?: Int.MIN_VALUE })
        }
    }
}

/**
 * A day that isn't the usual one: other [hours], or, without hours, a day off, [paid] or not.
 */
@Serializable
data class WorkDay(val day: LocalDate, val hours: WorkHours? = null, val paid: Boolean = false)

/** What a day pays ([total], after tax) and when it is earned: across [hours], or all at once on a paid day off. */
data class DayPay(val day: LocalDate, val hours: WorkHours?, val total: Double, val paidOff: Boolean, val changed: Boolean) {
    /** How much of the day's work is done at [now], from 0 to 1. A paid day off is earned once it starts. */
    fun done(now: LocalDateTime): Double {
        if (paidOff) return if (now.date >= day) 1.0 else 0.0
        val work = hours ?: return 0.0
        val minutes = (now.date.toEpochDays() - day.toEpochDays()) * MINUTES_A_DAY.toDouble() +
            now.hour * 60 + now.minute + now.second / 60.0 - work.start
        return (minutes / work.span).coerceIn(0.0, 1.0)
    }

    fun earned(now: LocalDateTime): Double = total * done(now)

    /** Minutes of work left at [now]: none before the day or after it. */
    fun minutesLeft(now: LocalDateTime): Int {
        val work = hours ?: return 0
        val done = done(now)
        return if (done <= 0.0 || done >= 1.0) 0 else kotlin.math.ceil((1 - done) * work.span - 1e-9).toInt()
    }
}

/** Pay worked out for a month: what is earned by now, of the month's whole pay. */
data class MonthPay(val earned: Double, val total: Double)

/** Working out pay from the [PayPlan]: by the day, by the month, and as an hourly rate. */
object Pay {
    /**
     * Pay for each paid minute of [hours] on [day], before tax. By the hour, the hourly rate; by
     * the day, the day's pay over the day's minutes; by the week, month or year, the pay spread
     * over the usual work minutes of the week or of [day]'s month (a year is 12 months).
     */
    private fun perMinute(plan: PayPlan, day: LocalDate, hours: WorkHours): Double = when (plan.unit) {
        PayUnit.HOUR -> plan.amount / 60
        PayUnit.DAY -> if (hours.paid > 0) plan.amount / hours.paid else 0.0
        PayUnit.WEEK -> if (plan.weekMinutes > 0) plan.amount / plan.weekMinutes else 0.0
        PayUnit.MONTH -> monthMinutes(plan, day).let { if (it > 0) plan.amount / it else 0.0 }
        PayUnit.YEAR -> monthMinutes(plan, day).let { if (it > 0) plan.amount / 12 / it else 0.0 }
    }

    /** Usual paid minutes in [day]'s month. */
    fun monthMinutes(plan: PayPlan, day: LocalDate): Int = monthDays(day).sumOf { plan.usual(it)?.paid ?: 0 }

    /** Every day of [day]'s month. */
    fun monthDays(day: LocalDate): List<LocalDate> {
        val first = LocalDate(day.year, day.month, 1)
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it.month == first.month }.toList()
    }

    /** What [day] pays, after tax, with its [change] if it has one. */
    fun day(plan: PayPlan, day: LocalDate, change: WorkDay? = null): DayPay {
        val share = plan.takeHome.coerceIn(0, 100) / 100.0
        val usual = plan.usual(day)
        val usualPay = usual?.let { perMinute(plan, day, it) * it.paid * share } ?: 0.0
        val hours = change?.hours
        return when {
            change == null -> DayPay(day, usual, usualPay, paidOff = false, changed = false)
            hours != null -> DayPay(day, hours, perMinute(plan, day, hours) * hours.paid * share, paidOff = false, changed = true)
            change.paid -> DayPay(day, null, usualPay, paidOff = usualPay > 0, changed = true)
            else -> DayPay(day, null, 0.0, paidOff = false, changed = true)
        }
    }

    /**
     * The work day going on at [now]: yesterday's while a night shift from yesterday is still
     * going, otherwise today's. [changes] holds the changed days.
     */
    fun current(plan: PayPlan, now: LocalDateTime, changes: Map<LocalDate, WorkDay>): DayPay {
        val yesterday = now.date.minusDays(1)
        val earlier = day(plan, yesterday, changes[yesterday])
        if (earlier.hours != null && earlier.done(now).let { it > 0.0 && it < 1.0 }) return earlier
        return day(plan, now.date, changes[now.date])
    }

    /** [day]'s month: what is earned by [now], of its whole pay. */
    fun month(plan: PayPlan, day: LocalDate, changes: Map<LocalDate, WorkDay>, now: LocalDateTime): MonthPay {
        val days = monthDays(day).map { day(plan, it, changes[it]) }
        return MonthPay(days.sumOf { it.earned(now) }, days.sumOf { it.total })
    }

    /** What [day] pays with the plan in force on it. */
    fun day(plans: PayPlans, day: LocalDate, change: WorkDay? = null): DayPay = day(plans.on(day), day, change)

    /** The work day going on at [now], each day with the plan in force on it. */
    fun current(plans: PayPlans, now: LocalDateTime, changes: Map<LocalDate, WorkDay>): DayPay {
        val yesterday = now.date.minusDays(1)
        val earlier = day(plans, yesterday, changes[yesterday])
        if (earlier.hours != null && earlier.done(now).let { it > 0.0 && it < 1.0 }) return earlier
        return day(plans, now.date, changes[now.date])
    }

    /** [day]'s month, each day with the plan in force on it, so a pay rise leaves the days before it as they were. */
    fun month(plans: PayPlans, day: LocalDate, changes: Map<LocalDate, WorkDay>, now: LocalDateTime): MonthPay {
        val days = monthDays(day).map { day(plans, it, changes[it]) }
        return MonthPay(days.sumOf { it.earned(now) }, days.sumOf { it.total })
    }

    /** Pay for an hour of work in a usual week, after tax; null without enough to go on. */
    fun hourly(plan: PayPlan): Double? {
        val hours = plan.weekMinutes / 60.0
        val workdays = plan.week.count { it != null && it.paid > 0 }
        val rate = when (plan.unit) {
            PayUnit.HOUR -> plan.amount
            PayUnit.DAY -> if (hours > 0) plan.amount * workdays / hours else return null
            PayUnit.WEEK -> if (hours > 0) plan.amount / hours else return null
            PayUnit.MONTH -> if (hours > 0) plan.amount * 12 / 52 / hours else return null
            PayUnit.YEAR -> if (hours > 0) plan.amount / 52 / hours else return null
        } * plan.takeHome.coerceIn(0, 100) / 100.0
        return rate.takeIf { it > 0 }
    }

    /** Minutes of work it takes to earn [cents] at [hourly], at least 1. */
    fun workMinutes(cents: Long, hourly: Double): Int = maxOf(1, (cents / 100.0 / hourly * 60).roundToLong().toInt())
}

/** Whether money went out, came in, or was put into or taken out of a saving goal. */
@Serializable
enum class MoneyKind { SPENT, RECEIVED, SAVED, TAKEN_OUT }

/** What a category is for, when the app files things in it itself. */
@Serializable
enum class CategoryRole { REWARDS }

/** A category of spending, with a monthly limit if the user set one. */
@Serializable
data class MoneyCategory(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val limitCents: Long? = null,
    val position: Int = 0,
    val archived: Boolean = false,
    val role: CategoryRole? = null,
)

/**
 * Money spent or received on [day], in cents. Spending has a category. A reward bought in the
 * shop with a money value is filed under Rewards with its [purchaseId]; a bill counted by itself
 * has its [billId]. Money put into or taken out of a saving goal has its [savingId].
 */
@Serializable
data class MoneyEntry(
    val id: Long = 0,
    val kind: MoneyKind,
    val cents: Long,
    val categoryId: Long? = null,
    val note: String = "",
    val day: LocalDate,
    val at: Long = 0,
    val purchaseId: Long? = null,
    val billId: Long? = null,
    val savingId: Long? = null,
    /**
     * Spending typed in by hand and paid from the saving goal [savingId]: it is kept as money
     * taken out of the goal, with its category for the record.
     */
    val spentFromSavings: Boolean = false,
) {
    /**
     * A reward, or spending typed in, paid for from a saving goal: the money leaves the goal, and
     * since it was set aside earlier it is no part of this month's spending, limits or budget game.
     */
    val paidFromSavings: Boolean get() = kind == MoneyKind.TAKEN_OUT && (purchaseId != null || spentFromSavings)
}

/** A category's spending in a month, against its limit. */
data class CategorySpend(val category: MoneyCategory, val cents: Long) {
    val over: Boolean get() = category.limitCents != null && cents > category.limitCents
}

/** A month of money: pay, other income, spending, and spending by category. */
data class MoneyMonth(
    val first: LocalDate,
    val pay: MonthPay?,
    val receivedCents: Long,
    val spentCents: Long,
    val categories: List<CategorySpend>,
    /** Put into saving goals, less what was taken out. */
    val savedCents: Long = 0,
) {
    /** What's left now: pay earned so far and other income, less spending and saving. */
    val leftNow: Double get() = (pay?.earned ?: 0.0) + (receivedCents - spentCents - savedCents) / 100.0

    /** What will be left at the end of the month if nothing else is spent. */
    val leftAtEnd: Double get() = (pay?.total ?: 0.0) + (receivedCents - spentCents - savedCents) / 100.0
}

object Budget {
    /** The categories a new budget starts with; Rewards is where bought rewards go. */
    val STARTER: List<MoneyCategory> = listOf(
        "Food" to "🍎", "Home" to "🏠", "Bills" to "🧾", "Transport" to "🚌", "Fun" to "🎉",
        "Health" to "💊", "Shopping" to "🛍️", "Rewards" to "🎁", "Other" to "📦",
    ).mapIndexed { i, (name, icon) ->
        MoneyCategory(name = name, icon = icon, position = i, role = if (name == "Rewards") CategoryRole.REWARDS else null)
    }

    /** The category a new bill goes under: Bills, or once it's renamed, the one with its 🧾 icon. */
    fun billsCategory(categories: List<MoneyCategory>): MoneyCategory? {
        val inUse = categories.filter { !it.archived }
        val bills = STARTER.first { it.name == "Bills" }
        return inUse.firstOrNull { it.name == bills.name } ?: inUse.firstOrNull { it.icon == bills.icon }
    }

    /** [amount] of money in cents, rounded to the nearest cent. */
    fun cents(amount: Double): Long = (amount * 100).roundToLong()

    /** The first day of [day]'s month. */
    fun monthOf(day: LocalDate): LocalDate = LocalDate(day.year, day.month, 1)

    /** The month with one [plan] for every day. */
    fun month(
        first: LocalDate,
        plan: PayPlan,
        changes: Map<LocalDate, WorkDay>,
        entries: List<MoneyEntry>,
        categories: List<MoneyCategory>,
        now: LocalDateTime,
    ): MoneyMonth = month(first, PayPlans.of(plan), changes, entries, categories, now)

    /** The month starting on [first]: its pay by [now] (each day with the plan in force on it) and its money, by category in the user's order. */
    fun month(
        first: LocalDate,
        plan: PayPlans?,
        changes: Map<LocalDate, WorkDay>,
        entries: List<MoneyEntry>,
        categories: List<MoneyCategory>,
        now: LocalDateTime,
    ): MoneyMonth {
        val inMonth = entries.filter { it.day.year == first.year && it.day.month == first.month }
        val spent = inMonth.filter { it.kind == MoneyKind.SPENT }
        val byCategory = spent.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.cents } }
        val shown = categories.filter { !it.archived || (byCategory[it.id] ?: 0) > 0 }.sortedBy { it.position }
        return MoneyMonth(
            first = first,
            pay = plan?.takeIf { it.complete }?.let { Pay.month(it, first, changes, now) },
            receivedCents = inMonth.filter { it.kind == MoneyKind.RECEIVED }.sumOf { it.cents },
            spentCents = spent.sumOf { it.cents },
            categories = shown.map { CategorySpend(it, byCategory[it.id] ?: 0) },
            // Money taken out to pay for a reward was spent, not put back in the pocket (see MoneyEntry.paidFromSavings).
            savedCents = inMonth.sumOf { if (it.kind == MoneyKind.SAVED) it.cents else if (it.kind == MoneyKind.TAKEN_OUT && !it.paidFromSavings) -it.cents else 0 },
        )
    }
}
