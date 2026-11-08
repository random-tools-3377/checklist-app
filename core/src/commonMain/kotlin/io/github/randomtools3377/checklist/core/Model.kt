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
    /** How the phone reminds the user. Null means the defaults (see [Reminder]). */
    val reminder: Reminder? = null,
    /** Skips and misses allowed per [allowancePer] before they cost points. Null means the default. */
    val allowance: Int? = null,
    /** WEEKLY or MONTHLY. Null means the default. */
    val allowancePer: Period? = null,
    /** Kept behind the privacy lock, when the user turns that on. */
    val private: Boolean = false,
    /** Its own celebration GIF, if it has one (see [Celebration]). */
    val celebration: String? = null,
    val archived: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = 0,
) {
    /** The kind of stretch its ticks count toward. Null for a project. */
    val period: Period? get() = repeat?.windowPeriod

    /** Skips and misses allowed per [allowanceWindow] before they go over the limit. */
    val allowed: Int get() = allowance ?: DEFAULT_ALLOWANCE

    val allowanceWindow: Period get() = if (allowancePer == Period.MONTHLY) Period.MONTHLY else Period.WEEKLY

    companion object {
        /** One skip or miss a week is free until the user sets otherwise. */
        const val DEFAULT_ALLOWANCE = 1
    }
}

/**
 * Notifications for a habit, scheduled on the phone like a calendar's. A habit with a clock time
 * notifies at that time unless [onTime] is off, and can add an earlier alert [minutesBefore]. A
 * habit without a clock time can be reminded [at] a time of day.
 */
@Serializable
data class Reminder(
    val onTime: Boolean = true,
    val minutesBefore: Int? = null,
    val at: LocalTime? = null,
)

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
enum class EntryKind {
    TICK, BONUS, PURCHASE, RETURN,

    /** Half the goal's points, for a skip or miss over the limit. */
    PENALTY,

    /** A penalty given back, because the miss turned out to be a forgotten tick. */
    REFUND,
}

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
    /** The skip or miss a penalty or refund belongs to. */
    val lapseId: Long? = null,
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

/** A skip says in advance that a goal won't be done. A miss is found when its time ran out. */
@Serializable
enum class LapseKind { SKIP, MISS }

/** Why a goal was skipped or missed. */
@Serializable
enum class Reason(val label: String) {
    FORGOT("Forgot"),
    NO_TIME("No time"),
    ILL("Ill"),
    CHOSE_NOT("Chose not to"),
}

/**
 * A skip or a miss. [day] is the day it belongs to: the day it was due, or the last day of a
 * week, month or year window, or a project's deadline. Over the limit, it cost points and owes a
 * penalty task.
 */
@Serializable
data class Lapse(
    val id: Long = 0,
    val goalId: Long,
    val kind: LapseKind,
    val day: LocalDate,
    /** The window it belongs to, so a forgotten tick can be put back in it. Null for a project. */
    val period: Period?,
    val periodStart: LocalDate,
    /** When it was recorded, in epoch milliseconds. */
    val at: Long,
    val overLimit: Boolean,
    /** Points it cost, 0 within the allowance. */
    val cost: Int = 0,
    /** Null until the user gives one. Skips within the allowance need none. */
    val reason: Reason? = null,
    val note: String? = null,
    /** True when the user said they did it and forgot to tick: no longer a miss. */
    val forgotten: Boolean = false,
    /** The goal's name at the time. */
    val label: String,
    /** The gap it falls in, whose one reason covers it. */
    val gapId: Long? = null,
)

/**
 * A stretch when it looks like the user stopped for a while. One reason and one penalty task
 * cover the whole stretch; points still come off for every miss in it.
 */
@Serializable
data class Gap(
    val id: Long = 0,
    val from: LocalDate,
    val to: LocalDate,
    /** Why it was flagged. */
    val kind: GapKind,
    val createdAt: Long,
    val reason: Reason? = null,
    val note: String? = null,
)

@Serializable
enum class GapKind {
    /** Days in a row with nothing ticked. */
    NOTHING_TICKED,

    /** A big point loss within one week. */
    POINT_LOSS,
}

/** One of the user's own penalty tasks, to be drawn at random. */
@Serializable
data class PenaltyTask(val id: Long = 0, val name: String, val archived: Boolean = false)

/** How a penalty is paid off: by making up a goal, or by a penalty task. */
@Serializable
enum class PenaltyChoice { MAKE_UP, TASK }

/** A penalty task owed for going over the limit. It must be done before anything else. */
@Serializable
data class Penalty(
    val id: Long = 0,
    /** The skip or miss that caused it. Null for a gap check-in. */
    val lapseId: Long?,
    /** The gap it covers, for a gap check-in. */
    val gapId: Long? = null,
    val createdAt: Long,
    val choice: PenaltyChoice? = null,
    /** What it came to: the goal made up, or the penalty task drawn. */
    val goalId: Long? = null,
    val taskId: Long? = null,
    val label: String? = null,
    val doneAt: Long? = null,
    val cancelledAt: Long? = null,
)
