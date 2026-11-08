package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** Habits repeat. Projects are made of steps and are done once. */
@Serializable
enum class GoalKind { HABIT, PROJECT }

/** The stretch a habit's ticks count toward: one day, or a whole week, month or year. */
@Serializable
enum class Period { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A goal as it is now. Editing a goal only changes what happens from then on: every tick keeps
 * the points it paid at the time.
 */
@Serializable
data class Goal(
    val id: Long = 0,
    val kind: GoalKind,
    val name: String,
    /** An emoji. */
    val icon: String,
    /** Points paid for each tick of a habit. Projects pay per step instead. */
    val points: Int,
    /** When a habit is due. Null for a project. */
    val repeat: Repeat?,
    /** Ticks that finish a habit each time it is due (or per week, month or year for "times per"). */
    val target: Int = 1,
    /** Paid when a counted habit reaches its target, or when a project's last step is done. */
    val bonus: Int = 0,
    /** Projects only, optional. */
    val deadline: LocalDate? = null,
    /** How long one tick takes, in minutes, if the user set it. */
    val minutes: Int? = null,
    /** The clock time to do it at, if the user set one. */
    val at: LocalTime? = null,
    /** The time to finish it by, if the user set one. Otherwise the default from Settings. */
    val finishBy: LocalTime? = null,
    /**
     * For "times per week, month or year": the day to finish by, if the user set one. For a week,
     * an ISO weekday (Monday = 1); for a month, a date (0 for the last day); for a year,
     * month × 100 + day (1201 for 1 December).
     */
    val finishDay: Int? = null,
    val archived: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = 0,
) {
    /** The kind of stretch its ticks count toward. Null for a project. */
    val period: Period? get() = repeat?.windowPeriod
}

/** One step of a project. */
@Serializable
data class Step(
    val id: Long = 0,
    val goalId: Long,
    val name: String,
    val points: Int,
    val position: Int,
    val archived: Boolean = false,
)

/**
 * One tick. For a habit it counts toward the window that starts on [periodStart]. For a project
 * it marks the step [stepId] done.
 */
@Serializable
data class Tick(
    val id: Long = 0,
    val goalId: Long,
    val stepId: Long?,
    /** When it was ticked, in epoch milliseconds. */
    val at: Long,
    /** The user's day the tick counts for, after the day-end setting is applied. */
    val day: LocalDate,
    /** The kind of window it counted toward. Null for a project step. */
    val period: Period?,
    /** First day of that window. The same as [day] for a project step. */
    val periodStart: LocalDate,
)

/**
 * What a ledger entry is for. The balance is the sum of all entries.
 *
 * Later stages add their own kinds here (a penalty for going over the limit, a refund when a
 * miss turns out to be a forgotten tick) without changing how the balance works.
 */
@Serializable
enum class EntryKind { TICK, BONUS, PURCHASE, RETURN }

/** One line in the points ledger. Positive amounts are earned, negative ones are spent. */
@Serializable
data class Entry(
    val id: Long = 0,
    val at: Long,
    val day: LocalDate,
    val kind: EntryKind,
    val amount: Int,
    val goalId: Long? = null,
    val tickId: Long? = null,
    val rewardId: Long? = null,
    val purchaseId: Long? = null,
    /** What it was for, as it was named at the time. */
    val label: String,
)

/** A real-life reward in the shop, at the price the user set. */
@Serializable
data class Reward(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val price: Int,
    val archived: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = 0,
)

/** A reward that was bought. It waits in the inventory until it is used or returned. */
@Serializable
data class Purchase(
    val id: Long = 0,
    val rewardId: Long,
    val name: String,
    val icon: String,
    val price: Int,
    val at: Long,
    val usedAt: Long? = null,
    val returnedAt: Long? = null,
)
